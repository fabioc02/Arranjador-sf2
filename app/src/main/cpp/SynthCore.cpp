#include "SynthCore.h"
#include <cstring>
#include <cmath>

namespace arranger {

static inline float midiNoteToFrequency(int note, float pitchBendSemitones) {
    float semitone = static_cast<float>(note) + pitchBendSemitones;
    return 440.0f * std::pow(2.0f, (semitone - 69.0f) / 12.0f);
}

SynthCore::SynthCore() {
    initialize(44100);
}

SynthCore::~SynthCore() {
    clearAllSamples();
}

void SynthCore::initialize(int sampleRate) {
    std::lock_guard<std::mutex> lock(stateMutex_);
    sampleRate_ = sampleRate > 0 ? sampleRate : 44100;

    for (int i = 0; i < NUM_CHANNELS; ++i) {
        channels_[i].volume = 0.8f;
        channels_[i].pan = 0.0f;
        channels_[i].expression = 1.0f;
        channels_[i].pitchBend = 0.0f;
        channels_[i].sustainPedal = false;
        channels_[i].program = 0;
        channels_[i].isDrumChannel = (i == 9 || i == 8); // MIDI channels 10 and 9
        std::memset(channels_[i].keySampleMap, 0, sizeof(channels_[i].keySampleMap));
    }

    for (int i = 0; i < ABSOLUTE_MAX_VOICES; ++i) {
        voices_[i].stop();
    }
}

void SynthCore::setMaxPolyphony(int voices) {
    std::lock_guard<std::mutex> lock(stateMutex_);
    maxActiveVoices_ = std::max(16, std::min(ABSOLUTE_MAX_VOICES, voices));
    // Stop any voice beyond the new polyphony limit
    for (int i = maxActiveVoices_; i < ABSOLUTE_MAX_VOICES; ++i) {
        voices_[i].stop();
    }
}

void SynthCore::reset() {
    std::lock_guard<std::mutex> lock(stateMutex_);
    for (int i = 0; i < ABSOLUTE_MAX_VOICES; ++i) {
        voices_[i].stop();
    }
}

void SynthCore::registerSample(int id, const float* pcm, int length, bool isStereo,
                               int sampleRate, int rootKey, int loopStart, int loopEnd, bool isLooped) {
    if (pcm == nullptr || length <= 0) return;

    std::lock_guard<std::mutex> lock(stateMutex_);
    SampleBuffer buf;
    buf.id = id;
    buf.pcm.assign(pcm, pcm + length);
    buf.isStereo = isStereo;
    buf.sampleRate = sampleRate > 0 ? sampleRate : 44100;
    buf.rootKey = rootKey;
    buf.loopStart = loopStart;
    buf.loopEnd = loopEnd > loopStart ? loopEnd : length;
    buf.isLooped = isLooped;

    sampleLibrary_[id] = std::move(buf);
}

void SynthCore::mapKeyToSample(int channel, int lowKey, int highKey, int sampleId) {
    if (channel < 0 || channel >= NUM_CHANNELS) return;
    std::lock_guard<std::mutex> lock(stateMutex_);

    int start = std::max(0, std::min(127, lowKey));
    int end = std::max(0, std::min(127, highKey));
    for (int k = start; k <= end; ++k) {
        channels_[channel].keySampleMap[k] = sampleId;
    }
}

void SynthCore::clearAllSamples() {
    std::lock_guard<std::mutex> lock(stateMutex_);
    for (int i = 0; i < ABSOLUTE_MAX_VOICES; ++i) {
        voices_[i].stop();
    }
    sampleLibrary_.clear();
    for (int i = 0; i < NUM_CHANNELS; ++i) {
        std::memset(channels_[i].keySampleMap, 0, sizeof(channels_[i].keySampleMap));
    }
}

Voice* SynthCore::findFreeVoice() {
    // 1. Look for idle voice up to maxActiveVoices_
    for (int i = 0; i < maxActiveVoices_; ++i) {
        if (!voices_[i].active) {
            return &voices_[i];
        }
    }
    // 2. Look for voice in release stage
    for (int i = 0; i < maxActiveVoices_; ++i) {
        if (voices_[i].envStage == EnvelopeStage::RELEASE) {
            return &voices_[i];
        }
    }
    // 3. Fallback: steal voice with lowest envelope level
    float minLevel = 1.0f;
    int stealIdx = 0;
    for (int i = 0; i < maxActiveVoices_; ++i) {
        if (voices_[i].envLevel < minLevel) {
            minLevel = voices_[i].envLevel;
            stealIdx = i;
        }
    }
    return &voices_[stealIdx];
}

void SynthCore::noteOn(int channel, int note, int velocity) {
    if (channel < 0 || channel >= NUM_CHANNELS || note < 0 || note > 127) return;

    if (velocity <= 0) {
        noteOff(channel, note);
        return;
    }

    std::lock_guard<std::mutex> lock(stateMutex_);
    Voice* v = findFreeVoice();
    if (!v) return;

    const ChannelState& ch = channels_[channel];
    v->active = true;
    v->channel = channel;
    v->note = note;
    v->velocity = static_cast<float>(velocity) / 127.0f;
    v->samplePos = 0.0;
    v->envStage = EnvelopeStage::ATTACK;
    v->envLevel = 0.0f;

    // Check if a sample buffer is registered for this key
    int sampleId = ch.keySampleMap[note];
    auto it = sampleLibrary_.find(sampleId);
    if (it != sampleLibrary_.end() && !it->second.pcm.empty()) {
        v->sample = &it->second;
        // Calculate step rate based on frequency ratio
        float rootFreq = 440.0f * std::pow(2.0f, (it->second.rootKey - 69.0f) / 12.0f);
        float noteFreq = midiNoteToFrequency(note, ch.pitchBend);
        double pitchRatio = static_cast<double>(noteFreq) / static_cast<double>(rootFreq);
        double sampleRateRatio = static_cast<double>(it->second.sampleRate) / static_cast<double>(sampleRate_);
        v->stepRate = pitchRatio * sampleRateRatio;

        if (ch.isDrumChannel) {
            // One-shot drum envelope
            v->attackRate = 0.05f;
            v->decayRate = 0.0003f;
            v->sustainLevel = 0.8f;
            v->releaseRate = 0.0005f;
        } else {
            // Melodic instrument envelope
            v->attackRate = 0.008f;
            v->decayRate = 0.0001f;
            v->sustainLevel = 0.75f;
            v->releaseRate = 0.0004f;
        }
    } else {
        // No sample registered for this key in native library; stop voice
        v->stop();
        return;
    }

    // Panning (Channel Pan: -1.0 to 1.0)
    float panNorm = (ch.pan + 1.0f) * 0.5f; // 0.0 (left) to 1.0 (right)
    v->gainL = std::cos(panNorm * M_PI_2) * ch.volume * ch.expression;
    v->gainR = std::sin(panNorm * M_PI_2) * ch.volume * ch.expression;
}

void SynthCore::noteOff(int channel, int note) {
    if (channel < 0 || channel >= NUM_CHANNELS) return;
    std::lock_guard<std::mutex> lock(stateMutex_);

    const ChannelState& ch = channels_[channel];
    for (int i = 0; i < maxActiveVoices_; ++i) {
        if (voices_[i].active && voices_[i].channel == channel && voices_[i].note == note) {
            if (ch.sustainPedal) {
                // Sustain pedal is down: keep voice alive until pedal released
                continue;
            }
            voices_[i].release();
        }
    }
}

void SynthCore::controlChange(int channel, int controller, int value) {
    if (channel < 0 || channel >= NUM_CHANNELS) return;
    std::lock_guard<std::mutex> lock(stateMutex_);

    ChannelState& ch = channels_[channel];
    float normVal = static_cast<float>(value) / 127.0f;

    switch (controller) {
        case 7: // Channel Volume
            ch.volume = normVal;
            break;
        case 10: // Pan (-1.0 to 1.0)
            ch.pan = (normVal * 2.0f) - 1.0f;
            break;
        case 11: // Expression
            ch.expression = normVal;
            break;
        case 64: // Sustain Pedal
            ch.sustainPedal = (value >= 64);
            if (!ch.sustainPedal) {
                // Release all sustained voices for this channel
                for (int i = 0; i < maxActiveVoices_; ++i) {
                    if (voices_[i].active && voices_[i].channel == channel) {
                        voices_[i].release();
                    }
                }
            }
            break;
        case 120: // All Sound Off
        case 123: // All Notes Off
            allNotesOff(channel);
            break;
        default:
            break;
    }
}

void SynthCore::pitchBend(int channel, int value) {
    if (channel < 0 || channel >= NUM_CHANNELS) return;
    std::lock_guard<std::mutex> lock(stateMutex_);

    // Value is 0 to 16383, 8192 is center
    float semitones = (static_cast<float>(value - 8192) / 8192.0f) * 2.0f; // +/- 2 semitones
    channels_[channel].pitchBend = semitones;
}

void SynthCore::programChange(int channel, int program) {
    if (channel < 0 || channel >= NUM_CHANNELS) return;
    std::lock_guard<std::mutex> lock(stateMutex_);
    channels_[channel].program = program;
}

void SynthCore::allNotesOff(int channel) {
    if (channel >= 0 && channel < NUM_CHANNELS) {
        for (int i = 0; i < maxActiveVoices_; ++i) {
            if (voices_[i].channel == channel) {
                voices_[i].stop();
            }
        }
    } else {
        for (int i = 0; i < maxActiveVoices_; ++i) {
            voices_[i].stop();
        }
    }
}

int SynthCore::getActiveVoiceCount() const {
    int count = 0;
    for (int i = 0; i < maxActiveVoices_; ++i) {
        if (voices_[i].active) {
            count++;
        }
    }
    return count;
}

void SynthCore::render(float* outputInterleavedStereo, int32_t numFrames) {
    if (outputInterleavedStereo == nullptr || numFrames <= 0) return;

    // Clear buffer first
    std::memset(outputInterleavedStereo, 0, sizeof(float) * numFrames * 2);

    std::lock_guard<std::mutex> lock(stateMutex_);

    for (int f = 0; f < numFrames; ++f) {
        float mixL = 0.0f;
        float mixR = 0.0f;

        for (int v = 0; v < maxActiveVoices_; ++v) {
            Voice& voice = voices_[v];
            if (!voice.active) continue;

            // ADSR Envelope step
            switch (voice.envStage) {
                case EnvelopeStage::ATTACK:
                    voice.envLevel += voice.attackRate;
                    if (voice.envLevel >= 1.0f) {
                        voice.envLevel = 1.0f;
                        voice.envStage = EnvelopeStage::DECAY;
                    }
                    break;
                case EnvelopeStage::DECAY:
                    voice.envLevel -= voice.decayRate;
                    if (voice.envLevel <= voice.sustainLevel) {
                        voice.envLevel = voice.sustainLevel;
                        voice.envStage = EnvelopeStage::SUSTAIN;
                    }
                    break;
                case EnvelopeStage::SUSTAIN:
                    // Stays at sustain level
                    break;
                case EnvelopeStage::RELEASE:
                    voice.envLevel -= voice.releaseRate;
                    if (voice.envLevel <= 0.0001f) {
                        voice.stop();
                        continue;
                    }
                    break;
                case EnvelopeStage::IDLE:
                    voice.stop();
                    continue;
            }

            float sampleL = 0.0f;
            float sampleR = 0.0f;

            if (voice.sample != nullptr) {
                renderSampleVoice(voice, sampleL, sampleR);
            } else {
                renderFallbackVoice(voice, sampleL, sampleR);
            }

            float gain = voice.velocity * voice.envLevel;
            mixL += sampleL * gain * voice.gainL;
            mixR += sampleR * gain * voice.gainR;
        }

        // Soft clipper (tanh-like limiter curve)
        outputInterleavedStereo[f * 2] = std::tanh(mixL * 0.7f);
        outputInterleavedStereo[f * 2 + 1] = std::tanh(mixR * 0.7f);
    }
}

void SynthCore::renderSampleVoice(Voice& voice, float& outL, float& outR) {
    const SampleBuffer& buf = *voice.sample;
    int len = static_cast<int>(buf.pcm.size());

    if (buf.isStereo) {
        int frameCount = len / 2;
        int idx = static_cast<int>(voice.samplePos);
        if (idx >= frameCount - 1) {
            if (buf.isLooped && buf.loopEnd > buf.loopStart && buf.loopEnd <= frameCount) {
                voice.samplePos = buf.loopStart;
                idx = buf.loopStart;
            } else {
                voice.stop();
                outL = 0.0f;
                outR = 0.0f;
                return;
            }
        }

        float frac = static_cast<float>(voice.samplePos - idx);
        int i0 = idx * 2;
        int i1 = (idx + 1) * 2;

        outL = buf.pcm[i0] + frac * (buf.pcm[i1] - buf.pcm[i0]);
        outR = buf.pcm[i0 + 1] + frac * (buf.pcm[i1 + 1] - buf.pcm[i0 + 1]);
    } else {
        // Mono sample
        int idx = static_cast<int>(voice.samplePos);
        if (idx >= len - 1) {
            if (buf.isLooped && buf.loopEnd > buf.loopStart && buf.loopEnd <= len) {
                voice.samplePos = buf.loopStart;
                idx = buf.loopStart;
            } else {
                voice.stop();
                outL = 0.0f;
                outR = 0.0f;
                return;
            }
        }

        float frac = static_cast<float>(voice.samplePos - idx);
        float s = buf.pcm[idx] + frac * (buf.pcm[idx + 1] - buf.pcm[idx]);
        outL = s;
        outR = s;
    }

    voice.samplePos += voice.stepRate;
}

void SynthCore::renderFallbackVoice(Voice& voice, float& outL, float& outR) {
    float s = 0.0f;

    if (voice.synthType == 2) {
        // Drum synthesis: noise burst + low sine punch
        float noise = ((static_cast<float>(rand()) / static_cast<float>(RAND_MAX)) * 2.0f) - 1.0f;
        float punch = std::sin(voice.phase);
        s = (punch * 0.7f) + (noise * 0.3f);
        voice.phase += voice.phaseStep * 0.5f; // pitch drop
    } else if (voice.synthType == 0) {
        // Piano/Tines: fundamental sine + 2nd harmonic
        s = (std::sin(voice.phase) * 0.75f) + (std::sin(voice.phase * 2.0f) * 0.25f);
        voice.phase += voice.phaseStep;
    } else {
        // Organ/Lead: soft saw/triangle
        float saw = (voice.phase / M_PI) - 1.0f;
        s = saw * 0.5f;
        voice.phase += voice.phaseStep;
    }

    if (voice.phase > 2.0f * M_PI) {
        voice.phase -= 2.0f * M_PI;
    }

    outL = s;
    outR = s;
}

} // namespace arranger
