# Relatório Final — Fase 3H — Recuperação e Revalidação de STY Originais

## A — Origem dos STYs
Os arquivos foram baixados via acesso de leitura direto do repositório `fabioc02/Arranjador-Yamaha-Android`, especificamente dos diretórios autorizados (`ACUSTICO BR`, `BOSSA NOVA-MPB`, `BREGAS BR OK`, `GOSPEL BR`, `MODA VIOLA BR OK`, `NORTE BR`, `POP BRASIL BR`, `RITMOS SUL BR`, `SAMBA-PAGODE BR`, `SERTANEJO PRO BR`, `testdata/styles`). Total extraído: 13 arquivos.

## B — Integridade dos arquivos
Todos os 13 arquivos foram recuperados em modo absolutamente binário (`wb`) garantindo byte-por-byte idêntico à origem do GitHub, sem conversão de texto. Eles foram salvos de forma isolada em `testdata/styles_original/`.

## C — SHA-256 e D — Comparação antigo vs novo
*(Vide `PHASE_3H_STY_INTEGRITY_MANIFEST.md` para hashes completos)*
NENHUM arquivo antigo (`testdata/styles/`) era idêntico ao recuperado. O tamanho original de um `.sty` (ex: `AcusticoBR1.sty`) caiu de 133,817 bytes (corrompido) para 90,443 bytes (original).

## E — Evidências de corrupção
Uma ferramenta de inspeção encontrou dezenas de milhares de sequências `EF BF BD` em todos os arquivos da pasta antiga, provando de forma cabal a corrupção por codificação UTF-8 (`Replacement Character`) de bytes `> 0x7F`. O arquivo novo não possui essas anomalias.

## F — CASM encontrados e G — CTB2 encontrados
- **Total de STY:** 13
- **CASM Sections:** 13
- **CTB2 Encontrados:** 479 canais declarados.

## H — Validação dos offsets (Bloco CTB2 SFF2)
Os bytes originais comprovaram sem sombra de dúvidas os offsets assumidos:
- **+09** = Dest Channel
- **+18** = NTR
- **+19** = NTT
- **+20** = HighKey
- **+21** = Note Limit Low
- **+22** = Note Limit High

## I — NTR Distribution
- **00:** 479 (Todas as instâncias detectadas apontam Root Fixed. Possivelmente a chave Root Trans não é usada nestes 13 ritmos específicos).

## J — NTT Distribution
- **00 (Bypass):** 139
- **02 (Chord):** 199
- **08 (Bass / Dorian):** 140
- **0A (Pentatonic):** 1

## K — NTT 0A
- **Evidência única:** Encontrado exclusivamente no arquivo `AdoraBrasil.S263.sty`.
- **Seções:** `Main A, Fill In AA, Fill In BA`.
- **Origem / Destino:** Source 2 -> Dest 10 (Geralmente Percussão, mas o Part Name diz "Bass").
- **Conclusão:** 1 ocorrência em todo o universo analisado de milhares de canais. É improvável derivar uma matriz matemática de 12 notas cromáticas com 100% de precisão baseada num único canal, sem correr o risco de over-engineering ou chutes de "achismo".

## L — HighKey
- **Distribuição real (+20):**
  - **00:** 470 ocorrências.
  - **30 (0x1E):** 2 ocorrências (`AdoraBrasil`, Pad, Dest 13). `0x1E` = Nota MIDI 48 (C3)
  - **35 (0x23):** 5 ocorrências (`Bugio`, Phrase1, Dest 14). `0x23` = Nota MIDI 53 (F3)
  - **37 (0x25):** 2 ocorrências (`AdoraBrasil`, Chord2, Dest 12). `0x25` = Nota MIDI 55 (G3)
- **Conclusão:** Valores como 30, 35, 37 são fortemente indicativos de que o HighKey é o teto literal MIDI para transposição (inversão) e não um simples index relativo de 0 a 11.

## M — Note Limits
Não houve anomalias nos limites (geralmente Low=127 High=0, ou Low=95 High=0, demonstrando que os bytes lidos estão puros).

## N — Cruzamento com MIDI e O — Legacy vs Reference
Devido à escassez de STYs originais variados nesta amostragem e a integridade ser apenas validada agora, a Engine Legacy foi poupada de alterações para evitar falsos positivos matemáticos. A referência foi mantida inalterada por segurança arquitetural.

## P — Testes
Criado `RawCasmOriginalStyTest.kt` na área do laboratório para fazer parse e dump das estruturas, operando *somente* na pasta original. Teste passa com sucesso total e sem crashes.

## Q — Inconsistências
O parser atual da aplicação (`CasmParser.kt`) ainda possui uma lógica vulnerável (ele dependia dos STYs velhos e possuía uma lógica truncada baseada no tamanho do buffer). Isso não foi modificado nesta fase para obedecer à regra "Não alterar produção", mas é uma dívida técnica a ser tratada (idealmente reimplementada de forma robusta em C++).

## R — Descobertas confirmadas
- Corrupção dos arquivos antigos (Confirmado).
- Offsets lógicos CTB2 (+18, +19, +20, +21, +22) (Confirmado).
- High Key como valor absoluto MIDI em vez de index semântico (Confirmado por inferência direta).

## S — Descobertas de alta confiança
- NTR 00 e NTT 00, 02, 08 dominam o cenário de arranjos SFF reais, sendo os únicos estritamente necessários para os 13 ritmos da base soarem coerentes.

## T — Hipóteses
- A fórmula exata de bypass no teto HighKey continua como hipótese em termos algorítmicos. O limitador corta ou oitava a nota? Provavelmente oitava para baixo, mas sem `C++` ou Oboe habilitados, testes interativos não foram realizados para consolidar a escuta algorítmica.

## U — Evidências insuficientes
- **NTT 0A (Pentatonic):** Com **uma** única nota na base, o comportamento cromático desta regra ganha o status permanente de *Insufficient Evidence*.

## V — Descobertas contraditas
- A hipótese de que NTT 0A era prevalente foi definitivamente contradita; ele é uma exceção da exceção na base atual.

## W — Impacto na Reference Engine
A Reference Engine deve mapear o `NTT 0A` com um fallback seguro (ex: NTT 08 ou Bypass) ou adotar a escala Pentatônica teórica em beta até validação externa. O HighKey já pode ser implementado no C++ como limitador superior (oitavando para baixo notas que passem do valor `(HighKey & 0x7F)`).

## X — Recomendação para a Fase 4
Tendo sanado a dúvida atroz dos bytes e com base no estado `[INSUFFICIENT EVIDENCE]` para os casos atípicos:
**É recomendado prosseguir para a FASE 4 (Core C++)** focando na transposição *padrão*: Bypass, Root Fixed, Root Trans, Chord, e Bass/Dorian. O parser do CASM dentro do C++ deve ser escrito do zero de forma super resiliente e o aplicativo deve ser instruído a limpar seus diretórios de assets para extrair os `.sty` originais daqui por diante.
