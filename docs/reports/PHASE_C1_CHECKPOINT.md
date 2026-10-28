# CHECKPOINT C1 — AUDITORIA E PLANO DE CORREÇÃO DO MOTOR CASM/BASS

## 1. Causa Raiz Confirmada
O cálculo incorreto para acordes invertidos (ex: `G/D`) no `BassRule` origina-se na **utilização exclusiva do destRoot** para ancorar o resultado, desprezando `destBass`. Além disso, o limite de transposição (`highKey`) é checado contra `destRoot` ao invés de `destBass`, causando octave drops no momento errado.
O segundo problema agravante é que `ArrangerEngine.kt` gera um `dummyCasm` estático (`HighKey = 6`), ignorando completamente o `CasmChannel` correspondente à trilha atual no `.STY` que contém os valores reais extraídos (High Key, Low Limit, High Limit, etc).

## 2. Arquivos que precisam ser modificados

1. **`app/src/main/java/com/example/domain/chord/YamahaRules.kt`**
   - *Por quê:* Precisamos corrigir `BassRule.transpose` para calcular a nova nota a partir de `destBass` em caso de acordes invertidos (Slash Chords) e comparar com o `highKey` correto. Também repassar a mesma correção pro `RootTransRule` se necessário, mas focando primeiro no Bass.
2. **`app/src/main/java/com/example/ArrangerEngine.kt`**
   - *Por quê:* Precisamos remover a construção do `dummyCasm` dentro de `transposeNote` e alterar a assinatura do método para receber o objeto `CasmChannel` *real* que já está parseado e pronto no `StyFile`.
3. **`app/src/main/java/com/example/MidiRhythmCreator.kt`**
   - *Por quê:* Como o chamador de `transposeNote`, o playback engine precisará obter a regra CASM aplicável à seção atual (`MidiRhythmState.currentSection`) e ao canal atual (`track.channel - 1`), para passar adiante na chamada.

## 3. Arquivos que permanecerão intocados
- UI (`ValidationPlayerScreen`, `MidiPlayerScreen`, `InspectorScreen`, etc)
- Engine de C++ (Oboe/JNI/RealTimeMidiScheduler)
- File Parser (`StyReader.kt`, `CasmParser.kt`) - Eles já extraem com sucesso o CASM e Ctb2.
- Outras regras em `YamahaRules.kt` (GuitarRule, MelodyRule)

## 4. Fluxo Atual vs Onde a Info é Perdida
**Atual:** `.STY → StyReader → CasmParser (Extrai Ctb2 com sucesso) → StyFile.casm`
**PERDA AQUI:** `MidiRhythmCreator` não consulta o `casm` e apenas passa notas puras e a Seção para `ArrangerEngine.transposeNote`.
`ArrangerEngine` cria um CASM vazio fake (`dummyCasm = CasmChannel(highKey = 6)`).
**Consequência:** `YamahaRules` executa cegamente usando parâmetros inventados e cálculo de baixo baseado apenas na Raiz (Root).

## 5. Fluxo Corrigido
`MidiRhythmCreator` procurará no `MidiState.currentStyle?.casm` o `CasmChannel` correspondente à seção tocada (ex: `Main A`) e ao canal alvo (ex: 10 para baixo).
Passará esse `CasmChannel` para `ArrangerEngine.transposeNote`.
`YamahaRules (BassRule)` usará o `HighKey` verdadeiro deste `CasmChannel`, além de usar `destBass` para a âncora da transposição.

## 6. Caso G/D (Matematicamente)
- Origem: `C2 (36)`
- `destRoot`: `G (7)`
- `destBass`: `D (2)`
- `HighKey`: (Real do CASM, ex: `PopReggaeBR` tem `HighKey = 0`, mas com `NTR=ROOT_FIXED(0)` ou limiters que ajustam isso, vamos considerar a lógica real do Bass onde High Key diz quando descer).
- *Lógica Errada:* `36 / 12 * 12 + BassTable[0] + destRoot(7) = 36 + 0 + 7 = 43 (G2)`. Checa se `7 > HighKey(6)`. Se sim, subtrai 12 -> `31 (G1)`.
- *Lógica Correta:* `36 / 12 * 12 + BassTable[0] + destBass(2) = 36 + 0 + 2 = 38 (D2)`. Checa se `2 > HighKey`. Se sim, tira 12. Se `HighKey=6`, fica como `38 (D2)`.

## 7. HighKey / LowKey
No `CasmChannel`, as propriedades `highKey`, `noteLowLimit` e `noteHighLimit` estão parseadas a partir da estrutura Ctb2.
O `YamahaRule` irá checar `highKey` para Octave Drop, e `noteLowLimit / noteHighLimit` para limites absolutos após a transposição base.

## 8. Testes
- Estilos a testar: `PopReggaeBR.sty`, `AcusticoBR1.sty`
- Acordes: C, G, F, Am, Dm, C/E, G/D, F/A
- Verificação: Checar o log ou monitor para confirmar que o `highKey` não é mais sempre `6` e que G/D toca `D2` e não `G1`.

## 9. Riscos
A correção do fluxo vai mudar globalmente a transposição, então há o risco das trilhas de ritmo e guitarra passarem a tocar de forma estranha caso o parser CASM esteja mapeando NTR/NTT diferente do padrão adotado. Para mitigar, vou garantir fallback para as regras seguras caso a `Section` ou `CasmChannel` retorne nulo.

## 10. Autorização
Parei e estou no aguardo da autorização.
