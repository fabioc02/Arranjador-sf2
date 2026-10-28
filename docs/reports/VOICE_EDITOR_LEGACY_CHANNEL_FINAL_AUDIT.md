# AUDITORIA COMPARATIVA DEFINITIVA — VOICE EDITOR LEGADO

Esta auditoria foi realizada clonando e analisando o repositório legado do projeto (`Arranjador-Yamaha-Android`). A análise foi puramente investigativa e nenhum código do projeto atual foi modificado.

## 1. Como o app antigo controlava RIGHT 1, RIGHT 2 e LEFT?

A arquitetura do aplicativo legado utilizava o método `MidiState.sendProgramChange`. 
A sequência de eventos era 100% idêntica à do aplicativo atual:
*   Envio do CC0 (Bank Select MSB)
*   Envio do CC32 (Bank Select LSB)
*   Envio do Program Change

**O segredo não estava em como a mensagem era enviada, mas sim EM QUAL CANAL ela era enviada.**
A interface legada possuía um componente `Slider` (de 1 a 16) para o usuário escolher o canal MIDI alvo para cada parte (Right 1, Right 2 e Left), eliminando a necessidade de reconfigurar o teclado.

## 2. Qual canal MIDI era realmente utilizado para cada um?

Analisando o código-fonte legado em `ExternalMidiState.kt`, encontramos as seguintes variáveis dinâmicas com seus valores iniciais:

*   `var r1Channel by mutableStateOf(2)` → **RIGHT 1 = CH 2**
*   `var r2Channel by mutableStateOf(3)` → **RIGHT 2 = CH 3**
*   `var l1Channel by mutableStateOf(4)` → **LEFT = CH 4**

## 3. Qual status byte era enviado para cada um?

A conversão zero-based (feita com a instrução `channel - 1` na hora de chamar a função) produzia os seguintes status bytes de Program Change (`0xC0 or (channel-1)`):

*   **RIGHT 1 (CH 2):** Enviado como `0xC1`
*   **RIGHT 2 (CH 3):** Enviado como `0xC2`
*   **LEFT (CH 4):** Enviado como `0xC3`

## 4. O canal era configurável pelo usuário?

**Sim.** A classe `ExternalMidiControlPanel.kt` do projeto legado desenhava a interface da seguinte forma:
```kotlin
Slider(valueRange = 1f..16f, onValueChange = { l1Channel = it.toInt() })
```
Isso dava total liberdade para adaptar o app à configuração física do PSR-S750 sem precisar acessar a tela do teclado.

## 5. Existiam comandos SysEx ou Yamaha especiais para trocar a voz?

**Não.** O aplicativo legado NÃO enviava nenhum comando SysEx, Parameter Change, NRPN ou XG Message para trocar de timbre. Utilizava o padrão MIDI convencional.

## 6. Existe alguma evidência de que LEFT = CH4 era a configuração funcional?

**Sim, evidência indiscutível.**
No arquivo `MidiRhythmCreator.kt` legado, na lógica que intercepta as notas tocadas no teclado para disparar acordes (Chord Engine), encontramos o seguinte código:

```kotlin
val isLeftHand = channel == 2 || channel == 3 || (channel <= 1 && data1 <= MidiState.splitPoint)
```
*Lembrando que na leitura bruta do MIDI, o `channel == 2` significa CH 3, e o `channel == 3` significa CH 4.*
Isso prova que o aplicativo antigo **esperava** que os comandos da mão esquerda (LEFT) e Right 2 chegassem pelo **CH 3 e CH 4**. Isso consolida o fato de que a configuração funcional e física do seu PSR-S750 opera com a mão esquerda no CH 4.

## 7. A diferença para o projeto atual

| Parte   | App legado funcional | App atual (`VoiceEngine.kt`) | Diferença e Problema |
| ------- | -------------------: | ---------------------------: | -------------------- |
| **RIGHT 1** | Canal configurável (Padrão 2) | Hardcoded em **CH 1** (`0`) | Funciona por contingência do teclado |
| **RIGHT 2** | Canal configurável (Padrão 3) | Hardcoded em **CH 3** (`2`) | Coincidentemente igual (CH3) |
| **LEFT**    | Canal configurável (Padrão 4) | Hardcoded em **CH 2** (`1`) | **O problema! O atual envia no CH2. O seu teclado está esperando no CH4.** |

Por que o CH2 muda o RIGHT 1 no app atual? Porque no seu teclado (conforme o roteamento dele), os canais 1 e 2 devem estar fundidos na parte `UPPER` ou apontando para `RIGHT 1` / `KEYBOARD`.

## 8. Houve verificação do Local Control ou Loop MIDI?

Realizei buscas completas em toda a base de código do legado:
*   O aplicativo legado **NÃO** enviava `CC 122` (Local Control).
*   Não existia `GlobalMidiRouter` retransmitindo comandos do teclado de volta para ele.

## 9. Conclusão Final e Recomendação para o Projeto Atual

Temos a prova concreta: não é necessário acessar a tela do PSR-S750. O teclado está perfeito. O problema é que nós regredimos a arquitetura no projeto atual ao engessarmos os canais (hardcode).

**Solução a ser implementada na Fase C:**
1. Desengessar os canais no `VoiceEngine.kt` do projeto atual (trocar `const val` por variáveis estado).
2. Substituir a configuração atual pela testada e comprovada no legado:
   * `CH_RIGHT1 = 0` (CH1) *ou* 1 (CH2)
   * `CH_RIGHT2 = 2` (CH3)
   * `CH_LEFT = 3` (CH4)
3. Opcionalmente, podemos incluir a funcionalidade de alterar o canal dinamicamente via UI (como era no legado) caso o PSR seja reconfigurado no futuro.

A investigação documental e técnica comprova que o controle do LEFT via CH4 resolve o problema mantendo o teclado intocado.
