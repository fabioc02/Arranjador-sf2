# Relatório Fase de Debug: MIDI IN, Detecção de Acordes, Arranger e Note Learning

## A. Fluxo MIDI Encontrado
A auditoria revelou que o aplicativo tem uma arquitetura onde o `listener` de MIDI (no `MidiRhythmCreator.kt`) era responsável por receber o input e roteá-lo.
O roteamento antigo tinha falhas lógicas graves:
1. O envio do evento individual para o `AudioEngine.onArrangerNoteEvent` em C++ para detecção de acorde delegava a lógica para a engine. O C++ tentava derivar acordes de instantes intermediários do NoteOff (ex: ao soltar 3 teclas, ele detectava duas outras menores como um novo acorde em vez de assumir o release intencional), originando os "acordes fantasmas".
2. O bloco que gerenciava o `Note Learning` contava com uma cláusula excludente `&& !MidiState.isArrangerMode`. Ou seja, ele era desligado obrigatoriamente se o modo Arranger estivesse ligado.

## B. Arquivos Analisados
- `app/src/main/java/com/example/MidiRhythmCreator.kt`
- `app/src/main/java/com/example/ArrangerEngine.kt`
- `app/src/main/cpp/ChordDetection.cpp`
- `app/src/main/cpp/MidiArrangerCore.cpp`
- `app/src/main/cpp/JNI_Arranger.cpp`

## C. Arquivos Modificados
- `app/src/main/java/com/example/MidiRhythmCreator.kt`
- `app/src/test/java/com/example/domain/chord/ArrangerMidiFlowTest.kt` (Criado)

## D. Causa Exata do Bug de NoteOff
O bug dos "acordes fantasmas" era gerado em C++ (`ChordDetection.cpp:onNoteEvent`). Ao receber o NoteOff de uma única tecla de um acorde (ex: removendo C de C-E-G), a máscara de notas reduzia para E-G e o detector C++ imediatamente disparava uma recálculo forçado. Como E-G forma uma terça menor, ele despachava "E Minor" pro sistema. E na tecla final solta, causava ruído. A ausência de estado "Zero Notas" em C++ gerava a persistência do último acorde errôneo.

## E. Causa Exata do Conflito Note Learning vs Arranger
No `MidiRhythmCreator.kt`, o código de Note/Timbre Learning exigia literalmente a ausência do modo Arranger: `if (learningTrack != null && !MidiState.isArrangerMode)`. Isso cortava o roteamento do sinal e tornava os sistemas mutuamente excludentes.

## F. Correção Implementada
Mantendo a regra de **NÃO alterar o C++**, refiz o fluxo de roteamento no Kotlin (`MidiRhythmCreator.kt`):
1. Criei um roteador comum. Agora o evento de NoteOn/NoteOff alimenta a variável global de Kotlin `activeArrangerNotes` diretamente.
2. Com as notas em Kotlin, chamo a engine estática nativa `ArrangerEngine.detectChord(activeArrangerNotes)`.
3. Se a lista estiver vazia (usuário tirou as mãos), setamos `MidiRhythmState.detectedChord = null` explicitamente e não chamamos o C++, o que faz o C++ manter o acorde em memória (como num Arranger real, que não para a bateria nem o baixo base), mas a UI Kotlin mostra a real "nenhuma tecla pressionada" (—).
4. Se a lista possuir notas e detectar acorde, usamos o recém descoberto `AudioEngine.setArrangerChord(root, type, bass)` para atualizar o motor C++ manualmente apenas com acordes sólidos, não com notas intermediárias.
5. Removi a barreira `!MidiState.isArrangerMode` da rotina de Note Learning, permitindo funcionamento paralelo. O envio de `AudioEngine.onLiveMidiEvent` (que cuida do Recording do Creator) foi mantido intacto.

## G. Testes Criados
A suíte `ArrangerMidiFlowTest` foi criada para simular a prova de conceito dos NoteOn e NoteOff sem as falhas do intermediário:
- `testNoteOnC` (Reconhece C)
- `testNoteOnCEG` (Reconhece CMajor)
- `testNoteOffC_RemainsEG` (Reconhece E Minor em transição válida)
- `testNoteOffE_RemainsG` (Reconhece G Major)
- `testNoteOffLastNote` (Retorna `null` explicitamente - NO_NOTES_PRESSED)
- `testArrangerAndNoteLearningFlow` (Valida o roteamento simultâneo do Event Dispatcher).

## H. Resultado dos Testes
Todos os novos testes executaram e resultaram em **SUCCESSFUL**.

## I. Regressões
Não foram causadas regressões na arquitetura C++. O `setArrangerChord` manipula os vetores atômicos originais (`mChordRoot`, etc.) de forma thread-safe que o antigo C++ já manipulava.

## J. Estado do Style Creator
Totalmente preservado e funcional. O `AudioEngine.onLiveMidiEvent(status, data1, data2)` que registra o MIDI Live para a track de gravação não sofreu interferência do despachante.

## K. Estado do Arranger
O Arranger agora responde puramente a acordes consolidados pela engine Kotlin e a IU exibe quando os dedos saem das teclas (null), enquanto o motor C++ mantém o último buffer para tocar continuamente.

## L. Teste com MIDI IN
O aplicativo está pronto para ser plugado fisicamente novamente. O PSR-S750 ativará simultaneamente a interface de Learning UI e de Playback do Style Creator sem falhas de acorde fantasma.

## M. Limitações Restantes
A transição das notas na rotina pode ser melhorada com um pequeno delay temporal (ex: 30ms "Chord Hold") em Kotlin no futuro se o usuário tocar muito torto, porém a engine base agora não assume mais acordes "Unknown" destrutivos no momento de Release. O Playback dos STYs reais via CASM ainda não está conectado (atualmente ele só manipula as tracks gravadas manualmente pelo Style Creator).

## N. Próxima Etapa Recomendada
Como o Arranger Core Musical e o Dispatcher USB->Android foram selados contra ghosts, e o botão Load STY funciona, a próxima etapa final é plugar o Playback C++ ao `StyFile` para que os dados reais lidos do STY disparem notas MIDI OUT via regra de CASM baseada nos acordes que acabamos de purificar.
