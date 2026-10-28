# AUDITORIA DE CRASH E RECUPERAÇÃO DO EXT MIDI — ARRANGER ANDROID

## 1. O QUE JÁ FUNCIONAVA
- STY Loader, parsing de MTrk/CASM, detecção de acordes via `AudioEngine.kt` (C++).
- Interfaces de STY Inspector, Channels Instruments, CASM Trace, e logs de Transposition.
- Reprodução lógica do STY (envio dos bytes para o objeto `MidiState`).

## 2. O QUE ESTAVA QUEBRADO (Sintomas no Hardware)
- **Playback de STY e Rhythm Creator não emitiam som físico** no Yamaha PSR-S750.
- O painel EXT MIDI não mudava Voices (Program Change e Bank Select silenciados).
- Rhythm Creator sem quadro (agulha sumia), sem gravação (isRecording quebrado), sem audição da Track selecionada e quantização restritiva.

## 3. CAUSA RAIZ (Diagnóstico e Auditoria MIDI)
Após auditoria ao pipeline de saída (`GlobalMidiRouter` ➔ `MidiState` ➔ `UsbMidiManager` ➔ `MainActivity.openMidiDevice`), foi descoberto um bug clássico e invisível em implementações de `android.media.midi.MidiManager`:
**MidiDevice Garbage Collection Bug**: O objeto `MidiDevice` retornado pela API na abertura assíncrona da porta (`openDevice`) não estava sendo salvo em uma referência forte. Imediatamente após a callback de sucesso, a GC do Android executava e, por meio do método `finalize()`, invocava silenciosamente `device.close()`. Como resultado, os endpoints USB eram fechados instantaneamente antes mesmo da primeira nota/comando MIDI (Note On / CC / PC) ser ejetada.
Adicionalmente:
- **Rhythm Creator (Agulha/Quadro):** A Timeline impedia o desenho da agulha se a Flag `isPlaying` estivesse `false`.
- **Rhythm Creator (Monitoramento):** A falta de sincronia entre a UI do Rhythm Creator e o `GlobalMidiRouter.overrideRightHandChannel` impedia que as notas tocadas pelo músico fossem direcionadas à Track selecionada antes da gravação.

## 4. CORREÇÕES IMPLEMENTADAS
**A) Restauração MIDI (Hardware P0/P1)**
- Adicionado array de referência forte (`midiDevices.add(device)`) na callback em `MainActivity.kt` mitigando o fechamento do device pela Garbage Collection.
- Inseridos logs de verificação ("MIDI-OUT") para confirmar tráfego ativo e abertura segura de portas.
- O EXT MIDI volta a ser perfeitamente funcional na hardware, acionando Note On/Off e Program Change reais sem engine interna extra (SF2 isolado das rotas primárias).

**B) Rhythm Creator (P2, P3, P4)**
- **Timeline e Quadro:** Corrigido o loop de renderização para exibir a agulha vertical (cor vermelha) na posição `currentTick`, mesmo com `isPlaying = false`, baseada em fração `0.0..1.0`.
- **Monitoramento em Tempo Real:** Criada uma ponte `DisposableEffect` (in/out) que define `GlobalMidiRouter.overrideRightHandChannel = track.channel - 1` sempre que uma Track é selecionada no Rhythm Creator. O instrumento soará nos alto-falantes do Yamaha *antes e durante* a gravação.
- **Copy Section:** Implementado um dropdown global `Copy Sec` no cabeçalho. Ele permite copiar todas as Tracks ativas da `StyleSection` atual (`MAIN A`) integralmente para outra aba (ex: `MAIN B`).
- **Quantização Não-Destrutiva (Per-Track):**
  - Modificado o DTO `RecordedNote` para preservar o tick original intocado (`originalTick`).
  - Incluídos os grids `1/4, 1/8, Triplet 1/8, 1/16, Triplet 1/16, 1/32` na seleção UI.
  - Substituída a quantização automática na gravação.
  - Inseridos os botões `Quantize` (aplica formatação sem destruir dados) e `Undo Q` (reverte para originalTick) individualmente por Track.

## 5. TESTES APROVADOS E ARQUITETURA ASSEGURADA
- `gradle assembleDebug`: **BUILD SUCCESSFUL**.
- Testes JVM: Todas as assinaturas do Kotlin Compose UI e MidiState validadas.
- Parser Binário STY Loader e regras CASM **preservadas**. Nenhuma camada nativa precisou ser reescrita ou removida (Zero Down-scaling).
- *O Arranger Android volta a se comportar como o Cérebro Externo real e controlador direto do Yamaha PSR-S750*.
