# Auditoria de Recuperação de Contexto do Arranger Android

## 1. O QUE É O PROJETO
* **Projeto**: Arranger Android.
* **Finalidade**: Aplicativo Android profissional de arranjador, Style Creator e Style Player. Funciona como cérebro/controlador que lê arquivos Yamaha `.STY`, permite criação de ritmos próprios e se comunica via USB MIDI para usar o gerador de som de um teclado Yamaha físico.
* **Arquitetura**: O Android atua como controlador (Arranger). A saída MIDI USB é enviada para o Yamaha PSR-S750, que atua como Tone Generator (gerador de som). O uso de SoundFonts (`.SF2`) internamente pelo Android é um recurso legado e não compõe o fluxo principal de produção (Android -> MIDI -> PSR-S750).

## 2. LINGUAGENS E CAMADAS
* **Kotlin**: A linguagem principal do projeto. Cobre praticamente toda a lógica de negócio, UI e integração, incluindo:
  * UI (Jetpack Compose, `MidiRhythmCreator.kt`, painéis).
  * Motores de MIDI e Tempo (`MidiEngine.kt`, `StyleClock.kt`, `TickProcessor.kt`, `GlobalMidiRouter.kt`).
  * Processamento do Estilo (`StyReader.kt`, `CasmParser.kt`).
  * Transposição e Acordes (`ChordEngine.kt`, `CasmTranspositionEngine.kt`).
  * Conexão (`UsbMidiManager.kt`, classes de `android.media.midi`).
* **C++ / JNI**: Camada nativa utilizada para o engine de áudio e tarefas de baixa latência (`AudioEngine.cpp`, `MidiArrangerCore.cpp`, `JNIWrapper.cpp`).
* **Regras de Camadas**: A arquitetura nativa em C++ será mantida. Nenhuma engine C++ será convertida para Kotlin, e nenhum componente JNI/Oboe será removido. O foco de estabilização está na camada Kotlin responsável pela comunicação MIDI e matemática do arranjador.

## 3. ENGINES EXISTENTES (A SEREM PRESERVADAS)
* **MIDI & Comunicação**: `MidiEngine`, `UsbMidiManager`, `GlobalMidiRouter`, `MidiState` (dentro de `MainActivity`).
* **Clock & Sincronização**: `TickProcessor`, `StyleClock`, `MidiClock`.
* **Style & Reprodução**: `StylePlaybackEngine`, `CasmTranspositionEngine` (e tabelas de transposição referenciadas em `domain/chord/reference/tables`).
* **Gravação & Edição**: `MidiRhythmCreator` (traz os estados `RhythmTrack`, `RhythmState`, `RecordedNote`).
* **Audio & Vozes**: `VoiceEngine` (Kotlin) e `AudioEngine` (C++ nativo).

## 4. CONECTIVIDADE MIDI
* **Conexão Física**: O PSR-S750 se conecta via USB. (Foi confirmado que a comunicação está ativa quando utilizada na porta USB correta do hardware).
* **Entrada (MIDI IN)**: O aplicativo usa o `android.media.midi` nativo do Android. O `MidiReceiver` no `MainActivity.kt` escuta a porta de entrada e propaga mensagens via callback (que o `GlobalMidiRouter` intercepta).
* **Saída (MIDI OUT)**: O evento sai pelas instâncias de `MidiInputPort` abertas armazenadas no `MidiState`. Funções como `MidiState.sendNoteOn` ou `sendProgramChangeRaw` convertem o comando em bytes (buffer) e enviam via `port.send(...)`.
* **Roteamento**: O `GlobalMidiRouter.kt` decide para onde o evento de entrada vai. Se `overrideRightHandChannel` está ativo (ex: gravando no Rhythm Creator), ele redireciona as notas para a track que está sendo gravada.

## 5. PROCESSAMENTO DE ARQUIVOS STY
* **Parser de STY**: Encontra-se no pacote `data/parser/` (ex: `StyReader.kt`, `MidiTrackParser.kt`).
* **CASM**: É parseado por `CasmParser.kt` e tipado via `CasmModels.kt`.
* **NTR / NTT**: Lidos pelo parser e injetados no sistema; a execução dessas regras no momento do Playback é encabeçada pela engine `CasmTranspositionEngine.kt`, apoiada pelas tabelas em `domain/chord/reference/tables`.
* **Playback**: Realizado pelo `StylePlaybackEngine.kt`, que utiliza o relógio (`StyleClock`, `TickProcessor`) para ler os eventos MIDI sequenciados das seções e dispará-los pelo roteador.

