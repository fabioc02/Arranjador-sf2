# AUDITORIA FORENSE — FASE C: ARRANGER, CASM E RECORDING

Esta auditoria foi conduzida puramente por análise estática de código e documentação oficial (Reference Manual / Data List do PSR-S750), sem realizar nenhuma alteração nos arquivos do projeto.

---

## A — LEFT + ARRANGER: CAUSA PROVÁVEL E FLUXO ATUAL
O fluxo atual de detecção funciona perfeitamente **quando o botão físico LEFT está desligado** porque o PSR transmite todas as teclas (Main) no **CH1 (índice 0)**. O `GlobalMidiRouter` possui a regra:
`channel == 0 && data1 <= splitPoint`
Isso captura a mão esquerda com sucesso.

Porém, **quando o botão físico LEFT está ligado**, a arquitetura do PSR muda o roteamento de transmissão USB. As notas abaixo do splitPoint deixam de ser transmitidas no CH1 e passam a ser transmitidas no canal dedicado à voz LEFT.

---

## B — LEFT FÍSICO ON/OFF (Diferenças de Canal)
De acordo com o Data List do PSR-S750 (pág. 72 - MIDI Settings / Transmit), o comportamento padrão de transmissão do teclado é:
* **RIGHT 1:** CH1 (índice 0)
* **RIGHT 2:** CH2 (índice 1)
* **LEFT:** CH3 (índice 2)

A falha ocorre porque o `GlobalMidiRouter` identifica a mão esquerda pela seguinte lógica restrita:
`channel == VoiceEngine.CH_LEFT || (channel == 0 && data1 <= MidiState.splitPoint)`

Como `VoiceEngine.CH_LEFT` foi definido para a comunicação de recepção (Local Control Off) como **3** (que representa CH4) no Voice Editor, o Android aguarda os eventos do LEFT no **CH4 (índice 3)**.
O PSR, entretanto, está transmitindo no **CH3 (índice 2)**. A nota MIDI entra, falha na checagem do `if (isLeftHand)` e é simplesmente ignorada pela engine do Arranger.

---

## C — ARRANGER: FLUXO COMPLETO
Quando a falha do canal mencionada em "B" ocorre, o fluxo é quebrado precocemente:

1. PSR transmite Nota G2 no CH3.
2. `GlobalMidiRouter` recebe (status `0x92`).
3. Verifica `isLeftHand` -> `(2 == 3)` -> **FALSE**.
4. `activeArrangerNotes` não é atualizado.
5. `detectChord()` nunca é chamado.
6. `MidiRhythmState.detectedChord` permanece inalterado.
7. As tracks continuam tocando no acorde anterior.

---

## D — GM/D E CASM: ROOT, BASS, NTR E NTT
A percepção de que a nota fundamental (D) no baixo do acorde GM/D soa "uma oitava mais grave" é **um comportamento matematicamente correto segundo as regras da Yamaha (High Key)**, porém mal configurado no código.

**Análise do fluxo:**
1. **Acorde:** GM/D (Root = G, Bass = D).
2. **NTR Bass:** Desloca a nota baseando-se no Target Bass (D) em relação ao Source Bass (C). Deslocamento = +2 semitons.
3. **Problema Real (High Key Rule):** O algoritmo da Yamaha no arquivo `ArrangerEngine.kt` utiliza um CASM dummy genérico:
   `val dummyCasm = CasmChannel(highKey = 6, ...)`
   O `HighKey = 6` representa a nota **F#**.
