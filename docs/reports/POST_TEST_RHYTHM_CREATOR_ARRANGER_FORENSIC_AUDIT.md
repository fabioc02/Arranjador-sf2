# Auditoria Forense Pós-Teste: Rhythm Creator / Arranger / MIDI

## 1. Estado atual
A base de código atual consolida a arquitetura da Fase C. A injeção do laço C++ `AudioEngine` existe e está mapeada. O motor de MIDI principal é baseado em eventos direcionados ao `MidiState`. A gravação no `MidiRhythmCreator` funciona nativamente através do `RealTimeMidiScheduler`. A UI está usando o tema claro e exibe os novos controles (Arranger, Ext Midi), embora os mesmos sofram de problemas lógicos de encadeamento e renderização, conforme detalhado abaixo.

## 2. Problemas confirmados
* **Loop MIDI Crítico e Vazamento de Ports (P0)**: Causando engasgos e tela preta.
* **Sobrescrita Visual (Layout - P1)**: O botão "Out: CH/L/R1/R2" sumiu por estar renderizado no mesmo espaço (eixo Z) que outro botão, devido a uma falha de layout `Box`.
* **Transporte Incompleto (P1)**: Ausência de estado para *Pause*.
* **Corte de Fluxo do Arranger (P1)**: O detector de acordes parou de receber o sinal MIDI de input.
* **Gargalo de Lista (P1 Performance)**: Mutação pesada `O(N)` ao gravar notas novas.

## 3. Problemas prováveis
* Suspeita-se que o J5 acione um encerramento forçado da UI (gerando a tela preta) pela alta carga de exceções não-fatais disparadas pela thread MIDI do sistema tentando gravar em um port USB desconectado. 

## 4. P0 — Tela escura / possível travamento
**ARQUIVO**: `MainActivity.kt` & `GlobalMidiRouter.kt`
**FLUXO**: Desconexão USB-OTG / Recepção de Notas
**EVIDÊNCIA & CAUSA**:
1. **Dangling Ports**: O método `midiManager?.registerDeviceCallback` monitora `onDeviceAdded`, mas **não sobrescreve o `onDeviceRemoved`**. Se o cabo OTG for retirado, o `MidiState.midiInputPorts` manterá a referência morta. Cada vez que o playback ou gravação emite um evento (que são dezenas por segundo), o `port.send()` é chamado sobre a porta morta e captura uma `IOException`. O lixo computacional pode congelar a UI.
2. **Infinite MIDI Loop**: A função `GlobalMidiRouter` intercepta um `NoteOn` do PSR-S750 e faz um re-encaminhamento usando `MidiState.sendNoteOn(recCh, ...)`. Se o "Local Control / MIDI Out" do PSR estiver transmitindo via USB as notas que ele mesmo recebe (fazendo um eco), o teclado envia a nota de volta pro Android, que processa a nota novamente e reenvia ao teclado em loop infinito e síncrono, sobrecarregando instantaneamente a Binder Thread e travando o compose (tela escura). Quando o cabo OTG é puxado, o loop é quebrado e o App ressuscita.

## 5. Play/Pause/Stop
**ARQUIVO**: `MidiRhythmCreator.kt`
**FLUXO ATUAL**: Só há um botão que inverte o estado booleano `MidiRhythmState.isPlaying = !MidiRhythmState.isPlaying`. 
**O QUE FOI PERDIDO**: Quando `isPlaying` passa a falso, o código possui a instrução imperativa `MidiRhythmState.currentTick = 0`, ou seja, atua apenas como um "STOP" radical.
**AÇÃO NECESSÁRIA**: Criar estados separados: `isPaused` (para a thread mas preserva o `currentTick`) e `isStopped` (reseta `currentTick = 0`). A UI deve expor três botões distintos (Play, Pause, Stop), separados do REC.

## 6. Arranger
**FLUXO ATUAL**:
UI (Botão "Arranger") → `MidiState.isArrangerMode = true` → (Durante o Playback) transposição usando `MidiRhythmState.detectedChord`.
**ONDE QUEBROU**: O pipeline de detecção está cortado. O `GlobalMidiRouter` não invoca `AudioEngine.onLiveMidiEvent`, bloqueando as notas de entrarem na engine C++. Ao mesmo tempo, um patch anterior removeu a lógica de detecção de acorde em Kotlin que existia diretamente no listener do `MidiRhythmCreator`. Como não há avaliação das teclas físicas pressionadas, o acorde nunca muda, invalidando o Arranger dinâmico.

