# Lógica de Transposição do Baixo em Estilos Yamaha

## 1. O que é o CASM
`CASM` = **C**hord **A**ssembly **S**ystem **M**odule.
Guarda instruções de como cada canal do estilo deve ser transposto quando o acorde muda.

## 2. NTR e NTT
**NTR** = **N**ote **T**ransposition **R**ule.
Define como a nota original será movida para a nota destino.
- 0x00: Root Fixed
- 0x02: Bass
- 0x03: Bypass
- 0x04: Root Trans

**NTT** = **N**ote **T**ransposition **T**able.
Define para quais notas a transposição pode ir.
- 0x00: Bypass
- 0x02: Chord
- 0x05 / 0x08: Bass Table

## 3. Tabela de baixo Yamaha (Major)
Para acorde maior de destino:
| Nota original em relação ao C original | Intervalo origem | Nota destino em relação ao acorde destino | Intervalo destino |
|---|---|---|---|
| C  | 0  | Fundamental | 0 |
| C# | 1  | Fundamental | 0 |
| D  | 2  | 9ª maior    | 2 |
| D# | 3  | 3ª maior    | 4 |
| E  | 4  | 3ª maior    | 4 |
| F  | 5  | 4ª justa    | 5 |
| F# | 6  | 5ª justa    | 7 |
| G  | 7  | 5ª justa    | 7 |
| G# | 8  | 6ª maior    | 9 |
| A  | 9  | 6ª maior    | 9 |
| A# | 10 | 7ª menor    | 10 |
| B  | 11 | 7ª maior    | 11 |

bassTableMajor = [0, 0, 2, 4, 4, 5, 7, 7, 9, 9, 10, 11]

## 4. Implementação C++ SFF
A lógica base de Bypass vs Chord vs Bass deve ser implementada no motor de arranjador C++.
