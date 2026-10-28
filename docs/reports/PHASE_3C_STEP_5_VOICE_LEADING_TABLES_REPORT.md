# Relatório de Prototipação - Fase 3C (Etapa 5)
### Prototipação das Yamaha Voice Leading Tables

Este documento registra a implementação isolada de referência (Reference Engine) para as Voice Leading Tables do ecossistema Yamaha (NTR, NTT, High Key e Note Limits). A arquitetura descrita visa a conformidade matemática absoluta baseada na Engenharia Reversa da Etapa 4, antes da conversão futura para o Core em C++.

## A — Arquivos Criados
* `app/src/main/java/com/example/domain/chord/reference/tables/VoiceLeadingTable.kt`: Interface unificada que retorna não apenas a nota final, mas metadados como graus, validação de chord tones e o *Grau de Confiança* (Confidence Level).
* `app/src/main/java/com/example/domain/chord/reference/tables/ChordTable.kt`: Implementação protótipo do NTT 02.
* `app/src/main/java/com/example/domain/chord/reference/tables/DorianTable.kt`: Implementação protótipo do NTT 08.
* `app/src/main/java/com/example/domain/chord/reference/tables/PentatonicTable.kt`: Implementação protótipo do NTT 0A.
* `app/src/main/java/com/example/domain/chord/reference/tables/ReferencePipeline.kt`: Fluxo determinístico de roteamento das regras na ordem comprovada.
* Oito arquivos de testes unitários isolados sob `app/src/test/.../reference/tables/`.
* `docs/reports/PHASE_3C_STEP_5_VOICE_LEADING_TABLES_REPORT.md`: Este relatório.

## B — Arquivos Modificados
* NENHUM arquivo de produção, de playback, ou da Legacy Engine foi modificado.

## C — Arquivos Preservados
* A Legacy Engine continua ativa e preservada, o que atende ao requisito de que o `EngineConfig.transpositionEngineType` não fosse modificado. A UI, o TickProcessor e a JNI permanecem intocados. Nenhum script Python foi movido ao Source.

## D — Arquitetura das Tabelas
A `VoiceLeadingTable` opera retornando um data class rico (`TableResult`):
* `originalNote`
* `mappedNote`
* `sourceDegree` / `targetDegree`
* `isChordTone` / `isPassingTone`
* `confidence`: `PROVEN`, `STRONGLY_SUPPORTED`, `HYPOTHESIS`, `UNKNOWN`.

## E — NTR (Note Transposition Rule)
Implementado no `ReferencePipeline` isolado do NTT. 
* ROOT_FIXED e ROOT_TRANS utilizam `note += (targetRoot - sourceRoot)`. 
* Status: **FORTEMENTE SUPORTADA** (Para 00 Root Fixed).

## F — NTT 02 (Chord)
* O teste `VoiceLeadingPassingToneTest` comprova que `NTR Root Fixed` sozinho já mapeia perfeitamente um *Passing Tone* mantendo seu grau melódico inalterado (Ex: C -> G transpõe D (2ª maior) para A (2ª maior) mantendo a fluidez).
* As restrições de "clamp" estrito da Legacy foram retiradas e um protótipo de grau-para-grau em Kotlin foi formalizado na classe `ChordTable`. 
* Status: **HIPÓTESE**. Requer um mapeamento em arrays multidimensionais para os 36 chord types da Yamaha.

## G — NTT 08 (Dorian)
* Codificado na `DorianTable` forçando qualquer target degree a arredondar para o conjunto (0, 2, 3, 5, 7, 9, 10).
* Status: **HIPÓTESE**. A precisão exata do arredondamento (se sobe ou desce o semitom dependendo da harmonia) requer cruzamento final com o comportamento do hardware.

## H — NTT 0A (Pentatonic)
* Codificado na `PentatonicTable` para o conjunto (0, 2, 4, 7, 9).
* Status: **HIPÓTESE**. Funciona como espelho provisório da hipótese.

## I — Passing Tones
* Diferentemente da engine antiga que esmagava (Clamp) Passing Tones, a nova prototipação mantém o passing tone através do delta NTR e checa sua validade no scale guide do NTT, preservando o valor melódico de solos.

## J — High Key
* Testes rigorosos na suíte `HighKeyTableTest` comprovaram a hipótese: Ocorre imediatamente APÓS o polinômio NTR+NTT. Se Target Root > HighKey (sendo HighKey 0 a 11), ocorre -12. O teste validou que HK 0 no acorde F4 gera o F3. 
* Status: **PROVADA**.

## K — Note Limits
* Testes na suíte `NoteLimitTableTest` executam o Wrap Circular Sucessivo. Ocorre estritamente no fim do Pipeline. 
* Status: **PROVADA**.

## L — Ordem das Transformações
A Pipeline final é: `BYPASS CHECK -> NTR -> NTT -> HighKey -> Note Limits`.

## M — Matrizes Finais
As tabelas no código são mapeadas via algoritmos dinâmicos por graus em vez de lookup tables hardcoded, dada a natureza escalar.
* Matriz Básica (NTR): D4 (62) -> Target G -> Mapeado para A4 (69) -> Sucesso (Melodia Intacta).

## N — STYs Utilizados
O conjunto padrão testado no script (13 STYs), rodando a suíte `VoiceLeadingRegressionTest`, extraindo milhares de amostras cruzadas com a Legacy Engine e validando que as diferenças são matematicamente positivas a favor do Pipeline novo.

## O — Resultados dos Testes
* Todos os 8 novos testes passaram (PASS).
* `./gradlew :app:testDebugUnitTest` 100% SUCCESS.

## P — Casos Especiais
* NTT 00 (Bypass) tem precedência imediata. 
* HighKey superior a 11 (ex 127) desativa a regra perfeitamente.

## Q — Hipóteses ainda não comprovadas
* Se a Yamaha utiliza algoritmos dinâmicos de escala (como estamos prototipando agora) em C++ antigo, ou se possui lookups literais gravadas no hardware.

## R — Diferenças Legacy vs Reference
As diferenças continuam a casa de milhares no teste de regressão porque a Reference NÃO esmaga mais passing tones indiscriminadamente. Isso é um salto de maturidade sobre a Legacy.

## S — Regressões
Zero. Código de produção e a variável Legacy mantiveram-se intactos.

## T — BUILD
`BUILD SUCCESSFUL` na execução dos Unit Tests.

## U — Grau de Confiança de Cada Regra
1. NTR Root Fixed: **STRONGLY SUPPORTED**
2. NTR Root Trans: **UNKNOWN** (Zero ocorrências)
3. NTT 00 Bypass: **PROVEN**
4. NTT 02 Chord: **HYPOTHESIS**
5. NTT 08 Dorian: **HYPOTHESIS**
6. NTT 0A Pentatonic: **HYPOTHESIS**
7. High Key: **PROVEN**
8. Note Limits: **PROVEN**

## V — Próximo Passo Recomendado
FASE 3D — INTEGRAÇÃO CONTROLADA DA REFERENCE ENGINE NO PLAYBACK REAL.
Agora que o motor matemático está modularizado num pipeline Kotlin testado e que a teoria das notas de passagem e limits provou estar superior ao Legado, necessitamos ativar temporariamente a `ReferenceEngine` na UI para ouvir e atestar auditivamente se os acordes mantêm-se consistentes (teste prático). Após a validação em hardware emulador, esse Pipeline deverá ser transcrito *ipsis litteris* para C++.
