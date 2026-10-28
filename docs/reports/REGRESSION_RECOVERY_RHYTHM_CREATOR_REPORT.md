# AUDITORIA DE REGRESSÃO E RECUPERAÇÃO FUNCIONAL — ARRANGER ANDROID

## 1. STYLE CREATOR / RHYTHM CREATOR DESAPARECEU DA TELA PRINCIPAL
**[REGRESSÃO]**: O aplicativo não exibe mais a tela principal do sistema (`ValidationPlayerScreen`), forçando o `MidiRhythmCreatorDialog` (Rhythm Creator) a ser a tela única e raiz do app.
**[CAUSA]**: O arquivo `MainActivity.kt` teve seu bloco `setContent` reescrito para embutir diretamente o `MidiRhythmCreatorDialog`, removendo o `Scaffold` original que continha a `ValidationPlayerScreen`.
**[ARQUIVO]**: `app/src/main/java/com/example/MainActivity.kt`
**[IMPACTO]**: Todo o sistema de Playback de STY (Arranger), as abas de CASM, MIDI Monitor, Inspector, e a tela original do EXT MIDI foram removidas do acesso do usuário.
**[RECUPERAÇÃO PROPOSTA]**: Restaurar o `setContent` original da `MainActivity` para chamar a `ValidationPlayerScreen`, e restaurar o estado da UI para que o Rhythm Creator seja aberto como um Dialog/Screen a partir do botão existente na aba EXT MIDI, devolvendo a hierarquia visual original.
**[TESTE]**: Verificar se o App abre na tela de Player do STY e se as 5 abas originais existem.

## 2. MENU DSP DESAPARECEU
**[REGRESSÃO]**: O menu e configurações de DSP Yamaha não estão mais disponíveis.
**[CAUSA]**: O componente `DSPControlPanel.kt` existe no código fonte, mas perdeu todas as chamadas. Ele estava na aba de EXT MIDI ou na tela original que foi substituída.
**[ARQUIVO]**: `app/src/main/java/com/example/ui/validation/DSPControlPanel.kt` e `ExternalMidiControlPanel.kt`
**[IMPACTO]**: O usuário não consegue configurar Reverb, Chorus e os DSPs Inseridos das Voices.
**[RECUPERAÇÃO PROPOSTA]**: Reinserir a chamada para `DSPControlPanel()` dentro da interface de configuração (provavelmente dentro do `ExternalMidiControlPanel` na aba EXT MIDI).
**[TESTE]**: Verificar a presença do seletor de efeitos de DSP, Chorus e Reverb na tela de MIDI.

## 3. PLAYBACK / METRÔNOMO / ARRANGER NÃO ENVIAM SOM
**[REGRESSÃO]**: "Nenhum som chega ao Yamaha PSR-S750" durante gravação, playback e uso do Arranger.
**[CAUSA]**: A quebra ocorreu em múltiplas frentes:
1. Ao remover o `ValidationPlayerScreen` da raiz do app, o `ValidationPlayerViewModel` (que detinha as Coroutines e Loops do Arranger) nunca é inicializado, silenciando todo o Player de STYs.
2. Na interface nativa do Rhythm Creator e no Roteador MIDI (`GlobalMidiRouter`), as rotas de áudio parecem silenciadas. Como o "Thru" do `MidiReceiver` da `MainActivity` foi excluído ("No Kotlin MIDI Thru needed anymore"), todo o som que sairia precisa passar pelo loop correto de estado ou pela configuração do `GlobalMidiRouter`. Se as Voices (VoiceEngine) estiverem desativadas ou sofrendo bypass, nada sai.
**[ARQUIVO]**: `app/src/main/java/com/example/MainActivity.kt` e `app/src/main/java/com/example/presentation/viewmodel/ValidationPlayerViewModel.kt`
**[IMPACTO]**: Mudez completa das emissões MIDI do Android para o PSR.
**[RECUPERAÇÃO PROPOSTA]**: Restaurar a inicialização do ViewModel Principal, revisar se o input/output ports estão sendo adicionados corretamente à coleção concorrente `MidiState.midiInputPorts` na `MainActivity` e conferir se o `GlobalMidiRouter` não está bloqueando o canal.
**[TESTE]**: Pressionar o Playback e ouvir saída pelo PSR. Tocar no Arranger e verificar a emissão.

