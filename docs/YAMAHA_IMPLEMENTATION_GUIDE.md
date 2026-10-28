# Guia de Implementação do Arranjador Yamaha (Android / Kotlin)

Este documento descreve como implementar a lógica de reprodução de estilos (SFF) em um motor de arranjador Kotlin para Android.

## 1. Classes do CASM e Parsing
Você deve ter uma classe `CasmParser.kt` que lê o bloco `CASM` do arquivo `.sty`.
A classe deve extrair as tabelas de `NTR` e `NTT` para cada seção e canal MIDI.

## 2. Motor de Reprodução (Transposer)
O motor de transposição deve observar a seção atual tocando e o acorde reconhecido na mão esquerda.
* **NTT = 00 (Bypass):** A nota é transposta de forma paralela.
* **NTT = 02 (Chord/Melody):** As notas MIDI do estilo são forçadas a mapear para as notas do acorde tocado.
* **NTT = 08 (Bass Table):** Usado para o Baixo e melodias caminhantes.

## 3. Pulo de Tabela por Seção
Durante um `Main A/B`, o canal 10 (Bass) usa `NTR=02/00` e `NTT=02/00`.
Em Intros/Endings, canais fantasmas (ex: 02) assumem o baixo com `NTT=08` e o principal entra em Bypass.

## 4. Local Control Off
Enviar CC# 122 com valor 0 para evitar que o teclado físico toque notas sobrepostas.
