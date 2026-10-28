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

    // USB Audio Interface Detection & Routing
    var connectedUsbDeviceName: String? = null
        private set
    var onUsbDeviceStateChanged: ((deviceName: String?, isConnected: Boolean) -> Unit)? = null
    private var audioManager: AudioManager? = null
    private var deviceCallback: AudioDeviceCallback? = null

    override fun setRenderer(renderer: AudioRenderer) {
        this.renderer = renderer
    }

    /**
     * Attaches an Android Context to monitor USB audio interface connection in real-time.
     */
    fun attachContext(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        this.audioManager = am

        // Initial check for connected USB audio interfaces
        scanForUsbAudioInterface(am)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            deviceCallback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    scanForUsbAudioInterface(am)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    scanForUsbAudioInterface(am)
                }
            }
            am.registerAudioDeviceCallback(deviceCallback, null)
        }
    }

    private fun scanForUsbAudioInterface(am: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val usbDevice = devices.find {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }

            if (usbDevice != null) {
                val name = usbDevice.productName?.toString() ?: "Interface USB"
                connectedUsbDeviceName = name
                audioTrack?.setPreferredDevice(usbDevice)
                onUsbDeviceStateChanged?.invoke(name, true)
            } else {
                connectedUsbDeviceName = null
                audioTrack?.setPreferredDevice(null)
                onUsbDeviceStateChanged?.invoke(null, false)
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

        // Route to USB device if available
        audioManager?.let { scanForUsbAudioInterface(it) }

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
