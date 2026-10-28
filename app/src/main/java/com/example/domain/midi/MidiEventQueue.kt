package com.example.domain.midi

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray

class MidiEventQueue(capacity: Int = 1024) {
    private val buffer = AtomicReferenceArray<MidiMessage>(capacity)
    private val head = AtomicInteger(0)
    private val tail = AtomicInteger(0)
    private val mask = capacity - 1

    init {
        require(capacity and (capacity - 1) == 0) { "Capacity must be a power of 2" }
    }

    fun offer(message: MidiMessage): Boolean {
        var currentTail = tail.get()
        var nextTail = (currentTail + 1) and mask

        if (nextTail == head.get()) {
            return false // Queue is full
        }

        buffer.set(currentTail, message)
        tail.set(nextTail)
        return true
    }

    fun poll(): MidiMessage? {
        val currentHead = head.get()
        if (currentHead == tail.get()) {
            return null // Queue is empty
        }

        val message = buffer.get(currentHead)
        buffer.set(currentHead, null) // Avoid memory leak
        head.set((currentHead + 1) and mask)
        return message
    }
}
