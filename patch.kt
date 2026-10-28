    fun handleMidiInput(status: Int, data1: Int, data2: Int) {
        val command = status and 0xF0
        val channel = status and 0x0F
        
        val isLeftHand = channel == 2 || channel == 3 || (channel <= 1 && data1 <= com.example.MidiState.splitPoint)
        if (isLeftHand) {
            if (command == 0x90 && data2 > 0) { // Note On
                heldNotes.add(data1)
                _activeLeftHandNotes.value = heldNotes.sorted()
                val detected = chordEngine.detectChord(heldNotes.sorted())
                if (detected != null && detected != _currentChord.value) {
                    setChord(detected)
                }
            } else if (command == 0x80 || (command == 0x90 && data2 == 0)) { // Note Off
                heldNotes.remove(data1)
                _activeLeftHandNotes.value = heldNotes.sorted()
            }
        }
    }
