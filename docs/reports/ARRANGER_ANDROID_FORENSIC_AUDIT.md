# ARRANGER ANDROID — AUDITORIA FORENSE DE CÓDIGO (ATUALIZADA)

**Objetivo**: Recuperar o estado determinístico e identificar as regressões musicais antes de novas implementações.
**Alvo Principal**: Android atua como controlador (cérebro MIDI). Yamaha PSR-S750 atua como Tone Generator (somente sintetizador).

## 1. INVESTIGAÇÃO DO PROBLEMA DO BASS / CASM (PRIORIDADE P1)
**[CONFIRMED BY CODE]**
O problema do baixo **não é apenas "tocar a tônica"**, mas de fato ele **ignora completamente as inversões (bassKey)**. 
A análise direta da `BassRule` (em `YamahaRules.kt`) comprova a matemática implementada atualmente:
- A engine recebe `destinationChord.bassKey`, mas a variável fica **isolada/não utilizada**.
- A tabela `bassTableMajor/Minor` calcula a classe da nota baseada unicamente no *Root* (`destRoot`).

**Exemplos Simulados pelo Código (Assumindo Fonte = CMaj7):**
- **C** -> Código calcula `C2` (Correto)
- **C/E** -> Código calcula `C2` (ERRO: ignorou E, tocou a tônica)
- **C/G** -> Código calcula `C2` (ERRO: ignorou G)
- **Am** -> Código calcula `A1` (Correto, com octave wrap devido ao High Key)
- **Am/E** -> Código calcula `A1` (ERRO: ignorou E)
- **F** -> Código calcula `F2` (Correto)
- **G/B** -> Código calcula `G1` (ERRO: ignorou B)

**Conclusão**: O acompanhamento soará muito estranho e fora da intenção musical, pois a linha de baixo (Walking Bass, Pedal Bass) das inversões é destruída pela `BassRule` atual.

## 2. INVESTIGAÇÃO DO SCHEDULER / JITTER (PRIORIDADE P0)
**[CONFIRMED BY CODE]**
O código Kotlin atual comete um erro fatal de *scheduling* de áudio/MIDI em dois pontos distintos:

1. **No `MidiRhythmCreator.kt`**: O motor roda dentro de um `LaunchedEffect` (ligado ao ciclo de vida da UI no Compose). Ele usa `delay(sleepTime)`.
2. **No `ValidationPlayerViewModel.kt`**: O motor roda em `viewModelScope.launch(Dispatchers.Default)` usando `delay(1)`.

**O Problema do `delay()`**: Na JVM/Android, `delay()` devolve a thread para o OS Scheduler. Um pedido de `delay(1)` geralmente acorda em 2 a 15 milissegundos dependendo do uso da CPU, varrendo o lixo (Garbage Collection) ou priorizando animações (UI Thread). O MIDI exige precisão de <1ms.
Além disso, a função de saída `MidiState.sendNoteOn` **não** agenda o evento no futuro (ela marca a nota com `System.nanoTime()` no exato instante em que é invocada). Como o *loop caller* está engasgando/atrasando, os eventos MIDI chegam à porta USB já fora do compasso ("baixo lento", "bateria engolindo notas").

## 3. INVESTIGAÇÃO DO MOTOR C++ (GHOST ENGINE) E CHORD DETECTION
**[CONFIRMED BY CODE]**
- **Fluxo C++ Morto**: O método nativo `AudioEngine.onLiveMidiEvent` (que deveria enviar as notas tocadas no PSR-S750 para o C++) **NUNCA é chamado** no código Kotlin. A conexão foi cortada acidentalmente em commits anteriores. 
- **O Falso Positivo do Rhythm Creator**: Como o C++ nunca recebe notas, ele nunca chama de volta `AudioEngine.onArrangerChordDetected(...)`. Como resultado, a variável Kotlin `MidiRhythmState.detectedChord` NUNCA muda durante a gravação/preview.
- O Arranger Mode do Rhythm Creator tenta transpor baseado em `MidiRhythmState.detectedChord`, mas como este está inerte, a transposição falha, gerando a sensação de que nada está soando corretamente no acompanhamento.

## 4. MIDI CHANNELS, ROUTING & LOCAL CONTROL
**[CONFIRMED BY CODE] & [LIKELY BUG]**
- O PSR-S750 recebe `Local Control Off` (CC 122) via `VoiceEngine.mutePanelParts()`. Isso previne o eco direto da tecla do Yamaha.
- Porém, no `GlobalMidiRouter.kt`, quando a gravação/edição é ativada (`overrideRightHandChannel`), **todos os eventos MIDI IN (independente da zona de split)** são roteados para o canal que está sendo gravado.
- Exemplo prático: Se você gravar a Track `Rhythm 1` (Canal 10), apertar um acorde de Dó com a mão esquerda (Left) enviará "Nota Dó" direto pro Canal 10 do Yamaha. O PSR-S750 responderá tocando bumbos e pratos no meio da sua gravação.

## 5. PLANO DE CORREÇÃO EM ORDEM DE PRIORIDADE (ATUALIZADO)

*   **[P0] Reestruturação do MIDI Scheduler**: Abandonar `LaunchedEffect` e `delay(1)` para scheduling musical. Reimplantar o relógio em uma *Thread* ou mecanismo nativo (Oboe Callback / C++) para garantir temporização determinística. (Isso resolve 90% dos atrasos, baixo lento e notas emboladas).
*   **[P1] Restaurar o Chord Analyzer / Arranger Mode**: Ou reconectamos o `AudioEngine.onLiveMidiEvent` ao `GlobalMidiRouter`, ou consolidamos a detecção de acordes integralmente na `ChordEngine` (Kotlin), fazendo-a atualizar o `MidiRhythmState.detectedChord` em tempo real baseado no `splitPoint`.
*   **[P1] Correção do Yamaha Bass Table (NTT)**: Reescrever a lógica `BassRule` para usar o `bassKey` corretamente nas inversões, respeitando o CASM original.
*   **[P2] GlobalMidiRouter (Drum Leak)**: Filtrar a entrada MIDI no router; separar canais Left/Right para que a mão esquerda alimente APENAS o motor de acordes (Chord Engine), enquanto a mão direita alimenta o `overrideRightHandChannel` (gravação).
*   **[P3] Rhythm Creator e Quantização**: Reestruturar a interface para aproximar os controles (Medidas, Quantização, REC) da Timeline. Preservar o `originalTick` em memória para permitir "Undo Quantize" por Track.

**NENHUMA ALTERAÇÃO DE CÓDIGO REALIZADA.** A aguardar orientações.
