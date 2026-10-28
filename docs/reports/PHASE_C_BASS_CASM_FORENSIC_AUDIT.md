# AUDITORIA FORENSE — FASE C: TRANSPOSIÇÃO DO BAIXO / CASM / HIGH KEY

Esta auditoria foi conduzida estritamente através da análise do código atual, da documentação técnica (Yamaha Reference Manual / Data List) e de simulações do comportamento do Style File Format (SFF), sem nenhuma modificação nos arquivos funcionais do projeto.

---

## A. Fontes Utilizadas
1. **Documentação Oficial:** Yamaha PSR-S750 Reference Manual & Data List (Especificação CASM, CSEG, Sdec, Ctb2, NTR, NTT).
2. **Código Atual Inspecionado:**
   - `app/src/main/java/com/example/ArrangerEngine.kt` (Construção do CASM dummy e invocação de regras).
   - `app/src/main/java/com/example/domain/chord/YamahaRules.kt` (Classe `BassRule` - lógica de transposição).
   - `app/src/main/java/com/example/domain/models/CasmModels.kt`.
3. **Dados de Teste:** Simulação de *Slash Chords* (acordes invertidos) sobre a nota original `C2 (36)` da trilha de Baixo.

---

## B. Comportamento Yamaha Esperado (Regras Oficiais SFF)
No ecossistema Yamaha, a trilha de Baixo segue a tabela **NTT BASS**.
1. **Transposição Base:** A nota gravada (ex: `C` na Source Root `CMaj7`) atua como fundamental e é deslocada para a fundamental do acorde detectado.
2. **Slash Chords (On-Bass):** Quando o usuário toca um acorde invertido (ex: `G/D`), o alvo da transposição da trilha de baixo muda. A nota fundamental da gravação deixa de ir para o **Root** (`G`) e passa a ir para o **Bass** (`D`).
3. **High Key Limit:** Este parâmetro (definido no CASM/Ctb2) impede que acordes altos fiquem agudos demais. Se a nota alvo de transposição for maior que o *High Key*, a frase inteira sofre um *Octave Drop* (-12 semitons).
4. **Regra de Ouro do High Key no Baixo:** Em acordes *Slash*, o High Key é avaliado contra a nota **Bass** e não contra a nota **Root**.

---

## C. Comportamento Atual do Aplicativo
1. O código em `ArrangerEngine.kt` injeta um `dummyCasm` com `HighKey = 6` (F#) fixo para todas as situações. Não está extraindo do `.STY`.
2. A classe `BassRule` em `YamahaRules.kt` realiza o cálculo baseando-se EXCLUSIVAMENTE em `destRoot` (linha `var newNote = ... + targetNoteClass + destRoot`).
3. O parâmetro `destBass` (o baixo do acorde invertido) é completamente ignorado no cálculo da nota e na avaliação do `HighKey`.

---

## D. Divergência Encontrada
Como o código ignora o `destBass`, o nosso motor sempre toca a fundamental (Root) do acorde, ignorando a intenção de inversão do usuário, e pior: dispara incorretamente o limite de *High Key* ao testar a raiz errada.

---

## E. Caso GM/D: Trace Completo (A Prova do Problema)
**Situação:** O usuário toca **G/D** e reclama que o baixo soa numa região grave demais (e com a nota errada).

**Trace no Nosso Código (ERRADO):**
1. **Origem:** Track do Baixo gravada em `C2 (36)`.
2. **Acorde Detectado:** `G/D` (Root = `G(7)`, Bass = `D(2)`).
3. **Cálculo da Nota:** Baseado em Root(7). `36 + 7 = 43 (G2)`. *(Ignorou o D)*.
4. **Avaliação High Key:** Verifica se `Root(7) > HighKey(6)`. **VERDADEIRO**.
5. **Octave Drop:** `43 - 12 = 31`.
6. **Resultado MIDI Final:** **`G1 (31)`**. *(Muito grave e nota errada)*.

**Trace Yamaha (CORRETO):**
1. **Origem:** `C2 (36)`.
2. **Acorde Detectado:** `G/D` (Root = `G(7)`, Bass = `D(2)`).
3. **Cálculo da Nota:** Baseado em Bass(2). `36 + 2 = 38 (D2)`.
4. **Avaliação High Key:** Verifica se `Bass(2) > HighKey(6)`. **FALSO**.
5. **Octave Drop:** Não se aplica.
6. **Resultado MIDI Final:** **`D2 (38)`**. *(Nota correta, oitava correta)*.

---

## F. Outros Slash Chords (Impacto Estrutural)
Esta falha não ocorre apenas no `GM/D`. O mesmo erro afeta toda a estrutura de inversões.

| Acorde | Root | Bass | High Key | Nota Gerada (Erro Atual) | Nota Correta (Yamaha) | Avaliação Errada do HK |
|--------|------|------|----------|---------------------------|-----------------------|-------------------------|
| **C/G**| 0    | 7    | 6        | `C2 (36)`                 | `G1 (31)`             | Não cortou oitava do G. |
| **G/B**| 7    | 11   | 6        | `G1 (31)`                 | `B1 (35)`             | Tocou G em vez de B.    |
| **F/A**| 5    | 9    | 6        | `F2 (41)`                 | `A1 (33)`             | Tocou F agudo invés A grave. |
| **Am/E**| 9   | 4    | 6        | `A1 (33)`                 | `E2 (40)`             | Cortou A para grave, mas devia ser E. |

*Nota: Em todos os casos, a engine toca a nota da coluna Root em vez da coluna Bass, e corta a oitava nos momentos errados.*

---

## G. Causa Raiz
* **CONFIRMADA:** O problema da oitava no `GM/D` é diretamente causado pela checagem incorreta do *High Key* contra o `destRoot` em vez do `destBass` dentro de `BassRule`.
* **CONFIRMADA:** A nota errada (G em vez de D) deve-se ao abandono da variável `destBass` no cálculo de `newNote`.
* **CONFIRMADA:** A hipótese de que o `HighKey = 6` agrava o problema é verdadeira. Ele é fixo em `ArrangerEngine.kt` através do `dummyCasm`. O valor de `High Key` NÃO é universal; deve obrigatoriamente ser lido do chunk `Ctb2` do arquivo `.STY` para cada trilha individualmente.

---

## H. Correção Recomendada (NÃO IMPLEMENTADA)
Para consertar isso definitivamente no futuro:
1. **Em `YamahaRules.kt (BassRule)`:**
   - O cálculo `newNote` precisa mapear a nota alvo considerando o `destBass` (deslocamento e tabela) para a fundamental.
   - A condição de corte deve ser: `if (destBass > effectiveHighKey) newNote -= 12`.
2. **Em `ArrangerEngine.kt` (Engine do Arranger):**
   - Remover `dummyCasm`.
   - Ler o `HighKey`, `LowKey` e `Source Chord` reais do arquivo `.STY` através de um parser de CASM.

---

## I. Riscos da Implementação
* Alterar a regra do baixo cegamente pode danificar seções de **Intros e Endings**, onde a trilha de Baixo muitas vezes atua sob `NTT = BYPASS` ou `NTT = MELODY`, exigindo que as notas sigam o Root original. A correção precisará garantir que o `NTR/NTT` seja interpretado corretamente por compasso.
* Tracks de ritmo (Bateria) não devem ser submetidas a essa regra em hipótese alguma.

**STATUS:** Auditoria Completa. Nenhum arquivo foi modificado. Aguardando autorização para avançar com a implementação de correções, se desejado.
