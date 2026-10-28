# Relatório de Desacoplamento da Fórmula de Compasso e Arquitetura Musical

## 1. Limitações Encontradas Atualmente
A interface de usuário (UI) possui as seguintes restrições implementadas com `OutlinedButton` onde o estado interno cicla ("hardcoded") por valores predefinidos através de lógica condicional `if/else`:
- **Time Signature:** Somente permite `3/4`, `4/4` e `6/8`. O botão da interface cicla especificamente entre `{Numerator: 4, Denominator: 4} -> {3, 4} -> {6, 8}`.
- **Measures (Extensão do Pattern):** Somente permite `1`, `2`, `4` e `8` compassos.
- **Quantization:** Apresentado no código pela variável `quantizeResolution`. O botão cicla apenas entre os valores `4 (1/16)`, `8 (1/32)` e `12 (Triplets)`.

## 2. Quais vieram do manual do PSR-S750
O Yamaha PSR-S750, como um hardware comercial com sua própria tela e OS interno limitados, normalmente dispõe dessas exatas restrições na aba *Style Creator*. A quantização principal, fórmulas de compassos (majoritariamente 4/4 e 3/4) e as opções de limites de Length em `1, 2, 4, 8` são os presets do hardware. 

## 3. Quais são limitações artificiais do aplicativo
No contexto deste aplicativo - que deve agir como um "Arranger Profissional" genérico - **TODAS** as limitações citadas no item 1 são superficiais e artificiais.
A Engine Subjacente (O Relógio, a Timeline e o MIDI Router) já baseia seus laços nos valores declarados pelo `MidiRhythmState`. O Motor **não sabe** e não precisa saber que a UI limita a edição para 4/4. A restrição não existe na capacidade do Kotlin.

## 4. Hardcodes Encontrados
- **UI de Fração de Compasso:** `if (MidiRhythmState.timeSigNumerator == 4) 3 else if (MidiRhythmState.timeSigNumerator == 3) 6 else 4` (Linha 478, MidiRhythmCreator.kt)
- **UI de Medidas:** `if (MidiRhythmState.measures == 1) 2 else if (MidiRhythmState.measures == 2) 4 else if (MidiRhythmState.measures == 4) 8 else 1` (Linha 482)
- **UI de Quantização:** `if (MidiRhythmState.quantizeResolution == 4) 8 else if (MidiRhythmState.quantizeResolution == 8) 12 else 4` (Linha 486)
- **Cálculo de Ticks Matemático (Bug anterior):** `ppq / (denominator / 4)` utilizava matemática falha (agora corrigida).

## 5. Componentes Afetados
- `MidiRhythmCreator.kt` (Somente as Actions dos Botões da Interface, que são os únicos "gargalos").
- O Relógio Musical (`totalTicks`, `ticksPerBeat`) e a Timeline já calculam sobre o Estado e **não são** afetados; reagem naturalmente a qualquer Time Signature injetada. 
- Metrônomo: Calcula sua batida através do módulo `currentTick % ticksPerBeat == 0` (O que prova que a lógica já está saudável e generalizada).

## 6. Arquitetura Proposta
- **Internal Musical Model Genérico:** Manter `TimeSignature`, `PatternLength`, e `Quantization` totalmente dinâmicos nas classes Kotlin. Em uma futura iteração de interface, substituir a seleção via "botão de ciclar" por `DropdownMenus` ou um "Number Picker" livre que permita injetar, por exemplo, `11/8`, `5/4` e 16 measures. 
- **Time Signature & Quantization Isolados:** A Quantization (`quantizeResolution`) nunca afetará o tamanho do compasso, servindo puramente de filtro divisório matemático ao interceptar um `Note-On` na hora de gravá-lo no vetor interno.

## 7. Testes Realizados
Foram escritos testes rigorosos na suíte `RhythmCreatorTest.kt` testando permutações independentes:
- `2/4, 3/4, 4/4, 5/4, 6/4, 7/4, 9/4, 12/4`
- `2/8, 3/8, 5/8, 6/8, 7/8, 9/8, 11/8, 12/8`
- `2/2, 3/2, 4/2`
Cruzados com Lengths de `1 a 64 Measures`.
O sistema superou a bateria sem estourar memórias ou exibir *ArithmeticExceptions*. Os cálculos de Tick se mostraram imunes às restrições do Yamaha.

## 8. Resultado do BUILD
`BUILD SUCCESSFUL` (Confirmado por Gradle Task Assembly e Testes Unitários de Matemática do Relógio).

## 9. Riscos de Compatibilidade Yamaha
**Atenção Crítica:** Quando a função Exportar Yamaha (CASM/STY) for implementada, se o usuário criar um ritmo em `11/8`, o formato nativo da Yamaha pode rejeitar o Style no momento do Parse no hardware, exibindo "Data Error" na tela do teclado PSR-S750. 
O aplicativo será potente, mas o hardare final rejeitará compassos complexos caso o formato binário Yamaha STY não suporte essa metadata na tabela CASM/SFF.

## 10. O que deve permanecer limitado à camada de exportação Yamaha
- Se o formato `.STY` (via YAMAHA Format Specification) suportar apenas subdivisões estritas e `Length` máximo de 32 compassos (exemplo fictício), o Exporter é quem precisará interceder.
- O exporter deve possuir uma lógica (warning) que avise o usuário: *"Este compasso personalizado (5/4) será exportado, mas o hardware S750 pode se recusar a lê-lo corretamente."*

## 11. O que deve permanecer ilimitado no motor interno
- Toda a lógica de `ticksPerBeat`, Metrônomo e a Grade de Quantização do Rhythm Creator. O Playback e o Sequencer do Android App são e devem continuar sendo livres.