## 6. RHYTHM CREATOR
* **Implementação**: Escrito em Jetpack Compose e localizado principalmente em `app/src/main/java/com/example/MidiRhythmCreator.kt`.
* **Como abre**: Chamado a partir de um botão na tela de `ExternalMidiControlPanel.kt`.
* **Como grava**: Escuta os eventos no `MidiState.midiListeners`; se `MidiRhythmState.isRecording` for verdadeiro, captura o NoteOn gerando instâncias de `RecordedNote` que depois ficam associadas à `RhythmTrack`.
* **Como reproduz**: Um loop atrelado ao `TickProcessor` varre as notas das `RhythmTrack`s comparando os ticks calculados de compasso.
* **Timeline/Interface**: É desenhada no mesmo arquivo através de chamadas ao `Canvas` do Compose. A agulha de progressão obedece aos cálculos matemáticos do tick atual versus `totalTicks`.

## 7. VOICES (Right 1, Right 2, Left)
* **Canais Atuais**:
  * **Right 1**: Canal 0 interno (MIDI 1).
  * **Right 2**: Canal 1 interno (MIDI 2).
  * **Left**: Canal 2 interno (MIDI 3).
* **Definição**: Configuradas em `domain/models/VoiceEngine.kt` e nas memórias (RegistrationMemory, OTSManager).
* **Program Change e Bank Select**: Ocorrem via envio de MSB/LSB e PC nas rotinas de configuração de vozes da `VoiceEngine.kt` invocando as chamadas primárias do `MidiState`.

## 8. PROBLEMAS ATUAIS DIAGNOSTICADOS NA AUDITORIA
* **Problemas de MIDI & Voices**:
  * Ao selecionar instrumentos para gravar no Rhythm Creator, os sons (ex: bateria e pianos) acabam soando ao mesmo tempo. Pode haver um conflito no `overrideRightHandChannel`, falha na restauração do canal físico ou problema com o envio de `Local Control Off` (CC# 122).
* **Problemas no Rhythm Creator & Recording**:
  * A timeline/agulha deixa de aparecer (problema visual no Compose Canvas / dessincronia do `TickProcessor`).
  * Problemas com o modo Preview/Arranger (playback errático das tracks criadas).
* **Problemas de Musical Clock & BPM**:
  * Ao incluir múltiplas assinaturas e subdivisões complexas (2/4, 3/4 até 4/2), cálculos de `ticksPerBeat`, `totalTicks` e milissegundos por compasso aparentemente quebraram a relação de BPM vs PPQ. Resulta em acompanhamento e graves atrasados ("baixo lento", playback fora do tempo).
* **Problemas de Quantização & Copy Section**:
  * A UI exibe opções para `Copy` e `Copy Sec`, mas os botões estão possivelmente dispersos visualmente. A lógica necessitará refino para garantir quantização independente (não destrutiva e separada por peça - bumbo, caixa - na mesma Rhythm Track).
* **Problemas de Style Player & CASM & Bass**:
  * Mudanças de acorde resultam em comportamento inadequado do Baixo (não toca a tônica corretamente) e certas baterias mudam de timbre ou perdem o ritmo. Suspeita-se da forma como o `Bypass` (NTT=00) ou `Bass Table` (NTT=08) está interagindo entre a Seção/NTR e o canal MIDI externo no `CasmTranspositionEngine`.

## 9. DOCUMENTAÇÃO YAMAHA NO WORKSPACE
* Documentos relacionados ao **CASM** e à Engenharia do **Baixo** foram **ENCONTRADOS**:
  * `docs/YAMAHA_CASM_BASS_LOGIC.md`
  * `docs/YAMAHA_IMPLEMENTATION_GUIDE.md`
* Documentação oficial do **PSR-S750 (Data List e Manual)**:
  * **NÃO ENCONTRADA** (nenhum PDF ou arquivo Markdown de manual completo e data list está disponível no diretório `docs/` ou root).
  * **Ação Recomendada**: NECESSÁRIO REENVIO.
