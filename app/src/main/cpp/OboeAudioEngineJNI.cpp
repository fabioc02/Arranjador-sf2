#include <jni.h>
#include "OboeAudioEngine.h"
#include <memory>

static std::unique_ptr<arranger::OboeAudioEngine> gEngine;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_domain_audio_NativeAudioBridge_nativeInit(
    JNIEnv *env,
    jobject /* this */,
    jint sampleRate,
    jint framesPerBurst) {
    if (!gEngine) {
        gEngine = std::make_unique<arranger::OboeAudioEngine>();
    }
    return gEngine->start(sampleRate, framesPerBurst) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_domain_audio_NativeAudioBridge_nativeStart(
    JNIEnv *env,
    jobject /* this */) {
    if (gEngine) {
        return gEngine->start() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_domain_audio_NativeAudioBridge_nativeStop(
    JNIEnv *env,
    jobject /* this */) {
    if (gEngine) {
        gEngine->stop();
    }
}

JNIEXPORT void JNICALL
Java_com_example_domain_audio_NativeAudioBridge_nativeSetMaxPolyphony(
    JNIEnv *env,
    jobject /* this */,
    jint polyphony) {
    if (gEngine) {
        gEngine->getSynthCore().setMaxPolyphony(polyphony);
    }
}

JNIEXPORT void JNICALL
Java_com_example_domain_audio_NativeAudioBridge_nativeNoteOn(
    JNIEnv *env,
    jobject /* this */,
    jint channel,
    jint note,
    jint velocity) {
    if (gEngine) {
        gEngine->getSynthCore().noteOn(channel, note, velocity);
    }
}

JNIEXPORT void JNICALL
Java_com_example_domain_audio_NativeAudioBridge_nativeNoteOff(
    JNIEnv *env,
    jobject /* this */,
    jint channel,
    jint note) {
    if (gEngine) {
        gEngine->getSynthCore().noteOff(channel, note);
    }
}

JNIEXPORT void JNICALL
Java_com_example_domain_audio_NativeAudioBridge_nativeControlChange(
    JNIEnv *env,
    jobject /* this */,
    jint channel,
    jint controller,
    jint value) {
    if (gEngine) {
        gEngine->getSynthCore().controlChange(channel, controller, value);
    }
}

JNIEXPORT void JNICALL
Java_com_example_domain_audio_NativeAudioBridge_nativePitchBend(
    JNIEnv *env,
    jobject /* this */,
    jint channel,
    jint value) {
    if (gEngine) {
        gEngine->getSynthCore().pitchBend(channel, value);
    }
}

JNIEXPORT void JNICALL
Java_com_example_domain_audio_NativeAudioBridge_nativeProgramChange(
    JNIEnv *env,
    jobject /* this */,
    jint channel,
    jint program) {
    if (gEngine) {
        gEngine->getSynthCore().programChange(channel, program);
    }
}

JNIEXPORT void JNICALL
Java_com_example_domain_audio_NativeAudioBridge_nativeAllNotesOff(
    JNIEnv *env,
    jobject /* this */,
    jint channel) {
    if (gEngine) {
        gEngine->getSynthCore().allNotesOff(channel);
    }
}

JNIEXPORT void JNICALL
Java_com_example_domain_audio_NativeAudioBridge_nativeRegisterSample(
    JNIEnv *env,
    jobject /* this */,
    jint sampleId,
    jfloatArray pcmData,
    jboolean isStereo,
    jint sampleRate,
    jint rootKey,
    jint loopStart,
    jint loopEnd,
    jboolean isLooped) {
    if (!gEngine || pcmData == nullptr) return;

    jsize len = env->GetArrayLength(pcmData);
    if (len <= 0) return;

    jfloat *elements = env->GetFloatArrayElements(pcmData, nullptr);
    if (elements != nullptr) {
        gEngine->getSynthCore().registerSample(
            sampleId, elements, len, isStereo == JNI_TRUE,
            sampleRate, rootKey, loopStart, loopEnd, isLooped == JNI_TRUE);
        env->ReleaseFloatArrayElements(pcmData, elements, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL
Java_com_example_domain_audio_NativeAudioBridge_nativeMapKeyToSample(
    JNIEnv *env,
    jobject /* this */,
    jint channel,
    jint lowKey,
    jint highKey,
    jint sampleId) {
    if (gEngine) {
        gEngine->getSynthCore().mapKeyToSample(channel, lowKey, highKey, sampleId);
    }
}

JNIEXPORT void JNICALL
Java_com_example_domain_audio_NativeAudioBridge_nativeClearAllSamples(
    JNIEnv *env,
    jobject /* this */) {
    if (gEngine) {
        gEngine->getSynthCore().clearAllSamples();
    }
}

JNIEXPORT jint JNICALL
Java_com_example_domain_audio_NativeAudioBridge_nativeGetActiveVoices(
    JNIEnv *env,
    jobject /* this */) {
    if (gEngine) {
        return gEngine->getSynthCore().getActiveVoiceCount();
    }
    return 0;
}

JNIEXPORT jboolean JNICALL
Java_com_example_domain_audio_NativeAudioBridge_nativeIsRunning(
    JNIEnv *env,
    jobject /* this */) {
    if (gEngine) {
        return gEngine->isRunning() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

} // extern "C"
