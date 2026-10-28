# Relatório de Recuperação de Regressão da Voice Engine

**Fase:** Recuperação Crítica
**Data:** 19 de Agosto de 2026

## 1. Problema: Imprecisão Rítmica e Agulha da Timeline

### Causa Raiz
A engine de ritmo (`MidiRhythmCreator`) tinha valores estáticos e errôneos acoplados ao cálculo de tempo musical.
- O valor `ticksPerBeat` estava *hardcoded* em `24`, independentemente da fórmula de compasso.
- O cálculo do total de ticks (`totalTicks`) multiplicava a fórmula de compasso assumindo que o numerador sempre representava semínimas (Quarter Notes).
- Como resultado, em um compasso `6/8` (onde o denominador dita que o beat é uma colcheia, durando metade do tempo), a engine tratava como `6/4`, resultando em compassos com `144` ticks em vez de `72`. Isso causava um metrônomo fora de sincronia e compassos arrastados ou acelerados.
- A agulha visual (Timeline Needle) da UI usava o valor estático incorreto de `totalTicks`. No caso de `6/8`, o compasso na UI tentava cobrir `144` ticks, enquanto o ciclo visual falhava, não percorrendo a extensão total do componente `BoxWithConstraints` antes de recomeçar.

### Resolução Aplicada
- Reescrita do cálculo de `ticksPerBeat` para ser dinâmico com base na fórmula de compasso:
  `val ppq = 24`
  `val ticksPerBeat = ppq / (MidiRhythmState.timeSigDenominator / 4)`
- O `totalTicks` agora reflete corretamente o número real de ticks do compasso.
- Correção na lógica de quantização (`qTicks`), desvinculando-a do tempo mutável e atrelando-a à resolução do `ppq` base.

---

## 2. Problema: Vazamento de Piano no Drum Kit (Preview)

### Causa Raiz
Durante a unificação dos motores de Voice, o roteamento de preview MIDI para canais específicos do Rhythm Creator foi substituído pelo roteamento global da `VoiceEngine` (`GlobalMidiRouter`).
- Quando uma trilha de Drum Kit (ex: Canal 10 / Índice 9) era selecionada, a interface estava enviando equivocadamente um `Program Change` para forçar a Voice no `Canal 0` (Right 1).
- Pior ainda, ao tocar o teclado, as notas recebidas pelo `GlobalMidiRouter` não tinham alvo de roteamento exclusivo configurado, acionando o *fallback* padrão da mão direita.
- O *fallback* dispara simultaneamente o `Right 1` (Canal 0) e o `Right 2` (Canal 1). 
- O resultado: O usuário ouvia o Drum Kit vindo do `Right 1` (devido ao Program Change invasivo enviado erroneamente ao canal 0) e **também ouvia um Piano** vindo do `Right 2`, que continuava ativo na engine unificada.

### Resolução Aplicada
- Implementação de roteamento seguro através de estado: introdução do controle `overrideRightHandChannel` no `GlobalMidiRouter`.
- O `MidiRhythmCreatorDialog` agora gerencia dinamicamente este override usando um `DisposableEffect`. Quando aberto e uma track é selecionada, todo o tráfego da mão direita no roteador global é interceptado e redirecionado EXCLUSIVAMENTE para a track selecionada.
- O envio do Program Change na seleção da trilha foi corrigido para alvejar o canal nativo da track (`track.channel - 1`), eliminando a sobreposição destrutiva e vazamentos no `Right 1`/`Right 2`.

---

## 3. Desempenho: Vazamento de Callbacks

### Causa Raiz
Foi descoberta uma regressão drástica de performance no `DisposableEffect` que instanciava o listener MIDI do gravador. A propriedade mutável `MidiRhythmState.currentTick` (que muda a cada 20 milissegundos durante o playback) estava declarada como chave de dependência do `DisposableEffect`.
Isso causava a destruição e reinicialização completa da escuta de eventos MIDI dezenas de vezes por segundo, correndo o risco iminente de perda de eventos MIDI (Note On/Off "engolidos") por causa de *race conditions*.

### Resolução Aplicada
- Remoção do `currentTick` das dependências (Keys) do `DisposableEffect`. A leitura de variáveis de Estado dentro de *lambdas* não requer recriação do efeito no Compose.

## Conclusão de Fase
O ambiente rítmico encontra-se novamente estável, o metrónomo e a timeline funcionam dinamicamente segundo as regras reais da teoria musical e o roteamento de Voices e *Layers* ocorre sem vazamentos entre painéis e a engine de Ritmos. Nenhuma arquitetura legada, C++ ou Python foi violada na correção deste fluxo. O sistema de unificação MIDI permanece intacto.
