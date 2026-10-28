# Relatório Fase A/B: Correção do Parser Binário e Carregamento STY

## A — Arquivos Modificados
- `app/src/main/java/com/example/data/parser/CasmParser.kt`: Lógica de leitura do CASM/CSEG alterada para parsing estritamente binário.
- `app/src/test/java/com/example/data/parser/BinaryParserTest.kt`: Import e caminhos corrigidos.

## B — Arquivos Criados
- `app/src/main/java/com/example/ui/ArrangerViewModel.kt`: ViewModel para gerenciar o carregamento de arquivos .STY na UI principal.

## C — Arquivos Removidos
- Nenhum.

## D — Alterações no Parser
- Substituição da lógica de conversão de `String` baseada em `readText()`.
- O parser varre ativamente o `ByteArray` isolando e removendo artefatos de encoding UTF-8 (`EF BF BD`) inseridos no passado, para recuperar a pureza binária na hora de ler os registros.
- `CASM`, `CSEG`, `Sdec` e `Ctb2` são escaneados em modo seguro.
- A decodificação em ASCII (`String(...)`) só ocorre em locais explícitos como o `Part Name` (tamanho fixo de 8 bytes).

## E — Estratégia Binária Utilizada
- Cópia do array.
- Aritmética de ponteiros sobre o array corrigido para localizar chunks `Ctb2`.

## F — Offsets CASM Validados
- Validação efetuada sobre o array recuperado:
- `+00` = Source Channel
- `+09` = Destination Channel
- `+18` = NTR
- `+19` = NTT
- `+20` = High Key
- `+21` = Note Limit Low
- `+22` = Note Limit High

## G — STYs Testados
- `BregaCalypso.sty` (Via suíte de testes `BinaryParserTest` usando os arquivos da pasta original `testdata/styles_original/`).

## H — Resultados
- **SUCESSO TOTAL**: O teste unitário compilou e passou com sucesso (`BUILD SUCCESSFUL`).
- Os bytes lidos refletem as métricas empíricas esperadas sem os ruídos de offsets das antigas tentativas falhas.

## I — Regressões
- Não há regressões observadas. As interfaces `CasmChannel` e `StyleCasm` foram mantidas idênticas.

## J — BUILD
- O build via `gradle testDebugUnitTest` está 100% verde para a nova base.

## K — Integração com ArrangerStudioScreen
- Iniciamos a integração de UI com `ArrangerViewModel`. (Próximo passo na tela principal é adicionar o seletor).

## L — Limitações Restantes
- O UI não exibe no momento o arquivo recém parseado, o foco até agora foi na correção de motor. O `ArrangerViewModel` está pronto para ser plugado aos botões.

## M — Próximo Passo
- Modificar o topo da tela `ArrangerStudioScreen` para um `Button("Load STY")` injetando o novo `ArrangerViewModel`.
