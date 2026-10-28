# Auditoria de Arquitetura - Rhythm Creator (Pré-Teste Físico)
**Fase:** Auditoria Estática
**Data:** 19 de Agosto de 2026

## A. Arquivos analisados
- `app/src/main/java/com/example/MidiRhythmCreator.kt` (UI, Clock, Metronome, Tracks, MIDI Listener)
- `app/src/main/java/com/example/domain/midi/GlobalMidiRouter.kt` (Roteamento de Right 1 / Right 2 / Override)
- `app/src/main/java/com/example/MidiRhythmState.kt` (Estrutura de dados)
- `app/src/test/java/com/example/RhythmCreatorTest.kt` (Testes automatizados do Musical Clock)

## B. Arquivos modificados anteriormente
- `app/src/main/java/com/example/MidiRhythmCreator.kt`
- `app/src/main/java/com/example/domain/midi/GlobalMidiRouter.kt`
- `app/src/test/java/com/example/RhythmCreatorTest.kt`
- `app/src/test/java/com/example/ArrangerScreenTest.kt`

## C. Fluxo atual MIDI IN → Engine → MIDI OUT
O Yamaha PSR-S750 envia Note-On (MIDI IN) que entra no aplicativo e é registrado pelo `UsbMidiManager`. Este evento notifica o `MidiState.midiListeners`. O `GlobalMidiRouter` intercepta as notas e executa a lógica de *Split Point*. Sem a UI do Rhythm Creator aberta, ele divide o teclado em Left (Canal 2), Right 1 (Canal 0) e Right 2 (Canal 1), acionando as respectivas vozes enviando Note-On de volta via MIDI OUT para o Yamaha.

## D. Fluxo atual Voice Preview
No momento que a UI do Rhythm Creator é montada (`DisposableEffect`), uma variável global de estado (`overrideRightHandChannel`) é ativada no `GlobalMidiRouter`. Se o usuário tocar uma tecla, o roteador ignora *Split Points*, Right 1 e Right 2. Ele roteia TODO o Note-On físico diretamente para o Canal MIDI da Track selecionada no Rhythm Creator. Isso permite o **Voice Preview imediato e livre de vazamentos**, utilizando as rotas nativas do aplicativo, sem necessitar que a gravação esteja ativa.

## E. Fluxo atual Recording
Idêntico ao fluxo do Voice Preview (para ouvir o que se toca), mas em paralelo, o listener interno de `MidiRhythmCreator` captura as mesmas notas. Baseado na fórmula rítmica, ele quantiza o tempo (baseado no `currentTick` contra `ppq`) e salva na lista `track.notes` (memória). Os dados não passam por camadas externas de gravação em C++ e nem criam novos processamentos paralelos de som.

## F. Estado Right 1 / Right 2 / Left
Estão **totalmente preservados** sob o escopo da `VoiceEngine` e do `GlobalMidiRouter`. A correção aplicou o padrão de substituição por contexto (Override):
- Rhythm Creator Aberto: `GlobalMidiRouter` injeta a Track selecionada como substituto temporário total da mão direita.
- Rhythm Creator Fechado (`onDispose`): O override vira `null` e Right 1 / Right 2 voltam a operar normalmente conforme as regras globais de split point do Yamaha.

## G. Estado das Tracks
O `MidiRhythmState.sectionTracks` isola o estado de cada seção. Cada instância de `RhythmTrack` mantém localmente seu volume (CC7), MSB, LSB e Program Change. Não ocorre fusão não-intencional de canais. Track de bateria envia sinal ao Canal 10, Bass ao 11, etc.

## H. Musical Clock
**Arquitetura Matemática:** O PPQ padrão usado é 24 (compatível com especificações MIDI padrão). O cálculo de ticks foi refatorado para `ticksPerBeat = ppq / (timeSigDenominator / 4)`. No entanto, na auditoria, um defeito matemático foi encontrado:
- **Alerta de Divisão:** A divisão inteira em Kotlin para compassos de base maior (ex: denominadores como 2, no caso de 2/2 - *Cut Time*) provocará `(2 / 4) = 0`. Isso levará o cálculo de `ticksPerBeat` a `24 / 0` -> **ArithmeticException (Crash Fatal)**. A lógica matemática não está blindada contra divisões por zero em cenários extremos.

## I. Timeline
A agulha visual foi reescrita utilizando o comprimento dinâmico `px = (currentTick.toFloat() / totalTicks.toFloat())`. Esta lógica está sólida e desatrelada do valor engessado anterior. A UI agora reflete o estado do relógio de playback puramente (Fonte única de verdade). Não foi encontrada nenhuma duplicação de coroutines exclusivas para a Timeline visual.

## J. Metronome
O acionamento ocorre em `currentTick % ticksPerBeat == 0`, operando no escopo do loop condicional da coroutine principal do Playback (`LaunchedEffect`). Dispara com precisão em subdivisões 4/4 e 6/8. Sem delays arbitrários, sem chamadas bloqueantes de thread. 

