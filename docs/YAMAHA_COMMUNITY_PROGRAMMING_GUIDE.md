# Yamaha Keyboards Programming - CASM, CSEG, Ctab, Cntt & OTS Specs
*(Documentação extraída de Jørgen Sørensen / Jososoft - Yamaha Keyboards Programming 2024)*

## 1. CASM Section Format 1 (SFF1)

O bloco `CASM` é o primeiro chunk não-MIDI a seguir os dados MIDI em um arquivo `.sty`.

### Estrutura Hierárquica:
- **CASM**: Cabeçalho do bloco (`43 41 53 4D`), seguido do tamanho de 4 bytes (Big Endian).
  - **CSEG**: Seção de segmento (`43 53 45 47`), seguido de 4 bytes de tamanho. Agrupa seções com regras comuns.
    - **Sdec**: Declaração de partes (`53 64 65 63`), seguido de 4 bytes de tamanho. Contém nomes em ASCII separados por vírgula (ex: `Main A,Main B,Fill In AA`).
    - **Ctab**: Tabela de Canal (`43 74 61 62`), seguido de 4 bytes de tamanho.
      - Offset 00: Source Channel (`00 - 0F` = canais 1 a 16).
      - Offset 01..08: Voice Name (8 bytes ASCII).
      - Offset 09: Destination Channel (`08 - 0F` = canais 9 a 16).
      - Offset 10: Editable (`00` = editável, `01` = read-only).
      - Offset 11..12: Note Mute (Mapeamento de 12 notas para mutar canais em acordes específicos).
      - Offset 13..17: Chord Mute (5 bytes - máscaras de acordes como Maj, Maj6, Maj7, m, m7, dim, aug, etc.).
      - Offset 18: Source Chord Root (`00 - 0B` -> C a B).
      - Offset 19: Source Chord Type (`00 - 21` -> Maj, Maj6, Maj7, etc. Padrão: CM7).
      - Offset 20: NTR (Note Transposition Rule):
        - `00`: ROOT TRANS (Mantém intervalo relativo à fundamental).
        - `01`: ROOT FIXED (Mantém notas próximas da região original / condução de vozes para acordes).
        - `02`: GUITAR (Mapeamento para afinação das 6 cordas de violão/guitarra).
      - Offset 21: NTT (Note Transposition Table):
        - `00`: BYPASS
        - `01`: MELODY
        - `02`: CHORD
        - `03`: BASS
        - `04`: MELODIC MINOR
        - `05`: HARMONIC MINOR
      - Offset 22: High Key (`00 - 0B` -> Limite superior de transposição da raiz; notas acima descem 1 oitava).
      - Offset 23: Note Low Limit (`00 - 7F`).
      - Offset 24: Note High Limit (`00 - 7F`).
      - Offset 25: RTR (Retrigger Rule):
        - `00`: STOP
        - `01`: PITCH SHIFT
        - `02`: PITCH SHIFT TO ROOT
        - `03`: RETRIGGER
        - `04`: RETRIGGER TO ROOT
        - `05`: NOTE GENERATOR
      - Offset 26: End Marker (`00`).
    - **Cntt**: Extensão de NTT para SFF1 (`43 6E 74 74`):
      - Offset 00: Source Channel (`00 - 0F`).
      - Offset 01: NTT com Bass On/Off:
        - `80`: BYPASS (Bass On)
        - `81`: MELODY + BASS (Bass On)
        - `82`: CHORD (Bass On)
        - `83`: MELODIC MINOR (Bass On)
        - `04 / 84`: MELODIC MINOR 5th VAR (Bass Off / On)
        - `85`: HARMONIC MINOR (Bass On)
        - `06 / 86`: HARMONIC MINOR 5th VAR (Bass Off / On)
        - `07 / 87`: NATURAL MINOR (Bass Off / On)
        - `08 / 88`: NATURAL MINOR 5th VAR (Bass Off / On)
        - `09 / 89`: DORIAN (Bass Off / On)
        - `0A / 8A`: DORIAN 5th VAR (Bass Off / On)

---

## 2. CASM Section Format 2 (SFF2)

No SFF2 (introduzido a partir do Tyros 3):
- Permite divisão em 3 regiões de notas: Low, Middle e High notes.
- Byte 22: Low/Middle limit.
- Byte 23: Middle/High limit.
- Repete as regras de transposição (NTR, NTT, High Key, Low Limit, High Limit, RTR) para as 3 seções.
- Suporte a afinação e voice-leading de guitarra:
  - B -> 1ª corda (E agudo)
  - A -> 2ª corda (B)
  - G -> 3ª corda (G)
  - F -> 4ª corda (D)
  - E -> 5ª corda (A)
  - D -> 6ª corda (E grave)
  - C# -> Quinta acima/abaixo
  - C -> Nota fundamental (root note)

---

## 3. OTS (One Touch Setting)

- Identificador `OTSc` seguido de tamanho de 4 bytes.
- Contém 4 sub-tracks MIDI padrão `MTrk` correspondentes aos botões **OTS 1, OTS 2, OTS 3 e OTS 4**.
- Configura timbres e efeitos para as vozes Right 1, Right 2, Right 3 e Left via comandos SysEx:
  - Formato: `F0 43 73 01 50 ... F7`
  - Controla: Voice volume, Octave set, DSP effects, Left Hold, Harmony/Arpeggio.
