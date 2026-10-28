# Arranger Pro - Teclado Arranjador Yamaha para Android

O **Arranger Pro** é uma aplicação completa de teclado arranjador profissional (estilo Yamaha / Korg / Roland) desenvolvida para Android em **Kotlin** com interface moderna em **Jetpack Compose** e motor de processamento MIDI / Áudio de alta precisão e ultra-baixa latência.

---

## 🎹 Principais Recursos

### 1. Motor Arranjador Yamaha (.STY e CASM)
- **Suporte Nativo a Styles Yamaha (.sty):** Leitura e reprodução de arquivos `.sty` padrão Yamaha com suporte aos formatos SFF1 e SFF2.
- **Estrutura de Seções Yamaha:**
  - **Intros:** Intro A, Intro B, Intro C, Intro D
  - **Main Variations:** Main A, Main B, Main C, Main D (com loops precisos por compasso)
  - **Fill-Ins:** Fill In AA, Fill In BB, Fill In CC, Fill In DD, Fill In AB/BA, Break
  - **Endings:** Ending A, Ending B, Ending C, Ending D
- **Motor CASM Completo (Chord Root & Transposição):**
  - Processamento em tempo real de regras **NTR** (Root Transpose / Root Fixed / Bypass) e **NTT** (Chord, Bass, Melody, Bypass).
  - Reconhecimento harmônico e remapeamento dinâmico de notas de acordo com os acordes tocados na mão esquerda.
  - Suporte a transição automática de Fill para a variação Main correspondente.

### 2. Style Creator (Criador e Editor de Ritmos)
- **Criação de Ritmos no Próprio App:** Gravação e edição de seções rítmicas com grid de compassos e quantização de notas.
- **Exportação .STY e CASM:** Geração precisa de arquivos `.sty` compatíveis com teclados Yamaha reais, gerando blocos MIDI e chunk CASM com marcações de seção oficiais.
- **Multi-Pistas:** Suporte às 8 pistas padrão do arranjador (Rhythm Sub, Rhythm Main, Bass, Chord 1, Chord 2, Pad, Phrase 1, Phrase 2 - Canais MIDI 9 a 16).

### 3. Gerenciamento de Sons e Sintetizador XG
- **Suporte a SoundFonts (.sf2):** Mecanismo de carregamento de soundfonts com mapeamento GM/XG (General MIDI e Yamaha XG).
- **MegaVoice & Drum Kit Tuning:** Controle granular de parâmetros de bateria (Pitch, Pan, Reverb, Chorus, Filtro e Volume via NRPN).
- **Vozes para Teclado (Upper/Lower/Split):** Configuração de divisão de teclado (Split Point) para acompanhamento na mão esquerda e solo na mão direita (Upper 1, Upper 2).

### 4. Conectividade MIDI Externa & USB
- **Suporte a Controladores USB/Bluetooth MIDI:** Conecte teclados controladores externos diretamente ao celular ou tablet.
- **Painel de Monitoramento MIDI:** Visualização em tempo real de mensagens Note On/Off, Control Change, Program Change e comandos de acompanhamento (Start/Stop, Synchro Start, Tap Tempo).
- **Low-Latency Scheduling:** Roteamento com sincronia de relógio (MIDI Clock) e tratamento de anti-jitter.

---

## 🛠️ Arquitetura Técnica

- **Linguagem:** Kotlin 100%
- **Interface:** Jetpack Compose (Material Design 3 adaptativo para celulares e tablets)
- **Áudio & MIDI:** Motor híbrido com Kotlin Coroutines / Flow para controle de eventos, integração JNI / C++ para síntese e renderização de baixa latência
- **Parser Yamaha:** Parsers dedicados para chunks `MThd`, `MTrk`, `CASM`, `CTB2`, `CSEG`, `CNTM` e tabelas de transposição harmônica
- **Persistência Local:** SQLite / Room para armazenamento de presets, configurações de kits e ritmos criados

---

## 📂 Estrutura do Projeto

```text
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/
│   │   │   │   ├── domain/arranger/      # Motor de execução do Arranjador e Style Creator
│   │   │   │   ├── domain/casm/          # Lógica de transposição CASM (NTR / NTT)
│   │   │   │   ├── domain/chord/         # Detecção de acordes e matriz harmônica
│   │   │   │   ├── domain/midi/          # Leitura, parsing e exportação MIDI / .STY
│   │   │   │   ├── domain/yamaha/        # Motores XG e parâmetros MegaVoice
│   │   │   │   └── ui/                   # Telas em Jetpack Compose (Arranjador, Criador, Mixer)
│   │   │   └── cpp/                      # Motores nativos C++ para áudio de baixa latência
└── tools/ / scripts/                     # Utilitários de análise de styles Yamaha e diagnósticos
```