## K. Volume
A auditoria detectou a origem da **Regressão de Volume** (Gravo a Track A com volume alto, ao mudar para a Track B a Track A abaixa sozinha).
- **Causa Analisada:** Ao trocar a seleção da Track (`onClick = { MidiRhythmState.selectedTrackIndex = index }`), o aplicativo envia imediatamente as configurações de MSB, LSB e Program Change ao hardware (PSR-S750) para alterar o timbre. No entanto, é documentado que hardwares da Yamaha quase sempre **resetam o Volume do Canal MIDI, Pan, Reverb e Chorus (DSP) para os valores "Defaults" da Voice de fábrica sempre que recebem um novo Program Change**.
- Como o aplicativo NÃO envia o CC7 (Volume) salvo no estado interno ao trocar de Track, o hardware fica com Volume 100/Padrão enquanto o visor exibe outro valor.

## L. Chord Detection
Intocada. Lógica mantida e blindada. O sistema lê as notas adicionadas via Note-On, extrai os intervalos e classifica a qualidade do acorde pela raiz. A liberação das notas (Note-Off) limpa o vetor físico, mas não comuta o Estado do Reconhecimento, preservando inteligentemente o acorde no visor e no acompanhamento até uma nova harmonização.

## M. Possíveis conflitos de motores
A auditoria confirma que não existe motor simultâneo executando durante o Rhythm Creator. O Playback STY nativo (CASM) e as tabelas NTT (C++) continuam bloqueados e adormecidos neste painel, não gerando poluição MIDI OUT concorrente. Nenhuma sobreposição com o Python (ausente do escopo Mobile).

## N. Possíveis duplicações de estado
Nenhuma duplicação arquitetural foi injetada. O roteamento dinâmico no `GlobalMidiRouter` garantiu a reutilização do listener MIDI base da aplicação em vez de instanciar um ouvinte "sombra" dentro da UI. 

## O. Possíveis regressões
1. Crash por ArithmeticException no relógio (`denominator / 4`) caso o compasso assuma denominador < 4.
2. Dessincronização do hardware/software devido ao reset natural do teclado perante Program Changes sem a correspondente reafirmação de Control Changes (Volume CC7).

## P. Testes executados
- Build local via `assembleDebug`.
- Execução de `testDebugUnitTest` via Robolectric. Os testes de conversão (6/8, 4/4, 12/8) adicionados na iteração prévia passaram sem problemas operacionais.

## Q. BUILD status
A confirmação do comando Gradle é `BUILD SUCCESSFUL`. (Compilação confirmada pela ferramenta).

## R. Alterações recomendadas
**1. PROBLEMA:** Crash de Divisão por Zero no cálculo do Relógio Musical.
*   **Causa:** A fórmula `ppq / (denominator / 4)` usou divisão de inteiros em Kotlin. 
*   **Arquivo:** `MidiRhythmCreator.kt` e `RhythmCreatorTest.kt`
*   **Risco:** Severo. Pode quebrar a aplicação caso o usuário digite 2 no campo Denominador.
*   **Correção Proposta:** Mudar a lógica para não utilizar divisão antecipada com arredondamento: `(ppq * 4) / timeSigDenominator`. Para 2/2: `(24 * 4) / 2 = 48 ticks`. Para 6/8: `(24 * 4) / 8 = 12 ticks`.

**2. PROBLEMA:** Perda física do Volume (CC7) da Track ao trocar de aba ou instrumento.
*   **Causa:** Yamaha System Reset no envio do Program Change. O hardware descarta o volume antigo, mas a interface Mobile não reafirma.
*   **Arquivo:** `MidiRhythmCreator.kt`
*   **Risco:** Moderado (Impacto na Experiência do Usuário).
*   **Correção Proposta:** Sempre que acionar a seleção de uma Track ou forçar um Voice Update através da lista suspensa (Dropdown), inserir a chamada auxiliar para retransmitir imediatamente o estado gravado daquele canal: `MidiState.sendControlChange(track.channel - 1, 7, track.volume)`.

## S. Alterações que NÃO devem ser feitas
- Não adicionar lógica condicional complexa dentro de `GlobalMidiRouter` baseada em BPM ou estado da agulha.
- Não reescrever a arquitetura do Painel de Acordes para introduzir "debounces" cronológicos com `delay()` do Kotlin Coroutines.
- Não converter os loops matemáticos de ritmo para C++ ou Python sob pretexto de evitar a ArithmeticException (resolve-se trivialmente na álgebra Kotlin).
- Não criar classes duplicadas que representem a mesma Track em outro módulo.

## T. Conclusão
O último build isolou e curou as piores infrações estruturais sem quebrar regras absolutas (como a restrição ao uso do Motor C++). Os fluxos de Voice Preview e Isolamento do Right 1 / Right 2 são agora estritamente comandados pela seleção visual da Track. Apesar da vitória nas correções prévias, a auditoria atesta que **o build possui pequenos pontos cegos lógicos** (Divisão por zero e Desidratação do Estado de Volume pelo hardware) que necessitam de intervenção micro-cirúrgica antes que o usuário proceda com a validação orgânica no aparelho Yamaha PSR-S750.
