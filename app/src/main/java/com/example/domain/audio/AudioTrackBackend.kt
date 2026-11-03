package com.example.domain.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build

class AudioTrackBackend : AudioBackend {
    private var audioTrack: AudioTrack? = null
    private var renderer: AudioRenderer? = null
    
    @Volatile
    private var isRunning = false
    private var renderThread: Thread? = null

    // Audio Output & USB Interface Detection
    var connectedUsbDeviceName: String? = null
        private set
    var activeAudioDeviceDescription: String = "Alto-falante do Celular"
        private set
    var isUsbInterfaceConnected: Boolean = false
        private set
    var isWiredHeadphonesConnected: Boolean = false
        private set
    var onUsbDeviceStateChanged: ((deviceName: String?, isConnected: Boolean) -> Unit)? = null
    private var audioManager: AudioManager? = null
    private var deviceCallback: AudioDeviceCallback? = null

    override fun setRenderer(renderer: AudioRenderer) {
        this.renderer = renderer
    }

    /**
     * Attaches an Android Context to monitor USB audio interface connection in real-time
     * and optimize system volume so Android does not attenuate the output signal.
     */
    fun attachContext(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        this.audioManager = am

        maximizeSystemVolume(am)

        // Initial check for connected USB audio interfaces and P2 line out
        scanForAudioDevices(am)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            deviceCallback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    scanForAudioDevices(am)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    scanForAudioDevices(am)
                }
            }
            am.registerAudioDeviceCallback(deviceCallback, null)
        }
    }

    fun maximizeSystemVolume(am: AudioManager) {
        try {
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scanForAudioDevices(am: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val usbDevice = devices.find {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE || 
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }
            val wiredDevice = devices.find {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
                it.type == AudioDeviceInfo.TYPE_AUX_LINE
            }

            when {
                usbDevice != null -> {
                    val name = usbDevice.productName?.toString()?.ifBlank { null } ?: "Interface USB"
                    connectedUsbDeviceName = name
                    isUsbInterfaceConnected = true
                    isWiredHeadphonesConnected = false
                    activeAudioDeviceDescription = "Interface USB: $name"
                    audioTrack?.setPreferredDevice(usbDevice)
                    onUsbDeviceStateChanged?.invoke(name, true)
                }
                wiredDevice != null -> {
                    connectedUsbDeviceName = null
                    isUsbInterfaceConnected = false
                    isWiredHeadphonesConnected = true
                    activeAudioDeviceDescription = "Cabo P2 / Saída Auxiliar (Teclado)"
                    audioTrack?.setPreferredDevice(wiredDevice)
                    onUsbDeviceStateChanged?.invoke(null, false)
                }
                else -> {
                    connectedUsbDeviceName = null
                    isUsbInterfaceConnected = false
                    isWiredHeadphonesConnected = false
                    activeAudioDeviceDescription = "Alto-falante do Celular"
                    audioTrack?.setPreferredDevice(null)
                    onUsbDeviceStateChanged?.invoke(null, false)
                }
            }
        }
    }

    override fun start(sampleRate: Int, framesPerBuffer: Int) {
        if (isRunning) return

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        // Buffer sized for low latency (double burst)
        val bufferSize = Math.max(minBufferSize, framesPerBuffer * 2 * 4 * 2)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()

        val trackBuilder = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }

        val track = trackBuilder.build()
        this.audioTrack = track
        track.setVolume(1.0f)

        // Route to USB device or P2 if available
        audioManager?.let { scanForAudioDevices(it) }

        track.play()
        isRunning = true

        renderThread = Thread {
            val floatBuffer = FloatArray(framesPerBuffer * 2)
            while (isRunning) {
                renderer?.render(floatBuffer, framesPerBuffer)
                if (isRunning) {
                    audioTrack?.write(floatBuffer, 0, floatBuffer.size, AudioTrack.WRITE_BLOCKING)
                }
            }
        }
        renderThread?.priority = Thread.MAX_PRIORITY
        renderThread?.start()
    }

    override fun stop() {
        isRunning = false
        renderThread?.join(1000)
        renderThread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioTrack = null
    }

    fun detach() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceCallback != null) {
            audioManager?.unregisterAudioDeviceCallback(deviceCallback)
            deviceCallback = null
        }
    }
}