## 4. GRAVAÇÃO E CÓPIA DE TRACKS
**[REGRESSÃO]**: Gravação apresenta problemas. O botão e funcionalidade de "Copy Track" sumiram da interface do Rhythm Creator.
**[CAUSA]**: O código fonte de `MidiRhythmCreator.kt` perdeu as referências/botões para "Copy Track". O motor de gravação pode ter ficado instável com a sobreposição de `MidiRhythmCreator` sem o ciclo de vida adequado do `ValidationPlayerScreen`.
**[ARQUIVO]**: `app/src/main/java/com/example/MidiRhythmCreator.kt`
**[RECUPERAÇÃO PROPOSTA]**: Restaurar o botão de Copy Track para o Track e auditar a lógica de gravação no `DisposableEffect` (Note-On) para garantir que ele salva a Nota no Tick quantizado sem vazamentos.
**[TESTE]**: Gravar num compasso e testar a opção Copy para outro Track.

## 5. APP INICIA COM TRACKS JÁ GRAVADAS
**[REGRESSÃO]**: O usuário relatou que sempre que inicia o app, dados prévios são exibidos como já gravados.
**[CAUSA]**: A funcionalidade de Save Automático do Rhythm Creator (`com.example.RhythmAutoSaver.load(this)`) está embutida no `onCreate` da `MainActivity`, forçando o carregamento de "lixo" de sessões anteriores no momento em que o app inicia.
**[ARQUIVO]**: `app/src/main/java/com/example/MainActivity.kt`
**[RECUPERAÇÃO PROPOSTA]**: Desativar ou condicionar o Auto-Load para ser um botão "Restore Last Session" na UI, garantindo que o `RhythmState` inicie limpo e vazio, respeitando o desejo do usuário de iniciar um projeto novo por padrão.
**[TESTE]**: Abrir o aplicativo; o Canvas deve estar zerado e sem notas gravadas.

## 6. QUADRO NEGRO DA TIMELINE (CANVAS/BOX WIDTH)
**[REGRESSÃO]**: Os retângulos gráficos das notas gravadas ultrapassam o quadro nas fórmulas de compasso como 3/4 e 6/8.
**[CAUSA]**: Na fórmula visual, o cálculo de X usa `val totalTicks = MidiRhythmState.timeSigNumerator * MidiRhythmState.measures * ticksPerBeat`. Há inconsistências entre esse total de ticks visuais e a lógica do Relógio Musical, permitindo que a variável `px` transborde o valor de 1.0f e saia da tela.
**[ARQUIVO]**: `app/src/main/java/com/example/MidiRhythmCreator.kt`
**[RECUPERAÇÃO PROPOSTA]**: Limitar o rendering via `clip(RectangleShape)` no `BoxWithConstraints`, e corrigir a fórmula do musical engine para o cálculo do Canvas, assegurando coerência para compassos não convencionais.
**[TESTE]**: Gravar em 6/8 e verificar se a barra encosta exatamente no fim do box, não cruzando o limite.

## 7. COMPASSOS LIMITADOS (3/4, 4/4, 6/8)
**[REGRESSÃO]**: Apenas as fórmulas fixas podem ser inseridas (botão alterna de 4->3->6).
**[CAUSA]**: Em `MidiRhythmCreator.kt`, a ação do botão é: `if (num == 4) 3 else if (num == 3) 6 else 4`.
**[RECUPERAÇÃO PROPOSTA]**: Alterar a UI do compasso para ser livremente selecionável (exemplo, Input numérico ou um seletor abrangente que suporte 5/4, 7/8, 9/8, etc.), desfazendo a barreira artificial do hardware.
**[TESTE]**: Configurar compasso para 5/4 e verificar se o grid e a gravação respeitam a estrutura de 5 batidas.

## 8. ACORDES/CHORD DETECTION (REGRA PRESERVADA)
**[ESTADO ATUAL]**: O Chord Detection e Arranger já contam com uma regra (latched chord) que permite Note-Offs fantasmas (levantar o dedo) sem perder o último acorde identificado.
**[AÇÃO]**: NENHUMA alteração será feita nesta engine, a diretiva de recuperação garante sua preservação sem delays ou debounces extras.

## PRÓXIMOS PASSOS IMEDIATOS
Após este diagnóstico formalizado, daremos início apenas a Correções Cirúrgicas em Ordem de Prioridade:
1. **Restaurar `MainActivity`** para carregar a Tela Principal correta (`ValidationPlayerScreen`).
2. **Remover auto-restore silencioso** para garantir o início de estado limpo.
3. **Reativar a Instanciação do ViewModel** corrigindo o silêncio de playback MIDI OUT.
4. **Acoplar novamente o menu `DSPControlPanel`** à interface.
5. **Auditoria Visual do Canvas** e Botão Copy Track no Rhythm Creator.
