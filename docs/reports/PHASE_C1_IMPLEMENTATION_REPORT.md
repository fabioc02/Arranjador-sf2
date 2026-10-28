# Relatório de Implementação: Fase C1 (CASM/Bass Pipeline)

## 1. Correção do Parser CASM (`CasmParser.kt`)
Corrigimos a inversão identificada durante a auditoria C1.3. O SFF2 (Style File Format 2) da Yamaha determina que o byte 21 de um `Ctb2` é o limite superior (`High Limit`) e o byte 22 é o limite inferior (`Low Limit`). O código foi ajustado para carregar e respeitar essa ordem:
```kotlin
val high = payload[21].toUByte().toInt()
val low = payload[22].toUByte().toInt()
```

## 2. Remoção do Mock e Integração Real (`ArrangerEngine.kt`)
Removemos a dependência do `dummyCasm` (que impunha hardcode `HighKey = 6` e limites de `0` a `127`).
A engine de arranjo agora aceita o parâmetro opcional `styleCasm` e `trackChannel`. Ela localiza a seção atual (ex: "Main A") e pesquisa o canal de destino (`destChannel`) correspondente. Caso as informações reais do `.STY` sejam encontradas, elas são fornecidas ao motor de transposição. Apenas caso as informações não existam, utilizamos um fallback flexível.

## 3. Propagação do CASM Real pela UI (`MidiRhythmCreator.kt`)
Conforme a exceção arquitetural permitida, atualizamos o `MidiRhythmCreator.kt` para injetar o contexto real na engine de transposição durante o modo Arranger.
1. `MidiRhythmState.kt` recebeu o armazenamento global do arquivo carregado via `var currentStyle`.
2. `ArrangerViewModel.kt` foi atualizado para injetar esse dado assim que a leitura do `.sty` for concluída com sucesso.
3. Durante a reprodução iterativa, a track MIDI e a seção atual acionam o `ArrangerEngine.transposeNote` fornecendo o `styleCasm` para o correto mapeamento do SFF2 Sdec.

## 4. Atualização das Regras de Baixo / Slash Chords
Tanto a engine de referências (`ReferencePipeline.kt`) quanto a legacy engine (`YamahaRules.kt`) foram adaptadas para cumprir rigorosamente as normas Yamaha para transposição de baixo, especialmente os "Slash Chords" invertidos (ex: Acorde G/D). 
1. **Base Shift:** Em trilhas de baixo (`destChannel == 10`), a âncora da transposição é sempre `destinationChord.bassKey`.
2. **HighKey Drop:** A regra do `HighKey` agora avalia corretamente a nota contra o `bassKey` do destino para as trilhas de baixo. Antes ela verificava a nota do root do acorde, o que quebrava o drop de oitava ao acionar o inversor.
3. **Regra Mantida:** Comprovou-se em C1.3 que `NTT=8` e `NTR=0` (Dorian/Root Fixed) **NÃO DEVEM** ser traduzidos isoladamente como "Bass". Eles agora são preservados na tabela de regras, e as avaliações de baixo validam se o target tracker é `10` (Baixo) ou confiam no engine padrão se houver explícito `NTT=BASS`.

## 5. Testes de Regressão
Foram implementados testes de unidade comprobatórios (`BassSlashChordTest.kt` e `LegacyBassSlashChordTest.kt`), validando o percurso exato descrito no arquivo `PopReggaeBR.sty` (Trace C1.3):
- **Cenário G/D (Sol com baixo em Ré):** 
  - Nota SFF Original: C2 (36)
  - Transposição baseada no Bass(D): +2 semitons -> 38 (D2).
  - HighKey para o Baixo é 0. O TargetBass(2) > HighKey(0), desencadeando um "Octave Drop" de -12 semitons.
  - Limite Casm: 0-95.
  - Resultado final aprovado e aferido nos testes: D1 (26).
- **Cenário C/E (Dó com baixo em Mi):** Validado com resultado E1 (28).

*Nota: Os testes locais e a compilação do Android (Applet) foram avaliados com êxito, garantindo que as mudanças preservaram o fluxo de dependência.*
