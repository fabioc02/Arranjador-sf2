# Relatório de Auditoria: Arranger Engine & Midi Rhythm Creator
*(Nenhuma linha de código foi alterada. Este relatório foi gerado através de varredura nos fontes do aplicativo para confrontar as suspeitas.)*

## 1. Engasgos, Notas Presas e Crash (Performance no J5)
**Fatos encontrados no código:**
*   **Notas Presas na Gravação:** O erro foi rastreado até o `MidiRhythmCreator.kt` (linhas ~136-167). O ouvinte MIDI de entrada possui a seguinte condição global: `if (MidiRhythmState.isRecording)`. O problema é que isso envolve TANTO o `Note On` quanto o `Note Off`. Se o usuário apertar uma tecla, desativar o botão REC na tela, e depois soltar a tecla, a mensagem de `Note Off` é completamente ignorada. A nota fica na lista `activeRecordingNotes` para sempre.
*   **Causa dos Engasgos (Stuttering) e Crashes:** O `RealTimeMidiScheduler.kt` cria uma Thread de prioridade máxima (`Thread.MAX_PRIORITY`) em loop contínuo (busy-spin/park). Dentro desse loop de áudio de altíssima frequência, ele atualiza a variável de estado do Compose `MidiRhythmState.uiTick = currentTick` a cada 4 ticks (aprox. 6 vezes por tempo). Atualizar um Estado do Jetpack Compose milhares de vezes por minuto a partir de uma Background Thread inunda a Main Thread (UI) de pedidos de redesenho (recomposition). Em um Galaxy J5 (2GB), isso esgota a CPU rapidamente, força o Garbage Collector a travar o app (engasgo), e eventualmente causa um ANR (Application Not Responding) ou OOM, resultando no crash relatado.

## 2. A Tonalidade do Baixo e o CASM
**Fatos encontrados no código:**
*   A suspeita de que "gravar em G bagunça o baixo" foi **confirmada 100% pelo código**.
*   A classe `BassRule` (`YamahaRules.kt`) de fato JÁ implementa a tabela original SFF da Yamaha (`bassTableMajor = [0, 0, 2, 4, 4, 5, 7, 7, 9, 9, 10, 11]`). Ela não faz transposição linear burra, ela faz o mapeamento correto.
*   **O Erro Real:** Na classe `ArrangerEngine.kt`, a função responsável pela transposição no playback (`transposeNote`) possui a seguinte linha *hardcoded* (fixa):
    `val domainSource = com.example.domain.models.Chord(0, com.example.domain.models.ChordType.Major7) // CMaj7`
*   Ou seja, o motor atual **mente** para a `BassRule`. Não importa em qual tom você gravou no Rhythm Creator, o arranjador sempre diz à tabela de baixo que a nota de origem veio de Dó (C). Isso corrompe o cálculo dos intervalos se a frase original estava em Sol (G).
*   **Solução Necessária:** O `RhythmTrack` precisa salvar qual foi a "Source Root" da gravação, e passar essa informação real para o `ArrangerEngine` em vez de fixar em `C`.

## 3. Slider L/R (Panning CC10)
**Fatos encontrados no código:**
*   A interface do Slider dispara corretamente a instrução: `MidiState.sendControlChange(track.channel - 1, 10, track.pan)`.
*   O envio passa pelo `MainActivity.kt` e deságua na API MIDI nativa. 
*   **Onde a corrente quebra:** O `RealTimeMidiScheduler` (durante o Playback) só envia eventos de Control Change (CC) que estão gravados na lista `ccEvents` da trilha no tempo da música. Quando você dá *Play*, o valor do Slider que está na tela **não é injetado** no motor MIDI no compasso zero (Tick 0). Se você não mexer no slider *enquanto* a música toca, o sintetizador nunca recebe a ordem de Pan L/R e mantém o som no centro.
*   **Solução Necessária:** O Player precisa fazer um "flush" dos valores estáticos da UI (Volume, Pan) para todos os canais assim que o Play é apertado.

---
*Conclusão da Auditoria:* Todos os bugs relatados no teclado físico possuem raízes lógicas claras no código atual. A hipótese de perda de NoteOff foi confirmada. A hipótese da tonalidade no CASM foi confirmada. A hipótese do slider não enviar CC10 no Playback foi confirmada.
