# AUDITORIA E RELATÓRIO P1 — PSR-S750 MIDI, VOICE EDITOR E RHYTHM CREATOR

## 1. Mapeamento de Canais e o Comportamento Físico

A Yamaha adota por padrão (em modelos PSR/Tyros) uma alocação específica para controle via painel, porém o PSR-S750 permite alterar isso livremente no menu "MIDI Receive". O fluxo de roteamento foi rigorosamente rastreado desde a interface até o byte MIDI:

**Caminho Real (Rastreamento Efetivo do Código Atual)**
Para garantir que não houvesse suposições, o código do Android foi auditado:
- A interface mapeia cada botão de parte a um índice do `VoiceEngine.channels`.
- `sendProgramChange` constrói o status byte como `(0xC0 or (channel and 0x0F))`.
- As constantes de canal no código estavam definidas como:
  - `CH_RIGHT1 = 0` (Canal MIDI 1)
  - `CH_LEFT = 1` (Canal MIDI 2)
  - `CH_RIGHT2 = 2` (Canal MIDI 3)

**Tabela do Rastreamento do Fluxo:**

| Part (UI) | Canal exibido | Canal no Estado | Canal passado ao MidiState | Status MIDI final | Resultado Físico |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **RIGHT 1** | CH 1 | 0 | 0 | `0xC0` (CH 1) | Muda Right 1 |
| **LEFT** | CH 2 | 1 | 1 | `0xC1` (CH 2) | Muda Right 1 |
| **RIGHT 2** | CH 3 | 2 | 2 | `0xC2` (CH 3) | Muda Right 2 |

**Explicação da Divergência:**
O código **não tem bug de envio duplo** ou conversões erradas no motor: ele realmente está mandando os canais MIDI 1, 2 e 3 em bytes distintos (0xC0, 0xC1 e 0xC2). 
O motivo pelo qual o **CH2 afeta o Right 1** está nas configurações internas do hardware do PSR-S750. Se o teclado estiver configurado com o MIDI Template `KBD & STYLE` ou se CH1 e CH2 estiverem ambos apontando para a "Keyboard Part UPPER", o PSR-S750 roteia ambos os canais de recebimento para a mão direita (Right 1 / Right 2).
Para resolver de forma limpa, a arquitetura recomendada para os arranjadores Yamaha é redefinir o mapeamento do app para a ordem oficial e solicitar que a recepção (MIDI Receive) do hardware acompanhe:
- `RIGHT 1` = Canal MIDI 1
- `RIGHT 2` = Canal MIDI 2
- `LEFT` = Canal MIDI 3

## 2. Voice Bank / Program Change

A auditoria confirmou que a estrutura de MSB/LSB/PC está perfeitamente correta em ambas as fontes de dados do app:
- A `InstrumentDatabase` (usada no Rhythm Creator) tem PC `0-based` diretamente no Kotlin.
- A `YamahaVoiceRepository` (usada no Editor de Timbres) armazena o Program de `1-128`. No momento da seleção, a UI faz: `state.pc = voice.prg - 1`.
**Por que certos instrumentos não tocam?**
De acordo com o PSR-S750 Reference Manual e Data List, Voices da classe **MegaVoice** e algumas **S.Art!** possuem restrições críticas se tentadas via MIDI simples. MegaVoices (ex: violões) ativam ruídos de traste, harmônicos ou palm mutes dependendo do valor exato de *Velocity* das notas, e o teclado comumente os ignora se acessados como instrumentos padrão sem programação XG avançada ou se tocados "ao vivo" (pois foram feitos primariamente para sequenciadores e styles).

## 3. O Problema do Metrônomo (Novo Bug)

**Causa Raiz:** O clique atual (`MidiState.sendNoteOn(9, ...)`) usa o Canal 10 (índice 9), emitindo as notas 76/77 para o High/Low Wood Block. Como o Canal 10 é convencionalmente o mesmo canal reservado para a "Rhythm Track", quando o usuário muda o Drum Kit dessa Track, o teclado substitui os instrumentos do Canal 10. Ao mudar o Drum Kit, a "caixa de som" do Wood Block muda, alterando ou emudecendo o metrônomo.
**Solução Proposta:** Criar uma arquitetura de Click totalmente independente, roteando o metrônomo para um canal intocado (ex: **Canal 16**). Antes de qualquer gravação, o app enviará silenciosamente os parâmetros de timbre Woodblock (MSB=0, LSB=0, PC=115) para o Canal 16. O metrônomo enviará as notas (ex: C3=60, C4=72) nesse canal. Isso elimina qualquer dependência dos Drum Kits das trilhas do Rhythm Creator.

## 4. Rhythm Creator: Funções Perdidas

1. **ARRANGER:** O controle de `MidiState.isArrangerMode` perdeu seu atalho na UI principal do Dialog, o que ativava a escuta dos acordes junto ao player principal.
2. **EXT MIDI:** O atalho que permitia sobrepor o `ExternalMidiControlPanel` e fechar o Rhythm Creator com facilidade (ou dividi-los).
3. **Slider L/R:** Um controle interno (provavelmente Pan `CC 10`) ou um seletor virtual que gerenciava canais que foi temporariamente suprimido na refatoração da linha do tempo.
*A proposta é restaurar esses três botões diretamente no layout superior, sem prejudicar a barra azul (timeline).*

## 5. Proposta de Ações Imediatas para Implementação (Fase C)

- **Aparência:** Remoção imediata dos marcadores `Color(0xFF1E1E1E)` e `Color(0xFF121212)` em `ExternalMidiControlPanel.kt` e `MidiRhythmCreator.kt`, retornando ao tema claro que não causa distração no Samsung J5.
- **Roteamento MIDI:** Atualizar as três constantes no `VoiceEngine.kt` para a ordem natural (R1=0, R2=1, L=2).
- **Rhythm Creator:**
  - Desacoplar o Metrônomo do Canal 10 e roteá-lo para o Canal 16 usando o Program Change do Woodblock nativo.
  - Inserir botões Toggle para `ARRANGER`, atalho `EXT MIDI` e o pequeno slider de "L/R" em cada trilha.

**Aguardando autorização para proceder à Fase C (Implementação Cirúrgica das Correções)!**
