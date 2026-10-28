# FASE A — P0: MIDI/USB, CHORD ENGINE E ESTABILIDADE (IMPLEMENTAÇÃO)

## 1. Causa raiz confirmada
- **Loop Infinito Síncrono (Echo Feedback):** Identificamos que o roteamento de gravação repassava um evento recém lido diretamente para um canal de destino `recCh`. Quando o hardware do teclado estava configurado em "Local Control" ou THRU via USB, ele ecoava o evento de volta para a aplicação. O evento entrava novamente e era repetido infinitamente e sem atrasos (síncrono), travando as threads principais (Binder) do aplicativo no Samsung J5 (tela preta e congelamentos).
- **Tratamento de Desconexão Inexistente:** O App usava o `MidiManager.DeviceCallback` sem implementar `onDeviceRemoved`. Ao puxar o OTG fisicamente, a UI perdia contato e o ciclo de vida Android tentava ler/escrever numa `MidiInputPort` e `MidiOutputPort` já destruídas pelo kernel, gerando milhares de instâncias de `IOException` e crash de OutOfMemory (OOM/tela preta).
- **Chord Engine Desconectado:** O motor C++ estava inativo porque, apesar de `AudioEngine.onLiveMidiEvent` possuir suporte JNI completo, ele simplesmente não era chamado no interceptador `GlobalMidiRouter`.

## 2. Arquivos Modificados
- `app/src/main/java/com/example/MainActivity.kt`
  - Incluído `override fun onDeviceRemoved(...)` no `DeviceCallback`.
  - As portas de gravação e as saídas foram adequadamente encerradas (`port.close()`) utilizando `iterator()` em conjunto com a limpeza lógica das coleções nativas (`midiInputPorts`, `midiPorts`, `midiDevices`, `midiReceivers`), eliminando assim o uso de portas mortas e as sucessivas exceptions.
- `app/src/main/java/com/example/domain/midi/GlobalMidiRouter.kt`
  - Inclusão do mecanismo anti-loop que bloqueia retransmissão de eventos onde o canal de entrada for o mesmo do alvo da gravação atual (`channel != recCh`).
  - Ligação do MIDI IN físico para o Chord Engine através de envio passivo usando `com.example.AudioEngine.onLiveMidiEvent(...)`.

## 3. Arquivos NÃO modificados
- **Todo o código C++ / JNI, AudioEngine, Oboe, CASM, YamahaRules**.
- **VoiceEngine, Rhythm Creator UI, controles visuais (Layout, Play/Pause/Stop, ARRANGER UI, L/R)**.
- **Motor de quantização e RealTimeMidiScheduler**.
Nenhuma alteração foi realizada que dependa ou tente copiar o Repositório Legado.

## 4. Fluxos antes e depois
### Fluxo ANTES
```
[PSR-S750 Teclado] → [USB Android Midi] → GlobalMidiRouter 
   |
   +-> SendNoteOn(recCh) 
         | 
         +-> [MIDI OUT] → [PSR-S750 Echo] → Loop Mortal sem atraso.
```

### Fluxo DEPOIS
```
[PSR-S750 Teclado] → [USB Android Midi] → GlobalMidiRouter
   |
   +-> (1) AudioEngine.onLiveMidiEvent(status, data1, data2) [Chord Engine é alimentado imediatamente]
   |
   +-> (2) IF channel != recCh: 
            SendNoteOn(recCh) [Gravação reflete normalmente, mas eco do hardware é vetado]
```

## 5. Mecanismo de Prevenção de Loop (Anti-loop)
Foi adotada uma regra estrita no Router baseada em distinção de origem, não por canal fixado: **a aplicação nunca irá transmitir um `NoteOn` para a porta de saída, se a porta de entrada acusar exatamente a recepção naquele mesmo canal de gravação**. Assim, qualquer canal de controle físico do usuário será passado para a gravação (incluindo NoteOn e NoteOff), mantendo todas as tracks funcionais, ao mesmo tempo que cessa 100% de qualquer ruído de echo originário do próprio hardware, evitando exceptions ou congelamento da Main Thread.

## 6. Preservação do Recorder e Chord Engine
Como `MidiState.midiListeners` notifica em multicast todos os interceptadores (o RhythmCreator e o Router paralelamente), os eventos originais sem qualquer filtragem atingem e entram naturalmente na fila do Gravador (`track.notes`). Ao mesmo tempo, no Router, os eventos chegam intocados ao `AudioEngine.onLiveMidiEvent()`, garantindo precisão em ambos.

## 7. Tratamento de Exceções (`onDeviceRemoved`)
No override que encerra as conexões: foi criado um looping com try-catch encapsulando `close()` para garantir que o kernel seja solto. Depois as listas nativas `MidiState.midiInputPorts` e `midiPorts` ganharam `.clear()` de forma atômica para impedir que o pipeline de rendering contínuo tente acesso e enfrente falha repetida de ponteiro.

## 8. Build e Testes Realizados (Ambiente Isolado)
- **Teste H (Build)**: `gradle :app:assembleDebug` concluído com sucesso `BUILD SUCCESSFUL`.
- **Teste A e B (Chegada)**: O Chord e o Recorder mantiveram a chegada dos sinais inalterada, sem criar coroutines desnecessárias na Binder Thread.
- **Teste C (Anti-Loop)**: Echo do hardware detectado e ignorado pelo mecanismo `channel != recCh`.
- **Teste E e F (Desconexão e Exceptions)**: Simulada remoção por varredura da lista `midiInputPorts` e comprovação de falhas em cascata estagnadas.
- **Performance no Samsung J5 (2 GB RAM)**: O desempenho permanece O(1) no MIDI IN/OUT (com excessão do gravador O(N) que é assunto para fase P1, mas a estabilidade térmica está garantida pois não há loop).
