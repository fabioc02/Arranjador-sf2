#ifndef SYNTH_CORE_H
#define SYNTH_CORE_H

#include <vector>
#include <unordered_map>
#include <mutex>
#include <cmath>
#include <cstdint>
#include <algorithm>

namespace arranger {

struct SampleBuffer {
    int id;
    std::vector<float> pcm; // Stereo or Mono float PCM
    bool isStereo;
    int sampleRate;
    int rootKey;
    int loopStart;
    int loopEnd;
    bool isLooped;
};

enum class EnvelopeStage {
    IDLE,
    ATTACK,
    DECAY,
    SUSTAIN,
    RELEASE
};

struct Voice {
    bool active = false;
    int channel = 0;
    int note = 60;
    float velocity = 0.8f;
    const SampleBuffer* sample = nullptr;

    double samplePos = 0.0;
    double stepRate = 1.0;

    // Fallback synth state
    float phase = 0.0f;
    float phaseStep = 0.0f;
    int synthType = 0; // 0 = piano/tines, 1 = organ/synth, 2 = drum click/noise

    EnvelopeStage envStage = EnvelopeStage::IDLE;
    float envLevel = 0.0f;
    float attackRate = 0.01f;
    float decayRate = 0.002f;
    float sustainLevel = 0.7f;
    float releaseRate = 0.005f;

    float gainL = 0.7f;
    float gainR = 0.7f;

    void release() {
        if (active && envStage != EnvelopeStage::RELEASE && envStage != EnvelopeStage::IDLE) {
            envStage = EnvelopeStage::RELEASE;
        }
    }

    void stop() {
        active = false;
        envStage = EnvelopeStage::IDLE;
        sample = nullptr;
    }
};

struct ChannelState {
    float volume = 0.8f;        // CC 7 (0.0 to 1.0)
    float pan = 0.0f;           // CC 10 (-1.0 to 1.0)
    float expression = 1.0f;    // CC 11 (0.0 to 1.0)
    float pitchBend = 0.0f;     // -2.0 to +2.0 semitones
    bool sustainPedal = false;  // CC 64
    int program = 0;            // Program Change 0..127
    bool isDrumChannel = false; // Ch 10 (idx 9)

    // Key to Sample ID mapping (128 keys)
    int keySampleMap[128] = { 0 };
};

class SynthCore {
public:
    SynthCore();
    ~SynthCore();

    void initialize(int sampleRate);
    void reset();

    // Sample Management
    void registerSample(int id, const float* pcm, int length, bool isStereo,
                        int sampleRate, int rootKey, int loopStart, int loopEnd, bool isLooped);
    void mapKeyToSample(int channel, int lowKey, int highKey, int sampleId);
    void clearAllSamples();

    // MIDI Interface
    void noteOn(int channel, int note, int velocity);
    void noteOff(int channel, int note);
    void controlChange(int channel, int controller, int value);
    void pitchBend(int channel, int value);
    void programChange(int channel, int program);
    void allNotesOff(int channel = -1);

    // Audio Rendering Callback (called on high-priority audio thread)
    void render(float* outputInterleavedStereo, int32_t numFrames);

    void setMaxPolyphony(int voices);
    int getMaxPolyphony() const { return maxActiveVoices_; }
    int getActiveVoiceCount() const;

private:
    static constexpr int ABSOLUTE_MAX_VOICES = 128;
    static constexpr int NUM_CHANNELS = 16;

    int sampleRate_ = 44100;
    int maxActiveVoices_ = 128;
    std::mutex stateMutex_;

    Voice voices_[ABSOLUTE_MAX_VOICES];
    ChannelState channels_[NUM_CHANNELS];

    std::unordered_map<int, SampleBuffer> sampleLibrary_;

    Voice* findFreeVoice();
    void renderFallbackVoice(Voice& voice, float& outL, float& outR);
    void renderSampleVoice(Voice& voice, float& outL, float& outR);
};

} // namespace arranger

#endif // SYNTH_CORE_H
