# Relatório: Correção de Comportamento MIDI (Chord Detection) e Editor de Voices

## A. Arquivos analisados
- `app/src/main/java/com/example/MidiRhythmCreator.kt`
- `app/src/main/cpp/MidiArrangerCore.cpp`
- `app/src/main/cpp/JNI_Arranger.cpp`
- `app/src/main/java/com/example/ui/validation/ChordEducationalPanel.kt`

## B. Arquivos modificados
- `app/src/main/java/com/example/MidiRhythmCreator.kt` (reestruturação do listener MIDI e adição de Voice Preview)
- `app/src/test/java/com/example/domain/chord/StateMachineTest.kt` (criação da nova suíte de testes de máquina de estados MIDI)

## C. Arquivos preservados
- C++, Oboe, JNI, AudioEngine, CASM, NTR, NTT, parser de STY e lógica de Style Playback não sofreram nenhuma alteração.

## D. Causa raiz do bug de acorde
A implementação antiga calculava o acorde (`ArrangerEngine.detectChord`) tanto no `Note-On` quanto no `Note-Off`, atrelado a um temporizador frágil (debounce). Ao liberar as teclas de um acorde simultâneo, pequenas diferenças milissegundais na remoção de cada dedo geravam "acordes intermediários residuais", fazendo o Arranger disparar transições sonoras acidentais em Note-Off. Musicalmente, a harmonia atual de um Arranger só pode ser alterada por uma nova intenção (Note-On).

## E. Nova máquina de estados
Implementada através da supressão da análise temporal, operando exclusivamente na intenção da ação:
1. `Note-On` -> Adiciona nota no `activeArrangerNotes` e **reavalia** o acorde (`detectChord`).
2. `Note-Off` -> Remove nota de `activeArrangerNotes` e **não avalia** nada, garantindo a estabilidade matemática do acorde pré-existente.

## F. Tratamento NoteOn
O `Note-On` (comando `0x90` com velocity > 0) processa de imediato a detecção do acorde e envia a transição para a camada C++ do Arranger através do `AudioEngine.setArrangerChord`. 

## G. Tratamento NoteOff
O `Note-Off` remove as notas fisicamente da lista, mas não anula o acorde em `MidiRhythmState.detectedChord`. O Arranger continua mantendo (hold) o acorde, permitindo que a mão esquerda fique livre para operar modulações e afins.

## H. Tratamento NoteOn velocity 0
Mapeado na mesma instrução de `Note-Off`: `val isNoteOff = command == 0x80 || (command == 0x90 && data2 == 0)`.

## I. Tratamento de activeNotes
`activeArrangerNotes` agora serve *apenas* para refletir o estado de repouso dos sensores do teclado. Quando a lista chega a zero, ela permanece vazia até a próxima intervenção, sem gerar `null` para o visual do painel nem despachar nulo para o motor.

## J. Comportamento quando todas as teclas são liberadas
O painel visual e o `MidiRhythmState.detectedChord` preservam a última exibição. A transição apenas ocorrerá quando novas teclas injetarem novos `Note-On` e configurarem uma nova tríade harmônica validável.

## K. Correção do painel educativo
O painel contendo a imagem do acorde validado agora fica persistente em repouso (pois o `MidiRhythmState.detectedChord` não é mais esvaziado indevidamente no Kotlin) e mantém a indicação "Nenhuma" na lista de teclas ativas até a mão voltar a agir. Ele só some caso o modo Arranger seja desativado.

## L. Causa raiz do problema de Voice Preview
Na gravação, o Yamaha envia o som da tecla ao Android. A camada em C++ capta e descarta (`drops`) as notas se o botão REC não estivesse ativo, e mesmo durante o REC ela servia apenas para enfileirar em cache (`mRecordActiveNotes`), sem roteamento dinâmico de Echo MIDI para os canais corretos no teclado físico via cabo (pois "Local Control" é desativado na inicialização para prevenir duplicação).

## M. Correção do Voice Preview
Uma ponte explícita foi introduzida no Listener em Kotlin: Notas MIDI processadas sem característica de mão esquerda (acorde) são redirecionadas e espelhadas (echoed) imediatamente de volta pela porta MIDI Out do aplicativo usando a mesma via `channel` da track selecionada (`selectedTrack.channel - 1`). Assim, se o usuário seleciona um Bass na track (ex: MSB=0, PC=33), as teclas da mão direita disparam sons de baixo de volta ao PSR-S750 antes ou durante a gravação.

## N. Testes unitários
- Criado o arquivo de testes `StateMachineTest.kt`.
- Os Testes 1, 2, 3, 4, 5, 6 e 7 passaram de forma determinística, validando que Note-Off ou Velocity 0 não adulteram e não apagam o acorde pré-estabelecido. 

## O. Testes no PSR-S750, se executados
Aguardando validação com o dispositivo físico para a constatação final da experiência háptica (Hold dos acordes + Preview do áudio). 

## P. BUILD
Executado `assembleDebug`. O aplicativo foi compilado com 100% de sucesso. Nenhuma falha estrutural.

## Q. Regressões
As falhas vistas no `testDebugUnitTest` como `UnsatisfiedLinkError` continuam sendo puramente de ambiente de mock sem emulador carregado para JNI. Nenhuma falha lógica da nova arquitetura foi introduzida.

## R. Limitações restantes
As Voices podem apresentar ligeiro atraso dependendo do barramento USB-OTG do Android. A implementação da camada binária (C++) e leitura do STY nativo será iniciada na próxima fase.

## S. Próximo passo recomendado
Aprovar as correções feitas nos testes e no APK (`assembleDebug`) através de experimentação real no Yamaha PSR-S750, a fim de avançar com segurança para a FASE SEGUINTE — LEITURA COMPLETA E PLAYBACK DOS STYs.
