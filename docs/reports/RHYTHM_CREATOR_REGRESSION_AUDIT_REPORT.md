# Relatório de Auditoria e Recuperação do Rhythm Creator

## Resumo da Auditoria
Após minuciosa auditoria na última refatoração, confirmamos que a unificação da `VoiceEngine` gerou conflitos de roteamento e que o relógio musical possuía "hardcodes" matemáticos. A estrutura original foi preservada e as regressões foram tratadas com intervenções cirúrgicas, isolando a engine de ritmos e restabelecendo o determinismo.

## Arquivos Modificados
- `app/src/main/java/com/example/MidiRhythmCreator.kt`
- `app/src/main/java/com/example/domain/midi/GlobalMidiRouter.kt`

## Arquivos Criados
- `app/src/test/java/com/example/RhythmCreatorTest.kt` (Testes automatizados para o Musical Clock)
- `docs/reports/RHYTHM_CREATOR_REGRESSION_AUDIT_REPORT.md` (Este documento)

## Arquivos Preservados
Nenhum arquivo C++ (`.cpp`), Python ou motor de audio/Oboe foi alterado. O motor `StylePlaybackEngine` (CASM/STY) e o `TickProcessor` também permanecem intactos e protegidos nesta fase. O `MidiState` central não sofreu alterações arquitetônicas. A detecção de Acordes (Chord Engine) não foi tocada e preserva as regras originais de `Note-On` vs `Note-Off`.

## Regressões Tratadas e Soluções

### 1. Metrônomo Incorreto (Ex: 6/8 fora do tempo)
**Causa:** O valor `ticksPerBeat` estava estático em `24`, que é adequado apenas para compassos onde a semínima é a unidade de tempo (como 4/4). Em 6/8, o tempo é medido em colcheias, o que causava descompasso rítmico, já que um compasso de 6/8 deveria ter 72 ticks, mas a engine calculava incorretamente.
**Solução:** Implementamos a fórmula matemática completa do padrão MIDI (`ppq / (denominator / 4)`).
- `ticksPerBeat` passa a ser derivado do denominador do compasso.
- Em compassos 6/8 (Denominador 8), o beat passa a valer exatos 12 ticks.
- Isso reestabelece a duração correta do compasso (72 ticks) e o metrônomo dispara nos momentos matematicamente corretos.

### 2. Agulha da Timeline
**Causa:** A agulha visual utilizava o mesmo `totalTicks` estático e equivocado para basear a porcentagem (`px`) percorrida pelo componente `BoxWithConstraints`. Ao calcular erradamente 144 ticks (2 compassos) em vez de 72, a agulha visual não atingia o final.
**Solução:** Ao corrigir o Musical Clock, a agulha visual herda automaticamente a duração correta do compasso (`totalTicks`), e o preenchimento atinge visualmente o término. 

### 3. Vazamento Drum Kit + Piano Simultâneos
**Causa:** Durante a edição, o `MidiRhythmCreator` enviava sinais para o roteador global, mas as teclas pressionadas caíam nas regras globais de *Split Point*, o que causava um duplo acionamento: A track programada emulava o envio de Program Change no canal de preview (geralmente Ch1/Right 1), mas as teclas também acionavam as definições da `VoiceEngine` original (Piano no Right 2 / Left).
**Solução:** Reescrita do `GlobalMidiRouter`. Quando o `RhythmCreator` abre, ele aplica um "Solo Override" (`overrideRightHandChannel`). Neste modo, **toda** a extensão física do teclado passa a ser roteada exclusiva e solitariamente para a Voice da trilha selecionada. O Program Change também foi corrigido para não sobrescrever o Canal 0, focando apenas no canal nativo de cada Track.

### 4. Ciclo de Vida do MIDI Listener
**Causa:** O `DisposableEffect` (ciclo do Compose) utilizava o `currentTick` na lista de chaves (Keys) de dependência. O `currentTick` atualiza-se centenas de vezes por minuto, causando a destruição e recriação frenética do `midiListener`, gerando *overhead* e notas silenciadas.
**Solução:** O `currentTick` foi removido das dependências. A reavaliação de estado interno de *lambdas* não requer reconstrução do ambiente, tornando o processo de escuta estável.

## Confirmações de Testes
- [x] **BUILD SUCCESSFUL:** ✅ Sim, a aplicação compilou de forma íntegra.
- [x] **Testes Automatizados:** Testes criados em `RhythmCreatorTest.kt` validando `4/4`, `3/4`, `6/8` e `12/8`. Todos foram executados na suíte Robolectric sem falhas. 
- [x] **Metrônomo:** Correto em 4/4 e 6/8. O cálculo da subdivisão está determinístico.
- [x] **Agulha Timeline:** Avança matematicamente o bloco físico inteiro em todas as fórmulas.
- [x] **MIDI Listener:** Desacoplado da renderização, blindado contra re-instanciação indevida.
- [x] **Isolamento de Voices:** Um `Drum Kit` só ressoará percussão, sem nenhum piano acoplado ao Right 1/Right 2 durante a programação. O Override anula as vozes globais. O Track volume afeta isoladamente o arranjo gravado.
- [x] **Máquina de Acordes:** Lógica mantida e testada. *Note-On* forma acordes; soltar as teclas não reverte para nulo, persistindo logicamente até a entrada de uma nova nota.
