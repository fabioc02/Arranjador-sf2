# FASE A — P0: SEGURANÇA MIDI/USB, RECUPERAÇÃO DO MIDI IN E PREPARAÇÃO DO ARRANGER

## 1. Arquivos analisados
- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/example/domain/midi/GlobalMidiRouter.kt`
- `app/src/main/java/com/example/AudioEngine.kt`
- `app/src/main/java/com/example/MidiRhythmCreator.kt`

## 2. Arquivos modificados
**Nenhum arquivo modificado até o momento.** (Aguardando autorização).

*Planejados para modificação:*
- `MainActivity.kt`: Adição do override `onDeviceRemoved` para limpeza das portas (`close()` e `clear()`) prevenindo callbacks fantasmas e crashes por `IOException`.
- `GlobalMidiRouter.kt`: 
  1) Restrição da retransmissão de gravação (Echo / THRU) apenas para notas de entrada geradas pelo hardware (ex: Canais físicos 0, 1 ou 2), barrando as que estão ecoando na placa de som (ex: Ch 9, 10, etc).
  2) Despacho explícito de `AudioEngine.onLiveMidiEvent(status, data1, data2)` para alimentar o Chord Engine em C++.

## 3. Arquivos NÃO modificados
- **C++, Oboe, CASM, YamahaRules**: Intocados.
- **VoiceEngine & YamahaVoiceRepository**: Intocados.
- **RealTimeMidiScheduler**: Intocado.
- **Style Player & Voice Editor**: Intocados.
- **UI do RhythmCreator**: Intocada.

## 4. Fluxo MIDI antes (Estado Atual)
```text
PSR MIDI IN 
   ↓
AndroidMidiManager (MidiReceiver.onSend)
   ↓
MidiState.midiListeners 
   ↓
GlobalMidiRouter (recebe via listener)
   ↓
Se `overrideRightHandChannel` for nulo: Morre (não envia pra C++, não aciona Chord Engine).
Se `overrideRightHandChannel` for não-nulo: 
   MidiState.sendNoteOn -> MIDI OUT (gera retransmissão no teclado, voltando ao PSR MIDI IN, criando loop infinito síncrono).
```

## 5. Fluxo MIDI depois (Proposto)
```text
PSR MIDI IN 
   ↓
AndroidMidiManager
   ↓
MidiState.midiListeners
   ↓
GlobalMidiRouter
   │
   ├──→ AudioEngine.onLiveMidiEvent (Alimenta nativamente o Chord Engine C++ sem ecoar).
   │
   └──→ Se `overrideRightHandChannel` ativado:
        Verifica a origem: `if (channel == 0 || channel == 1 || channel == 2)`
        ↳ Se Sim: MidiState.sendNoteOn(overrideRightHandChannel) -> MIDI OUT
        ↳ Se Não: Ignora (quebrando o loop).
```

## 6. Loop MIDI
**Classificação: CONFIRMED**
**Conclusão Técnica**: Em `GlobalMidiRouter`, quando o usuário clica numa Track para gravar, `overrideRightHandChannel` recebe o canal (ex: 9). Uma nota tocada no teclado (CH1) entra, e o router faz `sendNoteOn(9, nota)`. Se o PSR-S750 estiver ecoando via USB OUT as notas que recebe no USB IN, essa nota CH9 entra novamente no Android. O router lê a nota CH9 e manda OUTRA VEZ como `sendNoteOn(9, nota)`, fechando um loop de feedback sem atraso que tranca o Binder (causando a tela preta e o estrangulamento da CPU no Samsung J5). Ao restringir o THRU apenas para as partes tocadas ativamente (`channel == 0` ou `1`), a nota CH9 de eco será simplesmente descartada pelo router.

## 7. onDeviceRemoved
**Problema Encontrado**: O callback `onDeviceRemoved` está *completamente ausente* do código em `MainActivity.kt`. Se o OTG for puxado, o `MidiState.midiInputPorts` mantém os descritores do sistema mortos, gerando exceptions sempre que há uma chamada nativa. 
**Proposta**: Implementar o `onDeviceRemoved` iterando `MidiState.midiInputPorts.forEach { it.close() }` e limpando os arrays. Assim, o app suspende os envios ao hardware sem causar erro.

## 8. Chord Engine
**Problema Encontrado**: A chamada `AudioEngine.onLiveMidiEvent`, essencial para entregar os bits ao C++ (onde mora o motor do Arranger), está declarada em Kotlin, mas *não é chamada em lugar nenhum*.
**Proposta**: Inseri-la no `GlobalMidiRouter`, antes do filtro de THRU. Assim todo o fluxo físico de teclado atingirá o Chord Engine sem gerar eco indesejado.

## 9. Performance
Nenhuma nova thread será criada e nenhuma coleção grande (como a lista `track.notes`) será tratada nesta fase A, focando a resolução exclusivamente no pipeline USB para combater os sintomas críticos e não causar perdas de performance por alocações excessivas.

## 10. Build / 11. Testes
Ainda não testados, aguardando autorização para implementação.
