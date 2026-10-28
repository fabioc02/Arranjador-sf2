# ARRANGER ANDROID — AUDITORIA FORENSE DE SCHEDULING (P0)

**Objetivo**: Analisar com precisão matemática e arquitetural a engine de tempo (Musical Clock) e o MIDI Scheduler, responsáveis pelos atrasos, jitter e notas emboladas durante o playback e acompanhamento.
**Nenhuma alteração de código foi realizada nesta fase.**

---

## 1. O RELÓGIO MUSICAL (PPQ, BPM E TIME SIGNATURE)
**[CONFIRMED MATHEMATICALLY]**

Existem dois relógios independentes no sistema, ambos com a matemática base correta:
1. **StyleClock (CASM/Styles)**: Usa `PPQN = 1920` (ou o valor nativo do `.sty`).
2. **MidiRhythmCreator (Drum Sequencer)**: Usa `PPQ = 24`.

A relação matemática encontrada no código para calcular os tempos é estritamente correta:
- **Ticks per Beat**: `(ppq * 4) / denominator`.
- **Duração do Tick em Tempo Real (Microsegundos)**: `60_000_000.0 / (BPM * PPQ)`.
- **BPM Absoluto**: O código mantém o BPM como Quarter Notes (Semínimas). Se o compasso for 6/8, o "beat" musical cai para uma colcheia (1/8), resultando em metade dos ticks por batida, mas a velocidade real do tick permanece inalterada em relação ao relógio. Isso está musicalmente e matematicamente correto.

**Valores Reais em Ticks (Para PPQ = 24)**:
- 1/4 (Semínima): 24 ticks
- 1/8 (Colcheia): 12 ticks
- 1/8 Triplet: 8 ticks
- 1/16 (Semicolcheia): 6 ticks
- 1/16 Triplet: 4 ticks
- 1/32 (Fusa): 3 ticks

**Representação de Compassos (totalTicks per Measure a PPQ=24)**:
- 4/4 = 96 ticks
- 3/4 = 72 ticks
- 6/8 = 72 ticks
- 3/2 = 144 ticks

**A quantização (Grid Visual vs Tempo Musical)**:
O código de quantização calcula corretamente os intervalos. Ex: se resolução = 16 (1/16), ele trava a nota em múltiplos de 6 ticks.

---

## 2. A CAUSA DO JITTER: SCHEDULING DE GAVETA (DELAY LOOP)
**[CONFIRMED BY CODE]**

Apesar da matemática do relógio ser perfeita, a *execução temporal* no Android quebra totalmente.

**Como funciona agora:**
1. No `ValidationPlayerViewModel`, o playback roda numa Coroutine (`Dispatchers.Default`).
2. Ele calcula que cada tick deve durar, por exemplo, 260 microssegundos (0.26ms).
3. Se não for a hora do próximo tick, ele chama `delay(1)`.
4. No `MidiRhythmCreator`, o loop roda em um `LaunchedEffect` (UI Thread do Compose) e chama `delay(sleepTime)`.

**Por que isso destrói o MIDI:**
- No Android/JVM, `delay()` devolve a Thread para o escalonador do SO. **Não existe garantia de 1ms**. Frequentemente, o `delay(1)` dorme por 5 a 15 milissegundos devido à Coleta de Lixo (GC) ou sobrecarga da UI.
- Quando a Thread finalmente "acorda" (ex: 12ms depois), o acumulador de tempo percebe que dezenas de ticks estão atrasados.
- O loop então processa todos esses ticks em milissegundos.
- **O Golpe Final**: A função `MidiState.sendNoteOn` carimba o evento com `System.nanoTime()` no exato momento em que é chamada. Como a engine "cospe" 15 notas atrasadas numa fração de segundo, todas recebem o mesmo timestamp. 
- O PSR-S750 recebe um bloco embolado de notas (Jitter extremo) em vez de notas distribuídas musicalmente no tempo.

---

## 3. ARQUITETURA DE ROTEAMENTO (O C++ FANTASMA E CÓDIGO MORTO)
**[CONFIRMED BY CODE]**

Rastreamos todos os caminhos do MIDI IN (Mão do Tecladista) até o destino:

1. **PSR-S750 -> MidiReceiver -> MidiState.midiListeners** (Única ponte real existente).
2. **GlobalMidiRouter**: Ouve o listener para fazer `Local Control Off` e ecoar a mão direita durante a gravação.
3. **ValidationPlayerViewModel**: Ouve o listener para detectar os acordes (usando `ChordEngine` em Kotlin pura) e transpor o *Style* localmente.
4. **MidiRhythmCreator**: Ouve o listener apenas para capturar notas durante a gravação.

**O Cemitério de Arquiteturas:**
- **A Rota C++**: A função JNI `AudioEngine.onLiveMidiEvent` **nunca é chamada** em todo o projeto Kotlin. O motor C++ roda, mas é surdo. Como não recebe notas, nunca envia acordes de volta, matando o Arranger Mode do `MidiRhythmCreator`.
- **A Rota PerformanceEngine**: Os arquivos `MidiEngine`, `MidiRouter` e `PerformanceEngine` (na pasta `domain/midi`) representam uma arquitetura limpa que foi iniciada mas **nunca instanciada**. São "código morto" puro.

---

## 4. CONCLUSÃO E PROPOSTA DE ARQUITETURA PARA P0

Os conceitos de Tempo Musical (Matemática) e Grid Visual (Quantização) estão surpreendentemente maduros no código. O problema central é a ausência de um **Real-Time MIDI Scheduler**.

**A solução definitiva não é trocar delay(), mas sim isolar o Clock:**
1. **Remover o Loop da UI**: Retirar `LaunchedEffect` e `viewModelScope.launch`.
2. **Implementar um Thread Loop Dedicado ou Handler**: Usar um `Thread(Runnable)` isolado com prioridade máxima (ou `HandlerThread`) que use bloqueio de alta precisão (ex: `LockSupport.parkNanos`) ao invés de `delay()` das coroutines.
3. **Agendamento no Futuro (Timestamping)**: O *StylePlaybackEngine* não deve enviar notas usando o tempo real atual (`System.nanoTime()`). Ele deve calcular o tempo no futuro que a nota *deve* soar, e enviar ao `MidiInputPort` com esse timestamp futuro. A própria API `android.media.midi` cuida do *scheduling* preciso se fornecermos o timestamp exato do evento, mitigando qualquer jitter gerado pelo Android OS.

Aguardo a aprovação para iniciar a implementação focada e cirúrgica do P0.

## 5. UPDATE - P0 IMPLEMENTATION
The P0 implementation has been completed. The `delay()` mechanisms were completely removed from the critical MIDI path in both `MidiRhythmCreator.kt` and `ValidationPlayerViewModel.kt`. We created a generic, reusable, isolated Thread-based `RealTimeMidiScheduler` class using `LockSupport.parkNanos` for high precision waiting, fully decoupling the musical clock from Android's UI threading/coroutines. 

For full technical details, please refer to `docs/reports/P0_MIDI_SCHEDULER_IMPLEMENTATION_REPORT.md`.
