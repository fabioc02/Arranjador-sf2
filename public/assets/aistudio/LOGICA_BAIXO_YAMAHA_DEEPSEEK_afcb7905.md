# Lógica de Transposição do Baixo em Estilos Yamaha

## 1. Contexto e Objetivo

**Contexto:** Análise de arquivos `.STY` (SFF1) da Yamaha para reproduzir, em
software, o comportamento de um arranjador Yamaha.

**Objetivo:** Entender e implementar corretamente a transposição de baixo a
partir dos chunks:

- `CASM` (Chord Assembly System Module)
- `CSEG` (CASM Segment)
- `Sdec` (Section Declaration)
- `Ctb2` (Channel Table)

E dos campos:

- `NTR` (Note Transposition Rule)
- `NTT` (Note Transposition Table)

**Regras fundamentais:**

1. O padrão de estilo é gravado em uma tonalidade de referência, normalmente `CMaj7`.
2. Durante a execução, o teclado transpõe o padrão para o acorde tocado.
3. Cada canal tem regra própria definida por `NTR` + `NTT`.
4. O comportamento do baixo **varia por seção** (Main/Fill ≠ Intro/Ending).
5. O mapeamento respeita limites `High Key` e `Low Key` do `Ctb2`.

---

## 2. O que é o CASM

`CASM` = **Chord Assembly System Module**.

Chunk que guarda instruções de como cada canal do estilo deve ser transposto
quando o acorde muda.

Estrutura:

```
CASM
  ├── CSEG (um ou mais)
  │     ├── Sdec  (lista de seções que este segmento cobre)
  │     └── Ctb2  (uma entrada por canal, com NTR/NTT/limites)
  └── ...
```

Exemplo real:

```
CASM
  CSEG 1
    Sdec: "Main A,Main B,Main C,Main D,Fill In AA,..."
    Ctb2: Brasil K   (ch 08)
    Ctb2: PowerKit   (ch 09)
    Ctb2: EBass      (ch 10)
    Ctb2: steelgt    (ch 11)
    ...
  CSEG 2
    Sdec: "Intro B,Ending B"
    Ctb2: Bass       (ch 02)
    Ctb2: Chord1     (ch 03)
    ...
```

**Ponto crítico:** um mesmo canal pode ter regras diferentes em CSEGs diferentes.
O player deve escolher o `Ctb2` correto baseado na **seção ativa**.

---

## 3. NTR — Note Transposition Rule

Define **como** a nota original é movida para a nota destino.

| NTR  | Nome       | Comportamento                                                                 |
|------|------------|-------------------------------------------------------------------------------|
| 0x00 | Root Trans | Transpõe pela diferença entre fundamental de origem e destino (intervalo)     |
| 0x01 | Guitar     | Voice leading específico de guitarra/violão                                   |
| 0x02 | Root Fixed | Nota raiz fixa; notas próximas seguem a escala                                |
| 0x03 | Bypass     | Não transpõe; toca a nota MIDI gravada                                        |

**Validação empírica:** nos 34 estilos analisados, **100% dos canais de baixo
usaram NTR = 0x00 (Root Trans)**. NTR = 0x02 (Root Fixed) é comum em guitarras
e cordas.

---

## 4. NTT — Note Transposition Table

Define **para quais notas** a transposição pode ir.

| NTT  | Nome              | Aplicação principal                              |
|------|-------------------|--------------------------------------------------|
| 0x00 | Melody            | Voice leading melódico (Phrase 1/2)              |
| 0x01 | Melody (alt)      | Variante melódica                                |
| 0x02 | Chord             | Força notas do acorde (Chord 1/2, Bass "pedal")  |
| 0x03 | Melodic Minor     | Escala menor melódica                            |
| 0x04 | Melodic Major     | Escala maior melódica                            |
| 0x05 | Natural Minor     | Escala menor natural                             |
| 0x06 | Harmonic Minor    | Escala menor harmônica                           |
| 0x07 | Dorian            | Modo dórico                                      |
| 0x08 | **Bass**          | **Bass Table** (walking bass em Intro/Ending)    |
| 0x09 | Mixolydian        | Modo mixolídio                                   |
| 0x0A | Pentatonic        | Escala pentatônica                               |

**Nota de nomenclatura (Jososoft / comunidade Yamaha):** o valor `0x08` também é
referenciado como `NATURAL MINOR 5th + Bass On`. Nos `.sty` SFF1, o campo NTT
armazena o valor **já com o bit de Bass On aplicado**, então `0x08` é o que
aparece nos arquivos quando a Bass Table está ativa.

---

## 5. CSEG — CASM Segment

Agrupa:

- uma lista de seções (`Sdec`)
- uma coleção de canais (`Ctb2`)

Um mesmo `.sty` normalmente tem **3 a 6 CSEGs**, cada um cobrindo um conjunto
diferente de seções:

