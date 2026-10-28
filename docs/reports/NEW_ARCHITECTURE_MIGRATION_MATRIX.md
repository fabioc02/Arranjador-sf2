# Matriz de Migração (New Architecture)

| Funcionalidade Antiga | Onde estava (Old) | Onde ficará (New) | Status |
| :--- | :--- | :--- | :--- |
| **Relógio / Tick Musical** | `RealTimeMidiScheduler.kt` (misto com UI) | `domain/arranger/ArrangerClock.kt` & `ArrangerEngine.kt` | Pendente |
| **Estado de Reprodução (Play/Stop/BPM)** | `MidiRhythmState.kt` (Compose `MutableState`) | `domain/arranger/ArrangerCoreState.kt` (Atomic/Volatile) | Pendente |
| **Envio MIDI / MidiManager** | `MidiState.kt` | `domain/midi_core/MidiDispatcher.kt` | Pendente |
| **Parser STY / CASM** | `domain/midi/CasmParser.kt`, `StyReader.kt` | `domain/style/` e `domain/casm/` (Refatorados para ler sem travar thread) | Pendente |
| **Transposição e Limites (CASM)** | Embutido no `MidiRhythmCreator.kt` loop | `domain/casm/CasmEngine.kt` | Pendente |
| **Detecção de Acordes (Chord)** | Misturado no loop principal / Compose | `domain/arranger/ChordDetectorCore.kt` | Pendente |
| **LEFT / LEFT HOLD** | Parcial / Embutido na UI | `domain/arranger/KeyboardSplitter.kt` & `LeftHoldState.kt` | Pendente |
| **Drum Setup (SysEx/NRPN)** | `DrumSetupDialog.kt` (Chamada direta) | `ArrangerEngine` processará fila de Comandos (SysEx Thread-Safe) | Pendente |
| **Gerenciamento de Seções (Main/Fill)** | No `currentTick == 0` check da UI | `domain/arranger/SectionManager.kt` | Pendente |
| **Gravação MIDI / Quantização** | Embutido no loop da UI | `domain/style/RecordingEngine.kt` | Pendente |
| **Exportação .STY** | `MidiFileCreator` e `CasmWriter` | `domain/style/StyleExporter.kt` | Pendente |
| **UI - Player** | `MidiRhythmCreator.kt` (gigante) | `ui/player/PlayerScreen.kt` | Pendente |
| **UI - Creator** | `MidiRhythmCreator.kt` (gigante) | `ui/creator/CreatorScreen.kt` | Pendente |
| **UI - Inspector** | - | `ui/inspector/InspectorScreen.kt` | Pendente |
| **UI - CASM Trace** | - | `ui/casmtrace/CasmTraceScreen.kt` | Pendente |
| **UI - MIDI Monitor** | Embutido no log/console | `ui/midimonitor/MidiMonitorScreen.kt` | Pendente |
| **UI - EXT MIDI** | `ExternalMidiTimbreControl.kt` | `ui/extmidi/ExtMidiScreen.kt` | Pendente |
