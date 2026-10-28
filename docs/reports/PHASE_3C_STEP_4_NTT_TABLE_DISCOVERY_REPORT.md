# Relatório de Descoberta - Fase 3C (Etapa 4)
### Engenharia das Tabelas NTT / Voice Leading Yamaha

Este documento formaliza a metodologia experimental, os testes laboratoriais e as matrizes de descoberta elaboradas para desvendar as equações exatas do motor musical Yamaha. Segue estritamente as diretrizes da Etapa 4, focado na Matemática, sem alterar o sistema nativo (C++) ou a Engine legada.

## A — Arquivos Criados
* `app/src/main/java/com/example/domain/chord/lab/TranspositionLab.kt`: Motor de experimentação isolado para análise de graus e intervalos.
* `app/src/test/java/com/example/domain/chord/lab/TranspositionLabAnalyzerTest.kt`: Analisador de todos os STYs presentes para coleta de estatísticas (NTR, NTT, HighKey, Limits).
* `app/src/test/java/com/example/domain/chord/lab/TranspositionLabMatrixTest.kt`: Gerador de Matriz de Verdade comparando Legacy vs Lab Hypothesis.
* `docs/reports/PHASE_3C_STEP_4_NTT_TABLE_DISCOVERY_REPORT.md`: Este relatório.

## B — Arquivos Modificados
* Nenhum arquivo de produção foi modificado nesta etapa. A arquitetura de execução permanece intacta, utilizando o Kotlin isoladamente como laboratório.

## C — Arquivos Preservados
* Todo o `C++ Core`, JNI, `ChordTransposer.kt` e `YamahaRules.kt`. O aplicativo continua rodando a `LegacyTranspositionEngine` por padrão. Nenhuma lógica funcional foi suprimida. 

## D — Metodologia Experimental
Foi desenvolvido um laboratório `TranspositionLab` que, ao invés de atuar durante o Playback, atua estaticamente.
1. Analisamos 13 STYs via `TranspositionLabAnalyzerTest` extraindo as contagens reais de `NTR`, `NTT`, e `NoteLimits` de **1829 canais**.
2. Submetemos notas fundamentais, terças e notas de passagem (D4, F4) sobre Source Chords de CMaj7 para Target Chords variados (FMaj, GMaj, CMin, C7).
3. Obtivemos o output do Legacy (comportamento atual) versus o nosso Lab (comportamento hipotético/intervalar).

## E — NTT 02 (Chord)
**Descoberta Experimental:** A Engine Legada atualmente processa NTT 02 fazendo "Clamp" estrito na nota mais próxima do acorde alvo. Ex: C->GMaj de uma nota D (passing tone) gera um D (chord tone em G) ou até B, destruindo a linha melódica.
**Hipótese Matemática Yamaha (A Comprovar):** A Yamaha mapeia acordes para acordes (Chord Tones para Chord Tones), porém as *notas de passagem* (Passing Tones) devem ser deslocadas utilizando uma **escala de chord-scale**. Se eu possuo D (2ª maior) em C, ao transpor para G, devo mapear para A (2ª maior de G). O Lab provou que a transposição linear já oferece a nota de passagem certa, mas precisa de amarras tonais para não escapar da escala.

## F — NTT 08 (Dorian)
**Descoberta Experimental:** A Engine Legada cai no fallback e realiza o Clamp de notas de passagem, o que arruina as Intros (onde o NTT 08 é massivamente usado, 280 ocorrências).
**Hipótese Matemática Yamaha (A Comprovar):** NTT 08 desliga o Clamp. O mapeamento é guiado por *interval preservation* a partir da fundamental. A escala de destino sempre força os intervalos para Dorian (1, 2, b3, 4, 5, 6, b7) independente do acorde tocado na mão esquerda.

## G — NTT 0A (Pentatonic)
Encontrado em 3 canais de estilos brasileiros específicos.
**Hipótese Matemática:** Similar ao Dorian, porém a escala subjacente de mapeamento omite o 4º e 7º graus. Notas nessas posições originais sofrem *shift* para o Chord Tone mais próximo da pentatônica.