4. Quando o acorde muda para **GM/D** (cujo Root principal é G, valor 7), a regra do CASM diz: se a nota/root alvo for maior que o HighKey (7 > 6), aplique uma transposição de **-12 semitons** (uma oitava abaixo) para evitar que o acompanhamento fique agudo demais (Tone Generation Limit).
5. Como 7 (G) ultrapassa 6 (F#), a regra derruba a oitava do Baixo.

A Yamaha documenta que canais de baixo costumam ter o High Key diferente dos canais Chord/Pad. No nosso código atual, esse valor está hardcoded (6) para todas as tracks.

---

## E — METRÔNOMO: TIMBRE ERRADO APÓS O PLAY
O bug do CLICK deve-se ao momento exato em que o comando `Program Change` (PC 115 - Woodblock) é disparado.

Analisando `MidiRhythmCreator.kt` (linhas 256-258):
O envio do `MidiState.sendProgramChange(15, 0, 0, 115)` só existe dentro do bloco de inicialização do Scheduler (`if (MidiRhythmState.isPlaying)` no momento de montagem do `DisposableEffect`).

Se o usuário clica em PLAY com o CLICK desligado, o PC não é enviado. Se no meio do loop (durante a execução) o usuário liga o CLICK, a variável `metronomeEnabled` vira `true` e começa a disparar notas no CH16. Contudo, como o PC 115 nunca foi enviado, o CH16 do PSR-S750 reproduz o instrumento padrão (Grand Piano).

---

## F — SOURCE CHORD / RECORDING CHORD
Consultando o **Reference Manual e Data List do PSR-S750**:
O conceito de **Source Root** e **Source Chord** é real, intrínseco e documentado no formato CASM (Seção Sdec).
O manual estipula que, durante a gravação no Style Creator (Assembly/Step Edit), o equipamento pressupõe que o usuário está gravando em C Major7 ou C Major (padrão), **mas permite** que o usuário defina especificamente a nota e o acorde sobre o qual tocou a frase.

---

## G — DIFERENTES ACORDES POR TRACK
A Yamaha documenta que os parâmetros de Source Chord/Root são gravados e processados **por canal (Track)** dentro da sub-sessão de CASM.
Sim, é totalmente permitido e comum que:
* `BASS` tenha Source Root C e Source Chord Major.
* `PHRASE 1` tenha Source Root A e Source Chord Minor.

Atualmente, `ArrangerEngine.kt` contém a limitação hardcoded:
`val domainSource = com.example.domain.models.Chord(0, ChordType.Major7)`
Restringir o aplicativo a apenas um acorde de gravação global é artificial e quebra a compatibilidade com a modelagem CASM.

---

## H — UI: PROPOSTA DE TRANSPORTE COMPACTO
Em vez de desperdiçar espaço com múltiplos botões grandes de transporte, a barra superior deve ser aglutinada num formato de "toolbar" densa e de ação unificada:

```text
┌────────────────────────────────────────────────────────┐
│ [▶/⏸] [■] [🔴 REC] │ ⏱ 120 │ 4/4 │ 2 Meas │ Q: 1/16 │
│ [ARRANGER: ON]  [CLICK: OFF]  [EXT MIDI]               │
└────────────────────────────────────────────────────────┘
```
**Comportamento do [▶/⏸]:**
* Estado inicial: Ícone de Play.
* Clicou (toca): Ícone muda para Pause.
* Clicou novamente (pausa): Ícone volta para Play.
* O botão `STOP [■]` sempre zera tudo e devolve o botão principal para Play.

---

## I — TRACKS: PROPOSTA SEM SCROLL HORIZONTAL
O scroll introduziu fricção (o usuário perde visão geral da mixagem). A reestruturação ideal usa um "Grid" apertado por Track, alinhado:

```text
Track: Bass          [ Instr▼ ]    Vol: [====O----] 90
Out: [ CH Original▼] [ MUTE ]      [ Q ] [ Cpy ] [ Del ]
```
O botão "Out" precisa permitir alternância cíclica (`L -> R1 -> R2 -> CH Org`) usando cores visuais em vez de texto longo, e a track inteira deve caber em no máximo 2 linhas verticais sem scroll.

---

## J — PERFORMANCE: RISCOS NO SAMSUNG J5 (2GB RAM)
**Risco Crítico Identificado:**
O `activeArrangerNotes` é um `mutableStateListOf`. Todo `NoteOn` ou `NoteOff` modifica essa lista.
Como o Jetpack Compose monitora alterações de estado para atualizar a UI do `ChordEducationalPanel`, tocar acordes rápidos com a mão esquerda aciona a recomposição do painel dezenas de vezes por segundo.
No J5, que possui um Garbage Collector agressivo (Dalvik/ART com pouca RAM), isso certamente causará saltos de quadros (micro-stutters) na UI e potencialmente atrasos de milissegundos no Scheduler de áudio que roda concorrentemente.

---

## K — ARQUIVOS A SEREM MODIFICADOS
Quando for autorizada a correção, os alvos serão:
1. `GlobalMidiRouter.kt` (Corrigir índice do LEFT físico para index 2).
2. `ArrangerEngine.kt` (Remover HighKey 6 estático, remover Source Chord fixo de CMaj7).
3. `MidiRhythmCreator.kt` (Refazer Toolbar de transporte unificado, resolver layout da Track, disparar PC 115 do metrônomo no clique em vez de no inicializador).
4. `MidiRhythmState.kt` (Incluir dicionário de Source Chords por Track).
5. `VoiceEngine.kt` ou `MidiState.kt` (Para eventual sincronização de PC).

---

## L — CLASSIFICAÇÃO DE PRIORIDADES
* **P0 (Impede Funcionamento):** LEFT Físico ON/OFF não sendo lido (Bug do CH3 vs CH4).
* **P0 (Impede Funcionamento):** Metrônomo chamando Grand Piano se ligado pós-Play.
* **P1 (Funcionalidade Incorreta):** HighKey forçando graves extremos em GM/D.
* **P1 (Funcionalidade Incorreta):** Ausência de Source Chord individualizado por track.
* **P2 (Usabilidade/UI):** Transporte excessivamente grande, Scroll horizontal nas Tracks.
* **P3 (Otimização):** Isolamento de estado (`activeArrangerNotes`) para mitigar stutters no Samsung J5.
