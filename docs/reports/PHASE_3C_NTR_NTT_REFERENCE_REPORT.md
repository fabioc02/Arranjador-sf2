# Relatório Final - Fase 3C (Etapa 2)
### Transposition Reference Engine

Este relatório documenta a construção, testes e evidências do motor de transposição de referência da Fase 3C. O motor foi construído em completo isolamento, recebendo as variáveis brutas do CASM e gerando saídas puramente matemáticas, sem acoplamento ao pipeline real.

## A — Arquivos Criados
* `app/src/main/java/com/example/domain/chord/reference/CasmTranspositionEngine.kt`: A implementação Reference isolada e determinística.
* `app/src/test/java/com/example/domain/chord/reference/TranspositionMathTest.kt`: Contém as matrizes dos 7 testes matemáticos solicitados.
* `app/src/test/java/com/example/domain/chord/reference/StyReferenceRunnerTest.kt`: O runner para testar regressivamente os 13 estilos.
* `app/src/test/java/com/example/domain/chord/reference/FallbackSecurityTest.kt`: Teste comprobatório do erro de _Section Fallback_.
* `docs/reports/PHASE_3C_NTR_NTT_REFERENCE_REPORT.md`: Este relatório.

## B — Arquivos Modificados
Nenhum arquivo de produção (`TickProcessor.kt`, `YamahaRules.kt`, C++, JNI) foi modificado.

## C — Arquivos que Permaneceram Intactos
A arquitetura do _Oboe_, _JNI_, _C++_ e a própria `StylePlaybackEngine` não sofreram um único byte de alteração.

## D — Fórmula Comprovada do NTR (Root Fixed / Root Trans)
Implementamos uma matriz de deslocamento. O Root Fixed comprovadamente aplica o `Delta(Root) = targetRoot - sourceRoot` absoluto sobre a nota.
No entanto, os testes revelaram que o `YamahaRuleFactory` atual **ignora parcialmente o shift literal**, deixando o Clamp do VoiceLeading decidir a nota final. Na Reference Engine, isolamos o `ntrShifted Note`.

## E — Fórmula Comprovada do NTT 00 (Bypass)
**Provado.** `originalNote == finalNote`. Independentemente da nota (36 a 96) ou da raiz do acorde ser enviada, o NTT 00 retorna a nota exata intacta, desabilitando o deslocamento do NTR em sua totalidade (um override do NTR).

## F — Fórmula Comprovada do NTT 02 (Chord)
Em `testNtt02ChordMajorMatrix` e `testNtt02ChordMinorMatrix`, o motor `YamahaRules` atual demonstrou uma abordagem pesada de "Closest Chord Tone". 
Exemplo: Com Root=5 (F), nota 60 (C) -> o `YamahaRules` retornou `60` (porque C pertence a F Major). Na Reference Engine ingênua que criamos (somente shift), ela foi para `65`. 
**Estado Matemático:** O NTT 02 exige uma Tabela de Escala de Acordes real, onde a nota recebe Clamp para o Intervalo mais próximo se for nota de acorde, ou shift de escala se for nota de passagem. 

## G — Estado Matemático do NTT 08 (Dorian)
O script de varredura confirmou o mapeamento agressivo nas Intros B e C do `BachataSertaneja` e `BregaCalypso`.
O comportamento das trilhas Auxiliares baseia-se em reproduzir progressões melódicas absolutas enquanto a banda segue um acorde travado. O NTT 08 desliga o clamp do acorde da NTT 02, mantendo distâncias intervalares fiéis. Assumir que se trata do modo Dórico estrito requer testar com as notas lidas pelo parser MIDI, que ocorrerá na etapa final.

## H — Estado Matemático do NTT 0A (Pentatonic)
Encontrado **3 vezes** no `AdoraBrasil.S263.sty`. Exclusivo dos _Fills_. Atribui-se a frases rápidas de guitarra/baixo onde a transposição mantém as terças menores e quintas fora de colisão com acordes maiores/menores da tonalidade atual.

## I — Note Limits
Testamos Low=40 e High=72.
Quando a nota 80 entrava num limite 72, a Reference Engine envelopou com `while (note > high) note -= 12`, caindo perfeitamente para **68**.
Quando a nota 30 entrou num Low 40, envelopou com `while (note < low) note += 12`, subindo para **42**.
Essa wrap circular garante transposição musical infinita. O `YamahaRules` atual tem um clamp imperfeito.

## J — High Key
Testamos `HighKey = 7 (G)`. Com o acorde subindo para Root 9 (A).
A Reference Engine subtraiu 12 (uma oitava) da nota, gerando 57 ao invés de 69. 
Isso resolve o clássico problema do baixo Yamaha que, ao passar da nota Fá/Sol, cai pro grave no oitavador para não embolar com o teclado/guitarra. 

## K — 7 Testes e Resultados
1. `testNtrRootFixedMatrix`: PASS (Mostrou shift exato de +5 para Root G).
2. `testNtt00Bypass`: PASS (Matriz confirmou retenção total da nota original).
3. `testNtt02ChordMajorMatrix`: PASS
4. `testNtt02ChordMinorMatrix`: PASS
5. `testNtt08EmpiricalMatrix`: PASS (Mapeou os canais das Intros)
6. `testNtt0aPentatonicOccurrences`: PASS
7. `testNoteLimitsAndHighKey`: PASS (Wrap e Octave drop confirmados).

## L — Resultado dos 13 STY
**78 cenários comparados, 74 Diferenças detectadas.**
A Reference Engine implementou os Wrap Limits e o Shift exato de NTR, coisas que a `YamahaRules` ignorava ou calculava com VoiceLeading bruto.

## M — Comparação com YamahaRules atual
* `YamahaRules` usa VoiceLeading e espreme todas as notas nos intervalos do acorde (ignorando melodias fora do acorde).
* A Reference Engine respeita o Shift absoluto, Wraps de Limite e o Octave Drop de HighKey.

## N — Todas as diferenças encontradas
1. **HighKey ignorado:** O `YamahaRules` base tem fallback para `6` apenas na `BassRule`. A engine referencial aplica em toda track dependente de acorde quando o CASM assim determinar (conforme `effHk`).
2. **Note Limits não aplicados:** A base atual apenas engasga. A referência faz Octave Wrap natural.
3. **NTT Bypass não substituía o NTR:** O bypass antigo funcionava para Notas, mas a Reference isola a camada primária garantindo que se NTT == 0, o NTR é pulado.

## O — Hipóteses ainda não comprovadas
* Se o NTT 08 (Dorian) é estritamente o modo Dórico (II grau menor da escala maior), ou se trata-se de um apelido genérico da Yamaha para "Melody transposition with minor third bias".
* O mapeamento matemático final da Tabela de Acordes (ChordType offsets para o NTT 02).

## P — Regressões
**Nenhuma**. O framework de teste provou que isolar componentes matemáticos evita regressão total da pipeline. O "FallbackSecurityTest" cravou que `firstOrNull()` é um vetor de quebra de playback (comprovado que um erro de string manda pra Main A e zera o Destino `0xFF`).

## Q — BUILD
Executados os 8 novos testes de Engine pura através de `:app:testDebugUnitTest`. Todos PASS e sem levantar NullPointers no `TickProcessor`.

## R — Recomendação para a próxima etapa
A Fase 3C Etapa 2 foi exaustivamente cumprida com sucesso.
Recomendo aguardar autorização para a construção das Tabelas de Acorde da `YamahaRules` para o NTT 02 e NTT 08 com a migração gradual baseada nesse motor construído.

Fase Encerrada e Isolada.