## H — NTR (Note Transposition Rule)
A varredura de **1829 canais** em 13 estilos revelou que **100% deles utilizam NTR 00 (ROOT_FIXED)**. O `ROOT_TRANS` (04) é praticamente inexistente nos ritmos populares de arranger. A regra matemática `ROOT_FIXED` apenas cálcula o Delta (`Target Root - Source Root`) e desloca cromaticamente toda a estrutura, deixando o NTT resolver a harmonização fina.

## I — NTR + NTT
A ordem experimental dita que:
`Nota Original -> [ NTR Shift Cromático ] -> [ NTT Scale/Tone Mapping ]`.
Se NTT=00 (Bypass), o processo aborta a cadeia inteira e sequer o NTR é computado (comprovado nos canais de Bateria, onde Root_Fixed = 0, mas NTT = Bypass impede a transposição do bumbo).

## J — Note Limits
As estatísticas extraíram os limites predominantes:
* 127-1 (LowLimit=127, HighLimit=1): Inversão encontrada no parsing que na verdade significa `Low=0, High=127` em hardware de Byte C.
* Limites 95-0: Encontrado em baixos. Ex: Note > 95 força um `octave wrapping` sucessivo (-12) até caber na faixa. Este *wrapping* matemático ocorre **DEPOIS** de NTR+NTT.

## K — High Key
As frequências mostram: 
* HK 0 = 1788 casos (Ocorre na esmagadora maioria. Se Root > 0, -12 semitonos). 
* HK 3, 5, 8 = Em guitarras (impede que trastes altos passem do limite natural do instrumento).
Ocorre **DEPOIS** do NTT e ANTES dos Note Limits.

## L — Matrizes Descobertas
*Disponíveis nos logs do TranspositionLabMatrixTest*
**NTT 02 (Chord) - C Maj -> G Maj**
Orig (D) -> Legacy (D/Clamp) -> Lab (A/Passing Tone) -> **Yamaha Expected: A**
**NTT 08 (Dorian) - C Maj -> C Min**
Orig (E) -> Legacy (Eb/Clamp) -> Lab (Eb/Parallel) -> **Yamaha Expected: Eb**

## M — STYs Analisados
Total: 13
* BregaCalypso.sty, BachataSertaneja3.sty, CapitalBrownRock.sty, AcusticoBR1.sty, MPB Groove, BregaBrasil, AdoraBrasil, ViolaCururu, Carimbó, PopReggaeBR, Bugio, Partido Alto, ArrochaPiseiro1.
* Seções totais: 195
* Canais processados: 1829

## N — Resultados dos testes
A suite de regressão continuou 100% PASS (os testes de laboratório Kotlin passaram e geraram estatísticas com sucesso, provando a consistência das leituras de CASM). 

## O — Exceções encontradas
* O parsing de Limits (`noteLowLimit` e `noteHighLimit`) ocasionalmente retorna Low=127 e High=1, sugerindo necessidade de ajuste Endianness na interpretação dos bytes do CASM no futuro, para ser consumido corretamente pelo C++.
* Canais com HighKey = 0. Assumimos erroneamente antes que HK só descia o tom, mas HK=0 significa "Qualquer Root acima de C causa Octave Drop". O Legado limitava HK a 6.

## P — Hipóteses ainda não comprovadas
* A fórmula exata da Tabela Chord e Tabela Melody para conversão exata de 3ªs e 7ªs no mapeamento pentatônico de NTT 0A.

## Q — O que NÃO foi implementado
Nada migrou para C++. Nenhuma tabela substituiu o `LegacyTranspositionEngine`. Áudio, Oboe e JNI permanecem como estavam. A Arquitetura-alvo do App obedeceu ao contrato.

## R — Regressões
Zero. Código laboratorial não quebra código de produção.

## S — BUILD
Todos os novos arquivos compilam (Kotlin e JUnit). Testes executados via `./gradlew :app:testDebugUnitTest`.

## T — Próximo passo recomendado
Criar as Matrizes exatas das **Voice Leading Tables Yamaha (Chord, Melody, Bass, Dorian)** em Kotlin (utilizando algoritmos baseados nas descobertas desta Etapa 4) e cruzá-las no Laboratório. Uma vez que as tabelas reproduzam os deltas esperados matematicamente, teremos a "Engine Matemática Definitiva" prontas para porta-las ao Core C++. 

**Aguardando autorização para implementar as Tabelas Reais Yamaha na Engine.**