## 7. L/R routing
**ARQUIVO**: `MidiRhythmCreator.kt` (linhas 456-474)
**O QUE DESAPARECEU**: Visualmente na interface. 
**ONDE ESTÁ**: O código da lógica iterativa cíclica ("Out: L", "Out: R1", "Out: R2") está intacto na UI, MAS foi colocado no mesmo escopo `Box { ... }` que o botão de carregar timbres (`Instr`). No Compose, sem modificadores específicos em um `Box`, os componentes desenham um diretamente sobre o outro. O botão "Instr" está literalmente ocultando o "Out: L/R" debaixo dele.

## 8. Layout
**ONDE ESTÁ O ESPAÇO CONSUMIDO**: A barra de ferramentas consiste em múltiplos `Row`s verticalmente empilhados (Configurações da seção, Medidas/Quantização, BPM, Controles de Transporte, Botões MIDI/Arranger). Em uma tela compacta (J5), esses elementos esmagam a `LazyColumn` inferior onde residem as 8 tracks, resultando na visibilidade de quase zero das tracks.
**SOLUÇÃO**: Consolidação e uso de menus "Dropdown" ou componentes em um `FlowRow` em barra única para desocupar o eixo Y (Vertical) e maximizar as Tracks.

## 9. Metrônomo
**ARQUIVO**: `MidiRhythmCreator.kt`
**CONFIRMAÇÃO**: Realocação confirmada. O disparo é invocado através de `MidiState.sendNoteOn(15, ...)` (Channel 16 - zero-based), enviando a nota GM 76 (Woodblock), seguido por envio de `Program Change 115` no mesmo canal CH16 ao inicializar o playback. Não há qualquer chamada residual usando o CH10 (index 9) para metrônomo no código atual.

## 10. Performance
**RISCO ESPECÍFICO PARA SAMSUNG J5**:
No processamento da gravação, em `MidiRhythmCreator.kt`, o código usa:
`track.notes = track.notes + note`
Isso cria uma **cópia completa inteira** (`O(N)`) da lista da track (que pode conter milhares de notas) na recepção de *cada* MIDI event, dentro da thread Binder. Isso instiga chamadas absurdas ao Garbage Collector e causa severos stutters no J5 (baixo poder de processamento de heap) e latência audível/visual. Deve-se adotar `SnapshotStateList<RecordedNote>` e adicionar `.add()` mutável que é `O(1)`.

## 11. Comparação com o legado
* **Transporte**: O legado usava a separação de ícones no `TopAppBar` para Play, Pause, e Stop, com lógica independente no scheduler.
* **MIDI Threading**: O legado tratava o ciclo de vida do dispositivo MIDI removendo ports na detecção do disconnect (impedindo exceptions em ghost-ports).

## 12. Plano de implementação
**FASE AUTORIZADA PÓS-RELATÓRIO**
1. **P0 — Estabilidade MIDI**: Implementar callback `onDeviceRemoved` no `MainActivity` limpando os ports. Adicionar filtro anti-loop no `GlobalMidiRouter` para não retransmitir notas via Out-to-In se não for estritamente em estado de Echo intencional seguro.
2. **P1 — Performance**: Modificar `track.notes` no `RhythmTrack` para `SnapshotStateList` prevenindo cópia `O(N)` na gravação (salvando o J5).
3. **P1 — Transporte Play/Pause/Stop**: Quebrar a booleana única e implementar os 3 botões / fluxos lógicos dedicados no `MidiRhythmCreator`.
4. **P1 — L/R Routing (Correção Compose)**: Trocar o `Box` dos botões da track por `Row` ou espaçamento adequado para revelar o botão oculto.
5. **P1 — Layout Espacial**: Agrupar os botões do topo do `MidiRhythmCreator` para compactá-los, liberando > 60% da tela para as tracks.
6. **P1 — Arranger Pipeline**: Ligar `AudioEngine.onLiveMidiEvent` no `GlobalMidiRouter` ou reabilitar a chamada `ArrangerEngine.detectChord` (no callback passivo do gravador) para o `MidiRhythmState.detectedChord` voltar a atualizar durante a execução.
