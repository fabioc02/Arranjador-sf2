package com.example.domain.validation

data class MemorySnapshot(
    val usedMemoryKb: Long,
    val freeMemoryKb: Long,
    val totalMemoryKb: Long,
    val maxMemoryKb: Long
)

class MemoryMonitor {
    fun takeSnapshot(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val total = runtime.totalMemory()
        val free = runtime.freeMemory()
        val used = total - free
        val max = runtime.maxMemory()
        
        return MemorySnapshot(
            usedMemoryKb = used / 1024,
            freeMemoryKb = free / 1024,
            totalMemoryKb = total / 1024,
            maxMemoryKb = max / 1024
        )
    }
    
    fun gc() {
        System.gc()
    }
}
