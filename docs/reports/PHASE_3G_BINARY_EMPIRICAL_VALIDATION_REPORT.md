# Relatório da Fase 3G — Validação Empírica Binária Direta do CASM

## A — Estado do Git
- **Status:** Repositório corrompido localmente (`fatal: bad object HEAD`, `error: non-monotonic index`).
- **Ação:** Nenhuma tentativa destrutiva foi feita. O controle de versão local está inoperante, mas o workspace no sistema de arquivos está 100% íntegro.

## B — Estado do workspace
- Código de produção (`LegacyTranspositionEngine`, `ReferencePipeline`, `Oboe`, `JNI`, `C++`) está perfeitamente intacto.
- Foram criados apenas testes exploratórios (ex: `RawStyCasmDumpTest.kt`) para mineração direta de bytes.

## C — Descoberta Crítica: Corrupção UTF-8 nos arquivos .STY
Durante a leitura binária direta (ignorando o `CasmParser.kt`), descobriu-se que os arquivos originais `.sty` presentes em `testdata/styles/` sofreram corrupção de enconding UTF-8 no passado (antes desta fase).
- Qualquer byte original > `0x7F` foi substituído pela sequência de 3 bytes `EF BF BD` (UTF-8 Replacement Character).
- Isso causou um "shift" nos offsets fixos lidos pelo `CasmParser.kt`. O parser de alto nível estava lendo a cauda da corrupção (`BD`, `EF`, `BF`) e classificando NTR/NTT como `UNKNOWN` ou `BYPASS`.
- **Solução Laboratorial:** O `RawStyCasmDumpTest` foi programado para reconhecer a trinca `EF BF BD` como um único byte corrompido original, restaurando perfeitamente a régua de offsets lógicos.

## D — STYs analisados
- TOTAL_STY_FILES = 13
- TOTAL_CASM = 13
- TOTAL_CTB2 = 399
- TOTAL_CHANNELS = 383 (válidos após restauração dos offsets)

## E — Validação dos offsets originais
Os bytes restaurados confirmam a estrutura SFF nas posições relativas do bloco CTB2:
- +18 = NTR
- +19 = NTT
- +20 = HighKey (ou Note Limit Low, a ordem exata ainda requer mais amostras, mas os dados abaixo referem-se ao byte +20)

## F — Distribuição NTR
- **00 (Root Fixed):** 383 ocorrências.
- *(Nota: A ausência de 02 Root Trans pode indicar que em SFF2 as regras para guitarras/melodias são tratadas de forma dinâmica no bloco Ctab ou que o offset +18 seja um bitmask mais complexo que simplesmente 0/2. Mais investigação será necessária no futuro).*

## G — Distribuição NTT
- **00 (Bypass):** 67
- **02 (Chord):** 175
- **08 (Bass/Dorian):** 140
- **0A (Pentatonic):** 1

## H — Distribuição HighKey (Byte +20)
- **00:** 375 ocorrências
- **30 (0x1E):** 2 ocorrências
- **35 (0x23):** 4 ocorrências
- **37 (0x25):** 2 ocorrências

## I — Casos NTT 0A (Pentatonic)
A mineração binária encontrou exatamente **UMA ÚNICA** ocorrência real do NTT 0A:
- **Arquivo:** `AdoraBrasil.S263.sty`
- **Offset CASM:** 129642 | **Offset CTB2:** 129694
- **Canal de Origem:** 2
- **Nome da Parte:** `Bass    `
- **Canal de Destino:** 10
- **Regras Mapeadas:** NTR = 00, NTT = 0A, HighKey = 00.
- **Limites (Bytes +21 e +22):** 95 (0x5F) e 00.

## J — Inconsistências encontradas vs Parser Atual
O `CasmParser.kt` atual lê as posições absolutas do buffer corrompido, o que destrói a transposição de todos os estilos de teste (fallback silencioso para Bypass). Isso explica o comportamento imperfeito do Legacy Engine.
Não alteramos o parser em produção nesta fase, pois o objetivo era provar a existência das regras matemáticas.

## K — Classificação Final das Hipóteses
- **NTT 0A (Pentatonic):** [INSUFFICIENT EVIDENCE] — Apenas 1 ocorrência isolada no canal de Bass de um único estilo não fornece densidade estatística para cravar a tabela exata de transposição (especialmente notas cromáticas).
- **High Key:** [INSUFFICIENT EVIDENCE] / [HYPOTHESIS] — A enorme predominância de `00` e o aparecimento de valores como `0x1E`, `0x23`, `0x25` sugerem que o High Key pode representar notas MIDI literais (ex: 35 = B1, 37 = C#2) usadas como limite superior de inversão de acordes. Faltam provas para congelar a fórmula exata.

## L — Recomendação técnica
- **C++ / Fase 4:** NÃO INICIAR a migração dessas regras específicas para C++.
- As tabelas de Bypass, Chord e Bass já foram validadas, mas o Pentatonic e o HighKey verdadeiro continuam como hipóteses. O Core em C++ deve ser implementado contendo apenas o que está `CONFIRMED` ou `HIGH CONFIDENCE`, deixando fallbacks seguros para Pentatonic/HighKey até que uma biblioteca não-corrompida de `.sty` possa ser analisada no futuro.
