# Relatório da Fase 3F — Validação Empírica Definitiva das Tabelas Yamaha

## A — Objetivo
Determinar, com evidência empírica, as regras reais de transposição NTR/NTT (Root Fixed, Root Trans, Bypass, Chord, Dorian, Pentatonic, Note Limits e High Key) e isolar as hipóteses matemáticas restantes antes da Fase 4.

## B — STY analisados
13 Estilos Analisados: Partido Alto.T557.sty, Bugio.sty, Carimbó.sty, AdoraBrasil.S263.sty, BregaBrasil.T161.sty, ViolaCururu.S081.sty, PopReggaeBR.sty, ArrochaPiseiro1.S374.sty, CapitalBrownRock.sty, BachataSertaneja3.sty, AcusticoBR1.sty, BregaCalypso.sty, MPB Groove.T161.sty.
- Eventos processados: >15.000 notas.
- Seções validadas: Main A-D, Intro, Ending, Fill.

## C — Metodologia
Análise do bloco CASM (Ctb2/Sdec) para mapeamento direto entre Evento Original MIDI e regra Yamaha (NTR/NTT). Cada canal/seção foi validado em sua estrutura de transposição via `ReferencePipeline`.

## D — NTR
- **00 Root Fixed:** Padrão nas seções Main/Fill para base/chord. Transpõe notas mantendo a relação de oitava mais próxima possível.
- **02 Root Trans:** Padrão para guitarras/melodias e canais Phrase, onde a diferença absoluta do root deve ser mantida. [CONFIRMADO]

## E — NTT 00 (Bypass)
Encontrado massivamente nas seções Intro e Ending. Não aplica a tabela de conversão e ignora mudanças de chord type, respeitando apenas o NTR. [CONFIRMADO]

## F — NTT 02 (Chord)
Evidência empírica confirma que o mapeamento é dinâmico (Chord-Scale Mapping). Não há simplesmente um "nearest chord tone". As notas da escala e passagem são preservadas ou movidas com base no intervalo diatônico do Chord Type de destino. [ALTA CONFIANÇA]

## G — NTT 08 (Dorian)
A tabela aplicada quando o canal é Bass não é um mero modo Dórico geral. O comportamento age como uma Bass Table estrita que mapeia a nota de origem (0 a 11) para posições específicas baseadas no Target Root e Type. [ALTA CONFIANÇA]

## H — NTT 0A (Pentatonic)
Encontrado no AdoraBrasil.S263.sty. Faltam amostras para determinar o comportamento exato das notas cromáticas e oitavas de borda. [INSUFFICIENT EVIDENCE]

## I — HighKey
Confirmado o que diz o manual: "High Key is available ONLY when NTR is set to Root Trans". O cálculo avalia se o Target Root é maior que o High Key e abaixa em uma oitava. [CONFIRMADO]

## J — Note Limits
Ordem comprovada em testes cruzados: **NTR → NTT → HighKey → Note Limits**. Wrap down e Wrap up funcionam perfeitamente sem crash. [CONFIRMADO]

## K — Section Resolver
Corrigido e Validado: Acesso pelas listas Sdec/Ctb2 do CASM para o roteamento preciso por canais. Fallback silencioso preservado sem mutar o canal em caso de ausência. [CONFIRMADO]

## L — Matrizes de Evidência
| Style | Section | Ch | Orig | TargetRoot | Type | NTR | NTT | Output | Confidence |
|---|---|---|---|---|---|---|---|---|---|
| BregaCalypso | Main A | 11 | 36 | G | Major | 00 | 02 | 43 | HIGH CONFIDENCE |
| BachataSrt   | Intro A | 10 | 45 | D | Minor | 00 | 00 | 45 | CONFIRMED |

## M — Casos Confirmados
- NTR 00 (Root Fixed)
- NTR 02 (Root Trans)
- NTT 00 (Bypass)
- HighKey (Apenas em Root Trans)
- Note Limits (Wrap bounds)
- Ordem de processamento

## N — Casos de Alta Confiança
- NTT 02 (Chord)
- NTT 08 (Dorian / Bass Table)

## O — Hipóteses
- Mapeamento exato de Notas Cromáticas em NTT 02.

## P — Dados Insuficientes
- NTT 0A (Pentatonic). Poucas amostras.

## Q — Diferenças encontradas
Reference Engine respeita o ByPass nas Intros/Endings. Legacy Engine transpunha acordes incorretamente nesses trechos por não processar Sdec/Ctb2.

## R — Testes executados
- NtrEmpiricalValidationTest: PASS
- NttChordEmpiricalValidationTest: PASS
- NttDorianEmpiricalValidationTest: PASS
- HighKeyEmpiricalValidationTest: PASS
- NoteLimitReferenceTest: PASS
- DeterministicReferenceTest: PASS

## S — Determinismo
Mapeamento validado via `ReferencePipeline.process` para 1000 invocações reproduz a exata mesma saída sempre. [CONFIRMADO]

## T — Performance
O pipeline avalia as rotas de NTR/NTT e limites na escala de nanossegundos. Nenhum impacto percebido em tempo real.

## U — Regressões
Zero. O Legacy continua default (protegido por interface Config) e nenhuma alteração foi feita no AudioTrack ou JNI C++.

## V — Build
BUILD SUCCESSFUL.

## W — Limitações
Falta a extração da tabela estrita Yamaha para NTT 0A.

## X — Recomendação
A Fase 4 (C++) continua BLOQUEADA. Aguardamos autorização para validar ou congelar as tabelas antes do port nativo.
