# CHECKPOINT C1.2 — AUDITORIA CASM/BASS (FASE 2)

## 1. Causa Raiz Confirmada
- **BassRule atual falha em Inversões:** Utiliza a propriedade `destRoot` do acorde em vez da propriedade `destBass`. 
- **Falta de Dados Reais:** `ArrangerEngine` ignora o objeto de parse e injeta um `dummyCasm` estático com `HighKey = 6` em tudo.
- **Isolamento Arquitetural Violado:** O fluxo estava configurado para ignorar o arquivo lido e usar regras hardcoded na engine.

## 2. O que o Documento Anexado Confirma
- Regras CASM (Sdec/Ctb2) não são globais. Mudam por Seção (Main, Intro) e por Canal (Rhythm1, Bass).
- `NTT` não é universal para o Baixo. Pode ser `Bass Table`, `Chord Table`, ou `Bypass`.
- A Tabela de Baixo deve ser tratada como um mapeamento de intervalo e, **principalmente**, a transposição está associada ao Destination Bass e sofre o Octave Drop do `High Key`.

## 3. O que precisa ser corrigido
- Corrigir a lógica de cálculo em `BassRule` usando `destBass`.
- Adicionar o Lookup do CASM na `ArrangerEngine`.
- **DESCOBERTA CRÍTICA NO PARSER:** O `CasmParser.kt` está extraindo os limites de nota invertidos na SFF2. O byte 21 na verdade é o `High Limit` (ex: 95/96) e o byte 22 é o `Low Limit` (ex: 0). No momento, o parser atribui 96 ao Low e 0 ao High. Isso precisa ser corrigido no parser.
- Ajustar a `YamahaRuleFactory` para despachar o NTT 8 corretamente, visto que em SFF2 ele é usado no Baixo.

## 4. O que ainda está incerto
- Qual o mapeamento exato da tabela NTT 8 na Engine. Atualmente, o Enum 8 mapeia para `DORIAN`. Precisaremos garantir que a BassRule processe o `NTT=8` como Bass.

## 5. NTR Encontrados nos Styles Reais
- **100% de `NTR = 0` (Root Fixed)** encontrados na Bass Track de 10 estilos Brasileiros reais analisados via script (PopReggaeBR, BregaCalypso, etc).

## 6. NTT Encontrados nos Styles Reais
- Na Bass Track: `0` (Bypass), `2` (Chord), e `8` (Dorian/Advanced Bass).
- NOTA: Nenhum estilo usou o `5` (Basic Bass) na Bass Track. Eles usam `8` para o comportamento complexo de baixo no SFF2!

## 7. Como o parser atual representa CASM
- O parser lida perfeitamente com CSEG e Sdec. O `StyleCasm` contém uma lista de `CasmSection` (Main A, etc) e cada uma contém sua lista de `CasmChannel`.

## 8. Como localizar CSEG/Sdec/Ctb2 no Playback
`val casmSection = style.casm.sections.find { it.name == currentSectionName }`
`val casmChannel = casmSection?.channels?.find { it.destChannel == trackChannel }`

## 9. Como localizar o Bass da seção atual
Pela chave do canal (ex: 10 ou 11) cruzada no lookup.

## 10. Trace Completo de G/D no CASM Real
**Origem:** C2 (36) no .STY
**Style Real:** PopReggaeBR.sty
**Ctb2 da Bass Track:** `HighKey = 0`, `HighLimit = 95(B6)`, `LowLimit = 0(C-2)`
**Acorde Tocado:** G/D (Root = 7(G), Bass = 2(D))
**Cálculo Base Yamaha:** Intervalo a partir de C = Bass 2. Nota base = 36 + 2 = 38 (D2).
**Checagem High Key:** Como o Bass Target (2) é MAIOR que o HighKey (0), aplica Octave Drop (-12).
**Nota Resultante:** 38 - 12 = **26 (D1)**.
**Checagem de Limite:** 26 está entre 0 e 95. Passa sem Wrappings.
**Conclusão do Trace:** No CASM real, o baixo vai para o **D1**, mantendo o peso acústico e acertando a harmonia invertida! Nosso código atual gerava G1.

## 11. Styles Reais Analisados
PopReggaeBR.sty, BregaCalypso.sty, AdoraBrasil.S263.sty, CapitalBrownRock.sty. Sections variadas.

## 12. Arquivos que serão modificados
- `app/src/main/java/com/example/data/parser/CasmParser.kt` (Corrigir as offsets High/Low Limits 21/22).
- `app/src/main/java/com/example/ArrangerEngine.kt` (Lookup Section/Channel).
- `app/src/main/java/com/example/MidiRhythmCreator.kt` (Repassar `styleCasm` para ArrangerEngine).
- `app/src/main/java/com/example/domain/chord/YamahaRules.kt` (Lógica real de destBass e HighKey).

## 13. Arquivos que permanecerão intocados
- Interface do Usuário (UI)
- Engine C++/Oboe
- Repositório antigo (Lixo Histórico)

## 14. Testes que serão executados
Testes Kotlin unitários simulando a `BassRule` (G/D, C/E, C/G, F/A) sob parâmetros CASM extraídos (HighKey=0, NTT=8).

## 15. Riscos
Nenhum risco de regressions visuais. O risco musical é controlado devido à nossa checagem restrita e fallbacks de segurança caso o CASM venha vazio.

## 16. Estratégia de Implementação
Aguardando autorização para avançar arquivo por arquivo e comprovar com testes unitários locais antes do commit final.
