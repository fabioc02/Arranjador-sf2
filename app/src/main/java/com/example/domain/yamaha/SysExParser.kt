package com.example.domain.yamaha

interface YamahaCommand {
    fun execute(xgEngine: XGEngine)
}

class GMResetCommand : YamahaCommand {
    override fun execute(xgEngine: XGEngine) {
        xgEngine.reset()
    }
}

class XGResetCommand : YamahaCommand {
    override fun execute(xgEngine: XGEngine) {
        xgEngine.reset()
    }
}

class MasterVolumeCommand(val volume: Int) : YamahaCommand {
    override fun execute(xgEngine: XGEngine) {
        xgEngine.masterVolume = volume
    }
}

class MasterTuneCommand(val tune: Int) : YamahaCommand {
    override fun execute(xgEngine: XGEngine) {
        xgEngine.masterTune = tune
    }
}

class ScaleTuneCommand(val data: ByteArray) : YamahaCommand {
    override fun execute(xgEngine: XGEngine) {
        // Implementation for scale tune
    }
}

class DSPParameterCommand(val data: ByteArray) : YamahaCommand {
    override fun execute(xgEngine: XGEngine) {
        // Implementation for DSP
    }
}

class UnknownYamahaCommand(val data: ByteArray, val type: String) : YamahaCommand {
    override fun execute(xgEngine: XGEngine) {}
}

class YamahaCommandFactory {
    fun createCommand(data: ByteArray): YamahaCommand? {
        if (data.isEmpty() || data[0] != 0xF0.toByte()) return null
        
        // Universal Non-Realtime (e.g., GM1 Reset)
        if (data.size >= 6 && data[1] == 0x7E.toByte()) {
            if (data[3] == 0x09.toByte() && data[4] == 0x01.toByte()) {
                return GMResetCommand()
            }
            return UnknownYamahaCommand(data, "Universal Non-Realtime")
        }
        
        // Universal Realtime (e.g., Master Volume)
        if (data.size >= 8 && data[1] == 0x7F.toByte()) {
            if (data[3] == 0x04.toByte() && data[4] == 0x01.toByte()) {
                val vol = data[5].toInt() + (data[6].toInt() shl 7)
                return MasterVolumeCommand(vol)
            }
            return UnknownYamahaCommand(data, "Universal Realtime")
        }
        
        // Yamaha (0x43)
        if (data.size > 2 && data[1] == 0x43.toByte()) {
            // XG Parameter Change (0x10 0x4C)
            if (data.size >= 9 && data[2] == 0x10.toByte() && data[3] == 0x4C.toByte()) {
                // XG Reset
                if (data[4] == 0x00.toByte() && data[5] == 0x00.toByte() && data[6] == 0x7E.toByte()) {
                    return XGResetCommand()
                }
                return UnknownYamahaCommand(data, "XG SysEx")
            }
            
            // Yamaha Proprietary (e.g. Master Volume specific to some old models, or other params)
            if (data.size >= 9 && data[2] == 0x10.toByte() && data[3] == 0x27.toByte() && data[4] == 0x30.toByte() && data[5] == 0x00.toByte() && data[6] == 0x00.toByte()) {
                return MasterVolumeCommand(data[7].toInt())
            }
            
            return UnknownYamahaCommand(data, "Yamaha Proprietary")
        }
        
        return UnknownYamahaCommand(data, "Unknown SysEx")
    }
}

class YamahaSysExDecoder(private val factory: YamahaCommandFactory = YamahaCommandFactory()) {
    fun decode(data: ByteArray): YamahaCommand? {
        return factory.createCommand(data)
    }
}

class YamahaCommandExecutor(private val xgEngine: XGEngine) {
    fun execute(command: YamahaCommand) {
        command.execute(xgEngine)
    }
}

class SysExParser(
    private val decoder: YamahaSysExDecoder = YamahaSysExDecoder(),
    private val executor: YamahaCommandExecutor) {
    fun parseAndExecute(data: ByteArray) {
        val command = decoder.decode(data)
        if (command != null) {
            executor.execute(command)
        }
    }
}
