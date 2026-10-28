package com.example.domain.yamaha

import com.example.domain.models.StyFile
import com.example.domain.models.MidiEvent

class YamahaController {
    val xgEngine = XGEngine()
    val executor = YamahaCommandExecutor(xgEngine)
    val sysExParser = SysExParser(executor = executor)
    val megaVoiceEngine = MegaVoiceEngine()
    val otsManager = OTSManager()
    val registrationMemory = RegistrationMemory()
    val dspLoader = DSPPresetLoader()
    val validator = StyleValidator()
    val repair = StyleRepair()
    
    fun processSysEx(data: ByteArray) {
        sysExParser.parseAndExecute(data)
    }
    
    fun loadStyle(style: StyFile) {
        // Trigger style validation and possibly repair
        val report = validator.validate(style)
        if (report.suggestedFixes > 0) {
            repair.repair(style)
        }
    }
}
