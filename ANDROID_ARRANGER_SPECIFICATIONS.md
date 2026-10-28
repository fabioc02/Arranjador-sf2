Este documento serve como o mapa arquitetural definitivo e roteiro de construção para o aplicativo "Live Groove Arranger". Ele deve ser fornecido a qualquer agente de IA para garantir a continuidade perfeita do projeto sem desvios de arquitetura.

## 1. Visão Geral e Restrições Críticas

O "Live Groove Arranger" é um aplicativo musical profissional para Android. Ele atua como um Sampler MPC (Máquina de Ritmos) e Arranjador de Estilos (Sequenciador de acompanhamentos).

**RESTRIÇÃO CRÍTICA DE ARQUITETURA:** 
O aplicativo **NÃO PODE** ser híbrido (React, WebView, Cordova, etc) ou Web. Aplicativos musicais profissionais exigem latência zero. A arquitetura **DEVE SER 100% NATIVA**, dividida estritamente entre Kotlin (Interface) e C++ (Motor de Áudio Oboe), conectados via JNI.

## 2. Divisão de Responsabilidades (Kotlin vs C++)

A linha que divide o que é feito em Kotlin e o que é feito em C++ é rígida.

### O que FICA em C++ (Motor de Áudio e Lógica Musical Pesada)
- Motor de áudio de baixa latência (Google Oboe).
- Reprodução de samples de áudio (Pads).
- Sintetizador de SoundFonts (SF2).
- Reprodução de Styles (Sequenciador de eventos MIDI).
- Reconhecimento de Acordes em tempo real.
- Efeitos DSP (Reverb, Delay, EQ).
- Mixer de áudio e roteamento.
- Gravação de eventos MIDI (Overdubbing/Quantização).

### O que FICA em Kotlin (UI e Sistema)
- Interface de Usuário (Jetpack Compose).
- Gerenciamento de Touch Screen (Multitouch para os Pads).
- Navegação entre telas (Mixer, Browser de Styles, Browser de Samples).
- Permissões do Android (Acesso a armazenamento, áudio).
- Leitura e gravação de arquivos de configuração (JSON).
- Activity e ciclo de vida do Android.

## 3. Estrutura de Arquivos e Módulos Implementada

A estrutura do projeto Gradle Android deve seguir o seguinte padrão:

```text
Android App
│
├── app/src/main/java/com/groovepad/arranger/ (KOTLIN UI)
│   ├── MainActivity.kt (Ponto de entrada, Jetpack Compose, Permissões)
│   ├── AudioEngine.kt (Classe Singleton com funções `external` (JNI))
│   └── ui/ (Telas em Jetpack Compose)
│       ├── MixerScreen.kt
│       ├── StyleBrowserScreen.kt
│       └── SampleBrowserScreen.kt
│
├── app/src/main/cpp/ (C++ ENGINE)
│   ├── CMakeLists.txt (Configuração de build do NDK linkando o Oboe)
│   ├── JNIWrapper.cpp (Ponte de comunicação Java <-> C++)
│   ├── AudioEngine.h / .cpp (Gerenciador do stream Oboe)
│   ├── Sequencer.h / .cpp (Sequenciador de eventos TPQN)
│   ├── Sampler.h / .cpp (Carregamento e reprodução de WAV/MP3)
│   ├── StylePlayer.h / .cpp (Lógica das variações Main A, Fill B, etc)
│   ├── Mixer.h / .cpp (Mixagem de canais e master bus)
│   ├── DSPEffects.h / .cpp (Reverb, Delay)
│   ├── MidiEngine.h / .cpp (Recepção de eventos MIDI externos)
│   └── ChordRecognition.h / .cpp (Análise de acordes tocados)
```

## 4. Próximos Passos (Para continuar no novo projeto)

Para o agente de IA que for continuar este projeto:

1. **Replicar a Estrutura:** Configure o `build.gradle.kts` e o `CMakeLists.txt` exatamente como desenhado para suportar o NDK e o Oboe.
2. **Implementar o JNI Completo:** Atualmente temos a casca do JNI. O próximo passo é passar os buffers de strings (caminhos de arquivos WAV/SF2) do Kotlin para o C++ de forma segura, para o `Sampler.cpp` e `StylePlayer.cpp` começarem a ler áudio real do disco.
3. **Multitouch nos Pads:** Substituir o `.clickable` simples do Jetpack Compose pelo `pointerInput` avançado para capturar eventos de `ACTION_DOWN` e `ACTION_UP` simultâneos em múltiplos Pads, enviando imediatamente o index e velocity via JNI para o `AudioEngine::playPad`.
4. **Motor de Renderização de Áudio:** No `AudioEngine::onAudioReady`, implementar o loop de mixagem onde o `Mixer.cpp` coleta os frames gerados pelo `Sampler.cpp` e `StylePlayer.cpp`, aplica os `DSPEffects`, e escreve no buffer do Oboe.

## 5. Instruções de Compilação (Export to ZIP)
Lembrete para o usuário: Como o ambiente do AI Studio foca em Web, a compilação final deste app nativo se dá baixando o ZIP e rodando em uma máquina local com:
`export ANDROID_HOME=/caminho/do/sdk` e rodando `gradle assembleDebug` ou abrindo diretamente no Android Studio.
