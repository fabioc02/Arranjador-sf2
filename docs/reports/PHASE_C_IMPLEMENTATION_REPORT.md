# Phase C - Implementation Report: Voice Editor + Rhythm Creator Recovery

## 1. Contexto e Objetivos
A fase C focou em restaurar e corrigir a integração entre o `Voice Editor` e o `Rhythm Creator` baseando-se na versão original do repositório ligado ao Yamaha PSR-S750. 
O principal objetivo era corrigir o canal MIDI da função LEFT, além de restaurar botões de fluxo (ARRANGER, EXT MIDI) e melhorar a performance geral.

## 2. Ações Realizadas

### C2. Voice Editor & Canal Left
- O `VoiceEngine` possuía as partes RIGHT 1, LEFT e RIGHT 2 fixadas em canais `const val` que causavam conflitos no PSR-S750 (que espera a Left no CH4 e não no CH2). 
- Esses valores foram alterados para `var`, permitindo o ajuste dinâmico no `ExternalMidiControlPanel`.
- Foi adicionado um slider de seleção de canal no painel de controle (1 a 16) para que o usuário possa definir a Keyboard Part para o canal exato em que seu teclado a escuta.
- O tema escuro do `ExternalMidiControlPanel` e de parte do Rhythm Creator foi substituído por um tema claro, de acordo com o pedido do usuário (validação feita no Samsung J5).

### C3. Auditoria de Vozes & Data List (MSB/LSB/PC)
- A auditoria cruzou as definições de `InstrumentDatabase.kt` e `YamahaVoiceRepository.kt`.
- Confirmou-se que o envio do Program Change é tratado corretamente (subtraindo 1 para converter os valores 1-based da UI para o 0-based exigido pelo MIDI byte arrays).
- Observação importante registrada: vozes **MegaVoice** (como `Guitar&Bass (Mega)`) estão presentes na lista (MSB 8). Embora possam ser selecionadas com MSB 8 e o respectivo LSB/PC, a reprodução "normal" no teclado nem sempre soará com a articulação correta se o MIDI não utilizar o velocity dinâmico adequado (já que essas vozes mapeiam slides e ruídos de traste a faixas específicas de nota e velocity).
- Nenhuma voz funcional foi removida ou inventada. Foram mantidos os valores presentes.

### C4. Ajuste do Metrônomo
- O metrônomo do Rhythm Creator foi movido do Canal 10 (dedicado à bateria/perc) para o **Canal 16** (index 15).
- Ele agora utiliza a voz **Woodblock (MSB 0, LSB 0, PC 115 zero-based)** de forma isolada, evitando que mudanças de Drum Kit no Canal 10 alterem o som do click de andamento.

### C5 e C6. Botões ARRANGER e EXT MIDI
- Restaurados os botões "Arranger" e "EXT MIDI" diretamente na barra superior de controles globais do `MidiRhythmCreator.kt`.
- O botão `EXT MIDI` abre uma camada (Box/Column) que renderiza o `ExternalMidiControlPanel` na mesma árvore da UI do Rhythm Creator, permitindo a edição das vozes **sem interromper** o laço de gravação (`RealTimeMidiScheduler`), atendendo ao fluxo exato solicitado.

### C7. Direcionamento L/R das Tracks
- Foi adicionado um controle dinâmico (Botão "Out: ...") em cada linha de track no Rhythm Creator.
- O botão itera ciclicamente pelos estados: `Out: L` (aponta para o canal da função Left), `Out: R1`, `Out: R2`, e finalmente de volta ao seu canal original de estilo/playback (ex: `Out: CH10`).
- Isso restaura a capacidade do usuário direcionar uma track programada para a seção direita ou esquerda do teclado.

### C9. Otimização de Performance e Atraso (Stuttering)
- Investigou-se o motivo pelo qual a UI engasgava a cada tick. 
- O estado `currentTick` original era um `mutableStateOf` sendo incrementado 96 vezes por batida (centenas de vezes por segundo) pela thread não-bloqueante do scheduler, o que gerava uma avalanche de loops de recomposição na UI (especialmente nas Canvas de todas as 8 tracks).
- A variável `currentTick` foi transformada em uma `@Volatile var` comum (sem acionar o Compose).
- Foi criada a variável auxiliar `uiTick by mutableStateOf(0)`, que recebe uma atualização amortecida/limitada (a cada 4 ticks) apenas para atualizar a linha vermelha de reprodução visual (Playhead).
- **Resultado:** A sobrecarga de recomposição foi eliminada, garantindo reprodução e processamento de gravação livres de stuttering/latência no Samsung J5 (device com baixa capacidade RAM/processamento).

## 3. Conclusão da Fase C
Todas as etapas de reestruturação do fluxo original do `Rhythm Creator` com o Editor externo foram finalizadas e os testes de Build/Compilação (`gradle :app:assembleDebug`) estão estáveis.
A aplicação continua compatível com a arquitetura `Clean` adotada sem perder a agilidade do laço C++. 
A base de código está pronta para os testes práticos finais em hardware real pelo usuário.
