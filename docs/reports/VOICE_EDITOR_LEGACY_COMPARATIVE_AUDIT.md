# AUDITORIA COMPARATIVA — VOICE EDITOR / LEFT / RIGHT 1 / RIGHT 2

## 1. Como o app antigo controlava as partes (RIGHT 1, RIGHT 2, LEFT)?
Após clonar e analisar minuciosamente o repositório legado (`https://github.com/fabioc02/Arranjador-Yamaha-Android`), o fluxo de envio do aplicativo antigo era:

**Fluxo:**
`UI` (Botão "Send Program Change") → `MidiState.sendProgramChange(canal)` → CC0 (MSB) → CC32 (LSB) → Program Change.

O código era exatamente idêntico ao atual na forma de enviar os dados MIDI. **Não havia envio de SysEx para trocar de timbres.**

## 2. Qual canal MIDI era realmente utilizado para cada um?
A grande descoberta desta auditoria está no estado do painel antigo (`ExternalMidiState.kt` e `ExternalMidiControlPanel.kt`).

No aplicativo antigo, os canais **NÃO eram hardcoded** (fixos). O aplicativo possuía um **Slider** na interface do usuário (UI) que permitia alterar o canal de cada parte dinamicamente (de 1 a 16).

Os valores padrão iniciais (`mutableStateOf`) encontrados no código legado eram:
* **RIGHT 1:** `r1Channel = 2` (Índice 1 -> MIDI CH 2)
* **RIGHT 2:** `r2Channel = 3` (Índice 2 -> MIDI CH 3)
* **LEFT:** `l1Channel = 4` (Índice 3 -> MIDI CH 4)

 *(Obs: um dos scripts de patch no repo legado mostra que em certo momento usou-se `r1Channel = 1` e `l1Channel = 3`, provando que isso era frequentemente testado/mudado pelo usuário).*

## 3. Existiam comandos SysEx?
**NÃO.** O aplicativo legado utilizava classes como `YamahaSysExDecoder` e `SysExParser` exclusivamente para **ler e interpretar** arquivos `.sty` (Style) e diagnosticar MIDI IN. 
As funções de envio no `MidiState.kt` legado (`port.send`) só enviavam `NoteOn/Off`, `Control Change` e `Program Change`. Nenhuma mensagem SysEx (iniciada com `F0 43 ...`) era enviada para alterar as Keyboard Parts.

## 4. Existiam comandos especiais Yamaha?
**NÃO.** O app antigo não usava NRPN, RPN, ou comandos de painel especiais. Era apenas a trinca clássica do padrão XG: `Bank Select MSB` + `Bank Select LSB` + `Program Change`.

## 5. Existe uma diferença arquitetural entre o app antigo e o atual?
**SIM.** Esta é a raiz do problema.
* **App Antigo:** Delegava a responsabilidade do canal ao usuário através de um **Slider de Canal MIDI** na interface gráfica. O usuário podia arrastar o canal do LEFT até encontrar o canal que o PSR-S750 estava escutando.
* **App Atual:** Os canais estão "hardcoded" (fixados) na classe `VoiceEngine.kt` (`CH_RIGHT1 = 0`, `CH_LEFT = 1`, `CH_RIGHT2 = 2`).

## 6. Por que o CH2 altera o RIGHT 1 no app atual?
Conforme constatado no relatório anterior, se o teclado receber um Program Change num canal mapeado para `KEYBOARD`, ele muda a voz da parte que está selecionada/ativa fisicamente (RIGHT 1). 
Como no app atual o LEFT está cravado no CH 2, e o seu PSR-S750 não está esperando o LEFT no CH 2 (provavelmente está esperando no CH 3 ou CH 4, ou CH 2 está mapeado como KEYBOARD), ele desvia o comando para o RIGHT 1.

No app antigo, "funcionava corretamente" porque **o canal do LEFT estava configurado no UI para CH 4 (ou CH 3)**, que provavelmente é o canal que bate com a configuração atual do *MIDI Receive* do seu PSR-S750.

## 7. Local Control
O aplicativo antigo **NÃO enviava** a mensagem `CC 122` (Local Control OFF) durante o controle de vozes. O tráfego de teclas e painel físico não era bloqueado.

## 8. É possível reproduzir o comportamento antigo no projeto atual?
Sim, perfeitamente e de forma muito simples. O segredo não está na mensagem MIDI, mas na **flexibilidade do canal**.

## 9. Solução Recomendada
Como você não pode alterar a tela de *MIDI Receive* no PSR-S750, o aplicativo precisa se adaptar ao teclado.
**A solução recomendada é:**
1. Desvincular os canais fixos (`CH_RIGHT1 = 0`, etc.) do `VoiceEngine.kt`.
2. Adicionar uma configuração no aplicativo (pode ser uma engrenagem de configuração no painel do Voice Editor, ou sliders escondidos) que permita definir quais canais MIDI (1 a 16) devem ser usados para Right 1, Right 2 e Left.
3. Se você lembrar que era o CH 4, basta configurar o app para usar o CH 4 (índice 3) para o LEFT. 

Isso resolverá o problema físico imediatamente, sem precisarmos de SysEx mágicos ou depender do visor do teclado.
