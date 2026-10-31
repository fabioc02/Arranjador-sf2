#include "OboeAudioEngine.h"
#include <android/log.h>

#define TAG "ArrangerOboeEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace arranger {

OboeAudioEngine::OboeAudioEngine() {
}

OboeAudioEngine::~OboeAudioEngine() {
    stop();
}

bool OboeAudioEngine::start(int sampleRate, int framesPerBurst) {
    if (isRunning_.load()) {
        return true;
    }

    sampleRate_ = sampleRate > 0 ? sampleRate : 44100;
    synthCore_.initialize(sampleRate_);

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setFormat(oboe::AudioFormat::Float)
           ->setChannelCount(oboe::ChannelCount::Stereo)
           ->setSampleRate(sampleRate_)
           ->setCallback(this);

    oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open Oboe stream (exclusive): %s. Retrying with Shared mode...", oboe::convertToText(result));
        builder.setSharingMode(oboe::SharingMode::Shared);
        result = builder.openStream(stream_);
        if (result != oboe::Result::OK) {
            LOGE("Failed to open Oboe stream (shared): %s", oboe::convertToText(result));
            return false;
        }
    }

    // Set buffer size to burst size * 2 for low latency without under-runs
    int32_t burst = stream_->getFramesPerBurst();
    stream_->setBufferSizeInFrames(burst * 2);

    result = stream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start Oboe stream: %s", oboe::convertToText(result));
        stream_->close();
        stream_.reset();
        return false;
    }

    isRunning_.store(true);
    LOGI("Oboe Audio Stream started: sampleRate=%d, framesPerBurst=%d, bufferSize=%d, API=%s",
         stream_->getSampleRate(), burst, stream_->getBufferSizeInFrames(),
         (stream_->getAudioApi() == oboe::AudioApi::AAudio ? "AAudio" : "OpenSLES"));
    return true;
}

void OboeAudioEngine::stop() {
    if (!isRunning_.exchange(false)) {
        return;
    }

    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
    LOGI("Oboe Audio Stream stopped");
}

oboe::DataCallbackResult OboeAudioEngine::onAudioReady(
    oboe::AudioStream *audioStream,
    void *audioData,
    int32_t numFrames) {

    float *floatData = static_cast<float*>(audioData);
    synthCore_.render(floatData, numFrames);
    return oboe::DataCallbackResult::Continue;
}

void OboeAudioEngine::onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) {
    LOGE("Oboe stream closed due to error: %s", oboe::convertToText(error));
    isRunning_.store(false);
}

} // namespace arranger
