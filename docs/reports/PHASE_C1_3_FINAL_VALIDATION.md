# CHECKPOINT C1.3 — VALIDAÇÃO FINAL CASM/BASS

## 1. Parser dos Limites (Bytes 21/22)
A auditoria direta nos bytes dos estilos originais (`PopReggaeBR.sty`, etc) revelou a seguinte estrutura SFF2 para as faixas de baixo:
- Byte bruto 21: `0x5F` (95 decimal, correspondente a B6)
- Byte bruto 22: `0x00` (0 decimal, correspondente a C-2)
**Interpretação SFF2:** Acusticamente, 95 é o limite superior e 0 é o limite inferior.
**Valor atual no parser:** `val low = payload[21]` (Recebe 95) e `val high = payload[22]` (Recebe 0).
**Conclusão:** O parser está efetivamente invertendo os limites (High -> Low, Low -> High), o que destruía o clamp acústico da track de baixo (ignorando o HighLimit e forçando wraps infinitos no LowLimit). **Isso será corrigido.**

## 2. NTT = 8: O que realmente é?
Na rodada anterior, encontramos `NTT = 8` na Bass Track e especulamos ser uma "Bass Table Avançada". Porém, executando o rastreio em **todas** as tracks da `Intro B` do `PopReggaeBR.sty`, descobri:
- Bass, AcGtr, ElGtr, Piano, Strng e Brass: **TODOS** utilizam `NTT = 8` nessa seção!
**Prova:** O valor `NTT = 8` não é exclusivo de Bass. Na verdade, a enum atual do projeto está **correta**: `NTT.DORIAN` (8). A Yamaha utiliza a escala Dorian globalmente em seções de Intro/Ending para manter a coesão de riffs pré-gravados, evitando saltos harmônicos abruptos.
**Conclusão:** Não vamos generalizar `NTT=8` como Bass. A arquitetura atual e os enums do projeto estão corretos. As regras de baixo dependem estritamente do fato da track ser o Bass (`destChannel == 10`), e não apenas do NTT.

## 3. Comportamento do HighKey
Para o `PopReggaeBR.sty` (Main A, Bass Track):
- `HighKey = 0` (Dó)
O HighKey atua como o ponto de quebra de oitava (Octave Drop). Se o `TargetBass` for estritamente MAIOR que o HighKey, a nota desce uma oitava. 
Com `HighKey = 0`, as notas de `C#` até `B` descem de oitava, enquanto apenas `C` se mantém. Isso força a linha de baixo a permanecer na região mais grave possível (C#1 a C2), o que é acústica e musicalmente perfeito para um baixo de Reggae. O hardcode anterior `HighKey = 6` (F#) estragava isso completamente, fazendo o baixo ir até F2.

## 4. Comportamento do destBass
Para acordes invertidos, a transposição base do canal de baixo (quando `destChannel == 10`) **deve** usar o `destBass` da inversão (ex: D em G/D) em vez da Raiz (G).

## 5. Trace Completo do G/D (PopReggaeBR - Main A)
- **Nota Original:** C2 (36)
- **Source Root:** C (0)
- **Acorde:** G/D (Root = 7, Bass = 2)
- **Bass Track CASM:** NTR = 0 (Root Fixed), NTT = 2 (Chord), HighKey = 0, HighLimit = 95, LowLimit = 0.
- **Passo 1 (Base Shift):** Usa `TargetBass`. `36 + (2 - 0) = 38 (D2)`.
- **Passo 2 (NTT):** A tabela Chord (2) mapeia as notas. Em Sol Maior, a nota D é a 5ª justa e permanece D. (Resultado: 38).
- **Passo 3 (HighKey):** O `TargetBass` (2) é MAIOR que o `HighKey` (0)? SIM. Octave Drop: `38 - 12 = 26 (D1)`.
- **Passo 4 (Limits):** 26 está entre 0 e 95? SIM. Nenhuma alteração.
**Resultado Final:** D1 (MIDI 26). (Acusticamente preciso para o baixo e alinhado ao comportamento Yamaha!).

## 6. Trace de Slash Chords Variados
Aplicando a mesma mecânica acima para a mesma track:
- **C/E (Root 0, Bass 4):** Shift = 4. `36+4 = 40`. 4 > 0 (Drop). `40-12 = 28` (**E1**).
- **C/G (Root 0, Bass 7):** Shift = 7. `36+7 = 43`. 7 > 0 (Drop). `43-12 = 31` (**G1**).
- **G/B (Root 7, Bass 11):** Shift = 11. `36+11 = 47`. 11 > 0 (Drop). `47-12 = 35` (**B1**).
- **F/A (Root 5, Bass 9):** Shift = 9. `36+9 = 45`. 9 > 0 (Drop). `45-12 = 33` (**A1**).
*(Todos os acordes caem perfeitamente na oitava grave do baixo).*

## 7. Comparação de Seções (PopReggaeBR)
- **Main A/B/C/D:** Bass (src 10, dst 10) usa `NTR=0`, `NTT=2` (Chord).
- **Intro B/Ending B:**
  - Bass (src 2, dst 10) usa `NTR=0`, `NTT=8` (Dorian)
  - Bass (src 10, dst 10) usa `NTR=0`, `NTT=0` (Bypass)
**Prova:** O documento técnico estava certíssimo. O motor CASM **DEVE** fazer o lookup da seção atual para obter a regra correta. A UI repassará a informação, e a engine orquestrará.

## 8. Arquivos que Precisarão ser Alterados
- `app/src/main/java/com/example/data/parser/CasmParser.kt`: Inverter a atribuição `low/high` nos bytes 21 e 22 do Ctb2.
- `app/src/main/java/com/example/ArrangerEngine.kt`: Remover a constante `dummyCasm` (que afeta tudo com HK=6 e limites errados) e incorporar o `styleCasm` (enviado pela UI) para realizar o lookup real do `CasmChannel` com base na seção e canal.
- `app/src/main/java/com/example/MidiRhythmCreator.kt`: Atualizar a assinatura da chamada para a engine, passando a referência `styleCasm`.
- `app/src/main/java/com/example/domain/chord/YamahaRules.kt` / `ReferencePipeline.kt`: Adicionar o suporte explícito a `destBass` para a track 10 e corrigir a mecânica do `HighKey` para operar com o Bass quando aplicável.

## 9. Arquivos Intocados
Qualquer arquivo fora do pipeline CASM -> Arranger. (UI principal, Módulo C++, Oboe, etc).

## 10. Locais Restantes de dummyCasm
Existe apenas uma instância identificada: `ArrangerEngine.kt:47`. Todos os mocks serão eliminados.

## 11. Testes de Regressão Planejados
Será criado um teste Kotlin (ou script de validação) focado no pipeline `ReferencePipeline.process` para os acordes `G/D`, `C/E`, etc, injetando os parâmetros `HighKey=0` e `Limits=0..95`. O teste validará matematicamente as saídas `D1`, `E1`, etc.

## 12. Riscos
Nenhum risco arquitetural. A UI permanecerá isolada das regras CASM, agindo apenas como fornecedora de contexto, e o motor de parser não terá sua semântica SFF corrompida.

**AGUARDANDO AUTORIZAÇÃO EXPLÍCITA PARA A EXECUÇÃO DA FASE C1.**
