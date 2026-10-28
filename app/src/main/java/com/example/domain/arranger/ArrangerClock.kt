package com.example.domain.arranger

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

/**
 * ARRANGER CLOCK
 * Temporizador musical de alta precisão (NanoTime).
 * Não utiliza LaunchedEffect ou recomposição do Compose.
 */
class ArrangerClock(
    private val state: ArrangerCoreState,
    private val ppq: Int, // Pulses Per Quarter note (ex: 96 ou 1920 dependendo do Style)
    private val onTick: (Int, Long) -> Unit // (CurrentTick, TargetTimeNs)
) {
    private var thread: Thread? = null
    private val isRunning = AtomicBoolean(false)

    fun start() {
        if (isRunning.getAndSet(true)) return

        thread = Thread {
            val startTimeNs = System.nanoTime()
            var nextTickTimeNsDouble = startTimeNs.toDouble()
            var nextTickTimeNs = startTimeNs

            while (isRunning.get()) {
                val currentBpm = state.bpm
                // Calcula a duração de 1 tick em nanossegundos
                // (60s / BPM) = Segundos por batida
                // Segundos por batida / PPQ = Segundos por tick
                val nanosPerTick = 60_000_000_000.0 / (currentBpm * ppq)

                var now = System.nanoTime()

                // Wait loop usando LockSupport
                if (now < nextTickTimeNs) {
                    while (now < nextTickTimeNs && isRunning.get()) {
                        val diffNs = nextTickTimeNs - now
                        if (diffNs > 1_500_000) {
                            // Se faltar mais de 1.5ms, descansa a thread
                            LockSupport.parkNanos(diffNs - 1_000_000)
                        } else {
                            // Busy spin de alta precisão pro último milissegundo
                        }
                        now = System.nanoTime()
                    }
                } else if (now > nextTickTimeNs + 50_000_000L) {
                    // Anti-Spiral: Se a thread pausou por mais de 50ms (ex: app em segundo plano)
                    // Resincroniza o relógio para evitar rajada ensurdecedora de notas
                    nextTickTimeNsDouble = now.toDouble()
                    nextTickTimeNs = now
                }

                if (!isRunning.get()) break

                val tick = state.currentTick.getAndIncrement()
                
                // Dispara o processamento do tick
                onTick(tick, nextTickTimeNs)

                nextTickTimeNsDouble += nanosPerTick
                nextTickTimeNs = nextTickTimeNsDouble.toLong()
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            name = "ArrangerClockThread"
            start()
        }
    }

    fun stop() {
        isRunning.set(false)
        thread?.interrupt()
        thread = null
    }
}
