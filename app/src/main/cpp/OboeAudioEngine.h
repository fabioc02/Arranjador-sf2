#ifndef OBOE_AUDIO_ENGINE_H
#define OBOE_AUDIO_ENGINE_H

#include <oboe/Oboe.h>
#include "SynthCore.h"
#include <memory>
#include <atomic>

namespace arranger {

class OboeAudioEngine : public oboe::AudioStreamCallback {
public:
    OboeAudioEngine();
    virtual ~OboeAudioEngine();

    bool start(int sampleRate = 44100, int framesPerBurst = 192);
    void stop();
    bool isRunning() const { return isRunning_.load(); }

    SynthCore& getSynthCore() { return synthCore_; }

    // AudioStreamDataCallback interface
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) override;

    void onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> stream_;
    SynthCore synthCore_;
    std::atomic<bool> isRunning_{false};
    int sampleRate_ = 44100;
};

} // namespace arranger

#endif // OBOE_AUDIO_ENGINE_H