```
CSEG 1 → Main A, Main B, Main C, Main D, Fill In AA..DD, Intro A, Ending A
CSEG 2 → Intro B, Ending B
CSEG 3 → Intro C, Ending C
```

---

## 6. Ctb2 — Channel Table

Tabela de configuração individual de cada canal dentro de um CSEG.

Layout validado em SFF1 (offsets relativos ao payload do bloco Ctb2):

| Offset | Campo               | Tamanho | Descrição                              |
|--------|---------------------|---------|----------------------------------------|
| 0      | Source Channel      | 1       | Canal MIDI de origem (0x00–0x0F)       |
| 1..16  | Part Name           | 16      | Nome ASCII (padding com `\x00`)        |
| 17     | Destination Channel | 1       | Canal MIDI de destino                  |
| 18     | NTR                 | 1       | Note Transposition Rule                |
| 19     | NTT                 | 1       | Note Transposition Table               |
| 20     | High Key            | 1       | Limite superior do range               |
| 21     | Low Key             | 1       | Limite inferior do range               |
| 22     | RTR                 | 1       | Retrigger Rule                         |
| 23+    | (reservado)         | —       | Note limits / velocity limits          |

---

## 7. Sdec — Section Declaration

Lista textual das seções cobertas por aquele CSEG.

Exemplo:

```
Sdec: Main A,Main B,Main C,Main D,Fill In AA,Fill In BB,Fill In CC,
      Fill In DD,Intro A,Ending A,Fill In BA
```

---

## 8. Como o teclado usa

**Parsing (ao carregar o `.sty`):**

1. Lê o arquivo `.STY`.
2. Interpreta as tracks MIDI.
3. Localiza chunk `CASM`.
4. Lê cada `CSEG`.
5. Lê `Sdec` para saber quais seções o CSEG cobre.
6. Lê cada `Ctb2` (uma por canal).

**Execução (durante a performance):**

1. Identifica o acorde tocado na mão esquerda.
2. Identifica a seção ativa (Main A/B/C/D, Fill, Intro, Ending).
3. Seleciona o `Ctb2` correspondente (do CSEG que cobre aquela seção).
4. Aplica `NTR` + `NTT` na nota original.
5. Aplica limites `High Key` / `Low Key`.
6. Envia nota MIDI transposta ao gerador de som.

---

## 9. Lógica correta do baixo

O comportamento do baixo **não é uniforme**. Ele muda conforme a **seção**:

```
Main A/B/C/D e Fill In:
  Ch 10: NTR=00, NTT=02 (Chord) → baixo "pedal" (notas do acorde)
  (Alguns estilos também usam Ch 02 com NTT=02 simultaneamente)

Intro B/C e Ending B/C:
  Ch 02: NTR=00, NTT=08 (Bass)   → walking bass com Bass Table
  Ch 10: NTR=00, NTT=00 (Melody) → contraponto melódico
```

**Conclusão para o player:** não basta uma regra global. É preciso:

1. Ler o `CSEG` da seção ativa.
2. Mapear o `Ctb2` do canal.
3. Aplicar a transposição conforme o `NTT` daquele `Ctb2`.

---

## 10. Tabela de Bass (aplicada quando NTT=0x08)

Para acorde **maior** de destino, a Bass Table da Yamaha é:

| Nota original (relativa ao C) | Intervalo | Nota destino (relativa ao acorde) | Intervalo |
|-------------------------------|-----------|-----------------------------------|-----------|
| C                             | 0         | Fundamental                       | 0         |
| C#                            | 1         | Fundamental                       | 0         |
| D                             | 2         | 9ª maior                          | 2         |
| D#                            | 3         | 3ª maior                          | 4         |
| E                             | 4         | 3ª maior                          | 4         |
| F                             | 5         | 4ª justa                          | 5         |
| F#                            | 6         | 5ª justa                          | 7         |
| G                             | 7         | 5ª justa                          | 7         |
| G#                            | 8         | 6ª maior                          | 9         |
| A                             | 9         | 6ª maior                          | 9         |
| A#                            | 10        | 7ª menor                          | 10        |
| B                             | 11        | 7ª maior                          | 11        |

```
bassTableMajor = [0, 0, 2, 4, 4, 5, 7, 7, 9, 9, 10, 11]
```

**Importante:** esta tabela **não está armazenada no `.sty`**. Vive no firmware
do teclado. O `.sty` só guarda `NTT=0x08` como referência.

---

## 11. Exemplo — C → F

Estilo gravado em C. Notas originais:

| Nota | MIDI | Semitom relativo a C |
|------|------|----------------------|
| C2   | 36   | 0                    |
| E2   | 40   | 4                    |
| G2   | 43   | 7                    |
| A2   | 45   | 9                    |
| B2   | 47   | 11                   |

Acorde destino: **F** (rootKey = 5)

