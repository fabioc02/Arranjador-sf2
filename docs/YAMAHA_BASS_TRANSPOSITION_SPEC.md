# Lógica de Transposição do Baixo em Estilos Yamaha

## 1. Contexto e Objetivo

**Contexto:** Análise de arquivos `.STY` (SFF1) da Yamaha para reproduzir, em software, o comportamento de um arranjador Yamaha.

**Objetivo:** Entender e implementar corretamente a transposição de baixo a partir dos chunks:
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
Chunk que guarda instruções de como cada canal do estilo deve ser transposto quando o acorde muda.

Estrutura:
```text
CASM
  ├── CSEG (um ou mais)
  │     ├── Sdec  (lista de seções que este segmento cobre)
  │     └── Ctb2  (uma entrada por canal, com NTR/NTT/limites)
  └── ...
```

Exemplo real:
```text
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

**Ponto crítico:** um mesmo canal pode ter regras diferentes em CSEGs diferentes. O player deve escolher o `Ctb2` correto baseado na **seção ativa**.

---

## 3. NTR — Note Transposition Rule

Define **como** a nota original é movida para a nota destino.

| NTR  | Nome       | Comportamento |
|------|------------|---------------|
| 0x00 | Root Trans | Transpõe pela diferença entre fundamental de origem e destino (intervalo) |
| 0x01 | Guitar / Root Fixed | Voice leading específico de guitarra / Chordal Root Fixed |
| 0x02 | Root Fixed / Guitar | Nota raiz fixa; notas próximas seguem a escala |
| 0x03 | Bypass     | Não transpõe; toca a nota MIDI gravada |

---

## 4. NTT — Note Transposition Table

Define **para quais notas** a transposição pode ir.

| NTT  | Nome              | Aplicação principal |
|------|-------------------|---------------------|
| 0x00 | Melody / Bypass   | Bypass ou Voice leading melódico |
| 0x01 | Melody            | Voice leading melódico (Phrase 1/2) |
| 0x02 | Chord             | Força notas do acorde (Chord 1/2, Bass "pedal") |
| 0x03 | Melodic Minor     | Escala menor melódica |
| 0x04 | Melodic Major     | Escala maior melódica |
| 0x05 | Natural Minor     | Escala menor natural |
| 0x06 | Harmonic Minor    | Escala menor harmônica |
| 0x07 | Dorian            | Modo dórico |
| 0x08 | **Bass**          | **Bass Table** (walking bass em Intro/Ending) |
| 0x09 | Mixolydian        | Modo mixolídio |
| 0x0A | Pentatonic        | Escala pentatônica |

---

## 5. Tabela de Bass (aplicada quando NTT=0x08)

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

```kotlin
val bassTableMajor = intArrayOf(0, 0, 2, 4, 4, 5, 7, 7, 9, 9, 10, 11)
```

---

## 6. Estrutura do bloco Ctb2 (SFF1)

| Offset | Campo               | Tamanho | Descrição |
|--------|---------------------|---------|-----------|
| 0      | Source Channel      | 1       | Canal MIDI de origem (0x00–0x0F) |
| 1..16  | Part Name           | 16      | Nome ASCII (padding com `\x00`) |
| 17     | Destination Channel | 1       | Canal MIDI de destino |
| 18     | NTR                 | 1       | Note Transposition Rule |
| 19     | NTT                 | 1       | Note Transposition Table |
| 20     | High Key            | 1       | Limite superior do range |
| 21     | Low Key             | 1       | Limite inferior do range |
| 22     | RTR                 | 1       | Retrigger Rule |
