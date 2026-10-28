package com.example.domain.midi

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

interface IMidiScheduler {
    val ppq: Int
    val mode: MidiSchedulerMode
    val isRunning: Boolean
    fun start()
    fun stop()
}

class J5CompatibilityScheduler(
    override val ppq: Int = 1920,
    private val getBpm: () -> Float,
    private val getNextEventTick: (() -> Long?)? = null,
    private val onTick: (Long, Long) -> Unit
) : IMidiScheduler {
    override val mode: MidiSchedulerMode = MidiSchedulerMode.J5_COMPATIBILITY
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)

    override val isRunning: Boolean
        get() = running.get()

    override fun start() {
        if (running.getAndSet(true)) return

        thread = Thread {
            var currentTick = 0L
            val startTimeNs = System.nanoTime()
            val maxBatchTicks = ppq.toLong() // 1 beat max wait (e.g. 480 or 1920)

            // Emit initial tick 0
            onTick(0L, startTimeNs)

            while (running.get()) {
                val currentBpm = getBpm().coerceAtLeast(20f)
                val nanosPerTick = 60_000_000_000.0 / (currentBpm * ppq)

                // Event-driven next-event scheduling in 1920 PPQ:
                val nextEventTick = getNextEventTick?.invoke()
                val targetTick = if (nextEventTick != null && nextEventTick > currentTick) {
                    minOf(nextEventTick, currentTick + maxBatchTicks)
                } else if (getNextEventTick != null) {
                    currentTick + (ppq / 4).coerceAtLeast(1)
                } else {
                    currentTick + 1
                }

                val targetTimeNs = (startTimeNs + (targetTick * nanosPerTick)).toLong()
                var now = System.nanoTime()

                // Sleep directly until target time without busy-spin (J5 economic mode)
                if (targetTimeNs > now) {
                    val waitNs = targetTimeNs - now
                    if (waitNs > 500_000L) { // > 0.5ms
                        LockSupport.parkNanos(waitNs - 200_000L)
                    }
                    while (System.nanoTime() < targetTimeNs && running.get()) {
                        Thread.yield()
                    }
                } else if (now > targetTimeNs + (nanosPerTick * ppq * 2)) {
                    // Anti-spiral of death (e.g. device sleep)
                    now = System.nanoTime()
                }

                if (!running.get()) break

                onTick(targetTick, targetTimeNs)
                currentTick = targetTick
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            name = "J5MidiScheduler"
            start()
        }
    }

    override fun stop() {
        running.set(false)
        thread?.interrupt()
        thread = null
    }
}

class HighPrecisionScheduler(
    override val ppq: Int = 1920,
    private val getBpm: () -> Float,
    private val getNextEventTick: (() -> Long?)? = null,
    private val onTick: (Long, Long) -> Unit
) : IMidiScheduler {
    override val mode: MidiSchedulerMode = MidiSchedulerMode.HIGH_PRECISION
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)

    override val isRunning: Boolean
        get() = running.get()

    override fun start() {
        if (running.getAndSet(true)) return

        thread = Thread {
            var currentTick = 0L
            val startTimeNs = System.nanoTime()
            val maxBatchTicks = (ppq / 4).toLong().coerceAtLeast(1L) // Finer anticipation window

            // Emit initial tick 0
            onTick(0L, startTimeNs)

            while (running.get()) {
                val currentBpm = getBpm().coerceAtLeast(20f)
                val nanosPerTick = 60_000_000_000.0 / (currentBpm * ppq)

                val nextEventTick = getNextEventTick?.invoke()
                val targetTick = if (nextEventTick != null && nextEventTick > currentTick) {
                    minOf(nextEventTick, currentTick + maxBatchTicks)
                } else if (getNextEventTick != null) {
                    currentTick + (ppq / 16).coerceAtLeast(1)
                } else {
                    currentTick + 1
                }

                val targetTimeNs = (startTimeNs + (targetTick * nanosPerTick)).toLong()
                var now = System.nanoTime()

                if (targetTimeNs > now) {
                    while (now < targetTimeNs && running.get()) {
                        val diffNs = targetTimeNs - now
                        if (diffNs > 1_500_000L) {
                            LockSupport.parkNanos(diffNs - 800_000L)
                        } else {
                            // Sub-millisecond precision
                            Thread.yield()
                        }
                        now = System.nanoTime()
                    }
                } else if (now > targetTimeNs + (nanosPerTick * ppq * 2)) {
                    now = System.nanoTime()
                }

                if (!running.get()) break

                onTick(targetTick, targetTimeNs)
                currentTick = targetTick
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            name = "HighPrecisionMidiScheduler"
            start()
        }
    }

    override fun stop() {
        running.set(false)
        thread?.interrupt()
        thread = null
    }
}

class RealTimeMidiScheduler(
    override val ppq: Int = 1920,
    private val getBpm: () -> Float,
    override val mode: MidiSchedulerMode = MidiSchedulerMode.AUTO,
    private val getNextEventTick: (() -> Long?)? = null,
    private val onTick: (Long, Long) -> Unit
) : IMidiScheduler {

    val effectiveMode: MidiSchedulerMode = mode.resolveEffectiveMode()

    private val delegate: IMidiScheduler = when (effectiveMode) {
        MidiSchedulerMode.J5_COMPATIBILITY -> J5CompatibilityScheduler(
            ppq = ppq,
            getBpm = getBpm,
            getNextEventTick = getNextEventTick,
            onTick = onTick
        )
        MidiSchedulerMode.HIGH_PRECISION, MidiSchedulerMode.AUTO -> HighPrecisionScheduler(
            ppq = ppq,
            getBpm = getBpm,
            getNextEventTick = getNextEventTick,
            onTick = onTick
        )
    }

    // Secondary constructor for 100% backward compatibility
    constructor(
        ppq: Int,
        getBpm: () -> Float,
        onTick: (Long, Long) -> Unit
    ) : this(ppq, getBpm, MidiSchedulerMode.AUTO, null, onTick)

    override val isRunning: Boolean
        get() = delegate.isRunning

    override fun start() {
        delegate.start()
    }

    override fun stop() {
        delegate.stop()
    }
}
