    fun autoFill() {
        val current = state.currentSection.get()
        val fillSection = when (current) {
            StyleSection.MainA -> StyleSection.FillInAA
            StyleSection.MainB -> StyleSection.FillInBB
            StyleSection.MainC -> StyleSection.FillInCC
            StyleSection.MainD -> StyleSection.FillInDD
            else -> StyleSection.FillInAA
        }
        val targetMain = when (current) {
            StyleSection.MainA -> StyleSection.MainA
            StyleSection.MainB -> StyleSection.MainB
            StyleSection.MainC -> StyleSection.MainC
            StyleSection.MainD -> StyleSection.MainD
            else -> StyleSection.MainA
        }
        val style = state.loadedStyle.get()
        if (style != null) {
            // Se tiver o fill, toca e agenda voltar pro main.
            if (style.hasSection(fillSection)) {
                engine.sendCommand(ArrangerCommand.ChangeSection(fillSection))
                // Note: Engine already returns to Main via logic in manageLoopAndSections if nextSection is null!
                // But just in case, we could set nextSection explicitly.
                // It's cleaner to let manageLoopAndSections do it, or we add another command.
                // Let's just send the Fill, and Engine will handle the return.
            }
        }
    }
