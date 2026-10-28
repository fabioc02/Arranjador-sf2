# Relatório de Diagnóstico: Performance (J5 2GB), Tonalidade do Baixo (CASM) e Panning

Este relatório foi gerado para análise conjunta com o ChatGPT, diagnosticando os problemas relatados em testes práticos no Samsung Galaxy J5 (2GB de RAM) e observações sobre a engine do Rhythm Creator. **Nenhuma linha de código foi alterada neste momento.**

## 1. Engasgos, Notas Presas e Fechamento Inesperado (Crash)
O Galaxy J5 é um aparelho de entrada (geralmente com Snapdragon 410 e 1.5GB a 2GB de RAM). A arquitetura do Android limita severamente a memória por aplicativo (às vezes 192MB ou 256MB).

*   **Fechamento (Crash / Out Of Memory):** Carregar bibliotecas de áudio, SoundFonts (arquivos SF2 pesados), a interface moderna do Jetpack Compose e o motor MIDI simultaneamente exige muita RAM. Quando o limite é atingido, o sistema operacional mata o aplicativo sem aviso ("fechou sozinho").
*   **Engasgos (Stuttering):** O nosso agendador MIDI atual (`RealTimeMidiScheduler`) dispara eventos na Thread de UI ou via Coroutines de alta frequência. Em um processador mais antigo, a sobrecarga de desenhar a tela (atualizar o cursor/UI Tick) conflita com o tempo de processamento do áudio, causando "engasgos".
*   **Notas Presas (Stuck Notes):** É a consequência direta dos engasgos. O protocolo MIDI funciona com mensagens de `Note On` e `Note Off`. Se o processador "travar" momentaneamente para fazer a coleta de lixo (Garbage Collection) exatamente no momento em que deveria enviar o `Note Off`, a nota fica tocando infinitamente. 

**Solução Futura (Para o ChatGPT):** Otimizar a alocação de memória (evitar criar objetos no loop MIDI), separar totalmente a thread de áudio da thread de UI (não atualizar a tela a cada "tick" microscópico, mas em blocos maiores) e implementar um "Panic Button" (All Notes Off).

---

## 2. Diferença no Som do Baixo (O Padrão CMaj7 da Yamaha)
Você tocou em um ponto de extrema inteligência ao consultar o manual da Yamaha. 
O seu relato de que "o baixo soou diferente porque gravei em outro tom" é **100% preciso.**

*   **A Regra SFF (Style File Format):** O padrão mundial da Yamaha exige que **todos os estilos sejam gravados na tonalidade de Dó Maior com Sétima Maior (CMaj7)**. Isso significa que o acorde base é `C` e a escala base é `C D E F G A B`.
*   **O que aconteceu na sua gravação:** Se você gravou o seu baixo em Sol (G), o nosso CASM Parser e a `BassRule` (que já provamos estar funcionando perfeitamente) leram a sua nota `G` e disseram: *"Ah, essa nota original está uma 5ª (7 semitons) acima da raiz C"*. Quando você toca um acorde no arranjador, a tabela de baixo vai usar essa distância matemática para recalcular a transposição. Como a fundação não era C, a matemática de inversão offset de C foi aplicada sobre um G, gerando um som diferente.
*   **Conclusão:** O código do CASM está corretíssimo. Para que ele soe igual ao Yamaha, o criador do ritmo (você) **deve obrigatoriamente gravar as frases originais em Dó (C)**.

---

## 3. Sliders de Panning (L/R) Sem Efeito
A falha no direcionamento do som da esquerda para a direita possui duas origens possíveis na nossa arquitetura atual:

1.  **Sincronização de Estado no Playback:** O slider envia o comando MIDI CC 10 (Pan) apenas quando você o movimenta (e se o ritmo estiver parado). Quando você dá *Play*, a engine de playback está lendo apenas os eventos MIDI que foram gravados. O valor fixo do slider não está sendo "injetado" no Início (Tick 0) do compasso.
2.  **Suporte do Sintetizador:** É preciso que o nosso motor de áudio (seja ele o FluidSynth nativo ou o MidiPlayer Android) esteja configurado para aceitar ordens de Controle Contínuo (CC) nº 10. Se a biblioteca ignorar o CC 10, o som sairá sempre no centro.

**Solução Futura (Para o ChatGPT):** Fazer a função de `Play` varrer os parâmetros estáticos das trilhas (Volume, Pan, Reverb, Chorus) e disparar esses comandos para a porta MIDI instantaneamente no `Tick 0`, antes da primeira nota tocar.
