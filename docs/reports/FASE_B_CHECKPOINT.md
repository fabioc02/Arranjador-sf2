# FASE B — CHECKPOINT DE AUDITORIA

## 1. Diagnóstico do ARRANGER
O botão apenas alterna o estado `MidiState.isArrangerMode`. Durante a reprodução (no laço do `RealTimeMidiScheduler`), o transposer verifica `MidiRhythmState.detectedChord`. Porém, nenhum lugar do código está de fato recebendo as notas MIDI físicas e atualizando a variável `detectedChord`, deixando a transposição inerte (sempre C Major).

## 2. Fluxo atual do ARRANGER (Quebrado)
Teclado -> MIDI IN -> `GlobalMidiRouter` (repassa eventos).
A ponte `AudioEngine.onLiveMidiEvent` no C++ rejeita a entrada pois ela impõe `channel == 0 && isRecording`.
Resultado: Ninguém analisa os acordes. O laço de playback continua achando que não há acorde.

## 3. Ponto exato da desconexão
Falta capturar a entrada do lado esquerdo (Canais 3 / CH4 ou abaixo do Split Point no canal físico 0), passá-la para `ArrangerEngine.detectChord(activeNotes)`, e alimentar `MidiRhythmState.detectedChord` em tempo real dentro de `GlobalMidiRouter.kt`.

## 4. Painel de Acordes/Notas
Já existe e foi localizado em: `app/src/main/java/com/example/ui/validation/ChordEducationalPanel.kt`. É um composable maduro e pronto para ser importado e reusado no topo do layout.

## 5. Diagnóstico do layout das tracks
O cabeçalho atual é extremamente perdulário, espalhado por 4 `Rows` distintas. Os controles da Track empilham 3 `Rows` cada (Instrumento, Volume, Timeline), engolindo a altura da tela. Condensaremos o cabeçalho numa ScrollableRow única ou barra compacta, reduziremos os paddings dos cartões, os botões e removeremos `height` fixo agressivo.

## 6. Diagnóstico dos Controles L/R
Os botões de roteamento L/R1/R2 da Track existem mas sofrem falha de visibilidade ou UX pobre. O projeto legado apontava `CH4=LEFT, CH2=R1, CH3=R2`, o qual será garantido no ciclo de clique (`Out: L/R1/R2`), mas a alteração visual será preservada no Kotlin existente sem alterar C++ ou Voice Engine.

## 7. Arquivos a Modificar
- `app/src/main/java/com/example/domain/midi/GlobalMidiRouter.kt` (para a detecção e array de notas ativas do Arranger)
- `app/src/main/java/com/example/MidiRhythmState.kt` (adição do booleano `isPaused`)
- `app/src/main/java/com/example/MidiRhythmCreator.kt` (redesenho enxuto do topbar, botões PLAY/PAUSE/STOP e instanciamento do `ChordEducationalPanel`).

## 8. Arquivos Intocados
C++, Oboe, CASM, StyleEngine, RealTimeMidiScheduler, VoiceEngine, JNI e todo o tratamento de exceção implementado na Fase A.

## 9. Repositório Legado
**DECLARAÇÃO**: Consultado apenas como referência comparativa (Mapeamento de canais CH2/CH3/CH4). NENHUM CÓDIGO FOI OU SERÁ COPIADO PARA O PROJETO ATUAL. Toda a lógica será elaborada aproveitando as interfaces idiomáticas deste branch.