| Nota original | Intervalo origem | Tabela destino | Nota destino | MIDI |
|---------------|------------------|----------------|--------------|------|
| C2            | 0                | 0              | F2           | 41   |
| E2            | 4                | 4              | A2           | 45   |
| G2            | 7                | 7              | C3           | 48   |
| A2            | 9                | 9              | D3           | 50   |
| B2            | 11               | 11             | E3           | 52   |

Resultado:

```
F2  A2  C3  D3  E3
```

---

## 12. O que o código atual (mock) fazia

```
F2  A2  C3  C3  C3
```

As notas `A` e `B` viravam `C`, descaracterizando o groove. Erro causado por
voice leading ingênuo + não uso da Bass Table.

---

## 13. Plano de implementação

1. **Parser real** de `CASM`, `CSEG`, `Sdec`, `Ctb2` (não mock).
2. **Extração completa** dos campos do `Ctb2` (NTR, NTT, High, Low, RTR).
3. **Associação seção → CSEG → Ctb2** para saber qual regra aplicar.
4. **Corrigir `BassRule`** para usar Bass Table quando `NTT=0x08`.
5. **Corrigir `ChordTransposer`** para aplicar `NTT=0x02` corretamente.
6. **Aplicar limites** `High Key` / `Low Key` do Ctb2.
7. **Validar** com estilos reais (comparação empírica).

---

## 14. Estrutura do bloco Ctb2 (validada empiricamente)

Extraído via parser Python em 34 arquivos SFF1:

1. `payload[0]` → Source Channel
2. `payload[1:17]` → Part Name (16 bytes, padding `\x00`)
3. `payload[17]` → Destination Channel
4. `payload[18]` → **NTR**
5. `payload[19]` → **NTT**
6. `payload[20]` → High Key
7. `payload[21]` → Low Key
8. `payload[22]` → RTR

**Exemplo real (`AcusticoBR1.sty`, canal 10 — EBass, Main):**

```
src=0x0A dst=0xFF NTR=0x00 NTT=0x02 high=0x00 low=0x5F rtr=0x00
```

Tradução: canal 10, Root Trans, Chord, limites 0x00–0x5F.

**Exemplo real (`BachataSertaneja1.sty`, canal 02 — EBass, Intro B):**

```
src=0x02 dst=0x00 NTR=0x00 NTT=0x08 high=0x00 low=0x5F rtr=0x00
```

Tradução: canal 02, Root Trans, **Bass Table**, limites 0x00–0x5F.

---

## 15. Validação empírica — 34 estilos SFF1

Estilos analisados: MPB, Bossa Nova, Samba/Pagode, Brega, Sertanejo, Gospel,
Pop, Rock BR, Norte, Sul.

**Resultado consolidado:**

| Métrica                                 | Valor |
|-----------------------------------------|-------|
| Canais de baixo analisados              | 100+  |
| Canais com NTR = 0x00 (Root Trans)      | 100%  |
| Main/Fill com NTT = 0x02 (Chord)        | 100%  |
| Intro/Ending com NTT = 0x08 (Bass Table)| ~95%  |
| Intro/Ending com Ch 10 em NTT = 0x00    | ~95%  |
| Estilos sem Bass Table em Intro/Ending  | ~5% (ex: AcusticoBR1) |

**Padrão observado nas Intros/Endings com walking bass:**

```
Ch 02 → NTT=0x08 (Bass Table)
Ch 10 → NTT=0x00 (Melody)
```

Isso é consistente em `AdoraBrasil`, `BachataSertaneja1/4/5`, `Brega60s`,
`BregaBolero`, `BregaBrasil`, `BregaRomantico`, `CanelaBR`, `CapitalBrownRock`,
`Carimbó`, `GarotaPopBR`, `GospelGrooveBR`, `Guitarrada`, `LambadaModerna`,
`LevadaPop`, `LouvorSoulBR`, `MPB Groove`, `MPBsweetVibes`, `PopFolkBR`,
`PopGrooveBR`, `PopMPB`, `PopReggaeBR`, `Rock BR 80s`, `RockPop BR`,
`RomanticRockBR`, `Seu JSwing`, `Skankley Rock`, `TechnoMelodyBR`, `WorshipBasil`.

**Exceção:** `AcusticoBR1.sty` mantém `NTT=0x02` no baixo mesmo em Intros/Endings.

---

## 16. Conclusão para o App

O Player do arranjador **não pode** usar a mesma regra para toda a música.
Deve:

1. Ler o `CSEG` da **seção atual**.
2. Mapear o `Ctb2` correspondente ao canal.
3. Checar se o `NTT` daquele Ctb2 exige `Chord` (0x02), `Melody` (0x00) ou
   `Bass Table` (0x08).
4. Aplicar a transposição correta.
5. Aplicar limites `High Key` / `Low Key`.
6. Enviar ao gerador de som.

Sem isso, o baixo soa errado nas Intros/Endings (vira "pedal" monótono em vez
de walking bass).
