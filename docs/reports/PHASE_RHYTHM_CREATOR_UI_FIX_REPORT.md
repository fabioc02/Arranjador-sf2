# Relatório de Correções: Rhythm Creator UI e Metrônomo

Atendendo às observações feitas após os testes práticos com o teclado, foram realizadas as seguintes correções exclusivamente no Rhythm Creator (a tabela CASM não foi tocada):

## 1. Conflito do Metrônomo com a Track Phrase 2
- **Causa:** O metrônomo do aplicativo estava programado para tocar na Track Phrase 2 (Canal MIDI 16 / índice 15) e enviava um *Program Change* forçado para Woodblock (115). Isso fazia com que qualquer tentativa de usar a Phrase 2 para um instrumento (ex: Strings) fosse interrompida e alterada para sons percussivos.
- **Correção:** O metrônomo foi movido para o Canal MIDI de Bateria (Canal 10 / índice 9). Além disso, o *Program Change* global foi removido. Agora, o metrônomo aciona apenas as notas `76` (Hi Wood Block) e `77` (Low Wood Block) padronizadas pelo kit General MIDI (GM), liberando a Phrase 2 integralmente para gravação sem conflitos.

## 2. Correção do Botão `Out: CH` (Slider de Panning)
- **Causa:** Um botão `Out: CHX` havia sido implementado para alterar o canal MIDI dinamicamente (roteando para L, R1, R2), o que conflitou com o slider de Pan L/R solicitado anteriormente, quebrando a mixagem estéreo das trilhas rítmicas.
- **Correção:** O botão roteador de canais foi inteiramente removido da interface das tracks. Em seu lugar, foi implementado o controle deslizante (Slider) `Pan`, que envia mensagens de MIDI CC 10 (Panorama), permitindo ao usuário posicionar cada instrumento no espectro L/R corretamente sem alterar sua porta de saída MIDI. Adicionado também o rótulo "Vol" no slider de volume para clareza visual.

## 3. Botão `Undo` Adicionado
- **Causa:** O botão para apagar a última gravação havia sido perdido em atualizações anteriores da interface.
- **Correção:** Um sistema de Snapshot foi implementado no modo de gravação (`MidiRhythmState.lastRecordingSnapshot`). Ao pressionar `REC`, o sistema guarda um backup das trilhas; um botão `Undo` foi adicionado ao lado de `REC` que, ao ser clicado, reverte as tracks exatamente para o momento antes da última gravação.

## 4. Consolidação de Botões Play/Stop
- **Causa:** A presença separada de botões para Play, Pause e Stop ocupava espaço desnecessário e gerava confusão.
- **Correção:** Os três botões foram consolidados em apenas um botão central. Ele exibe verde "PLAY" e muda para um botão vermelho "STOP" durante a reprodução, atuando em modo *Toggle*.
