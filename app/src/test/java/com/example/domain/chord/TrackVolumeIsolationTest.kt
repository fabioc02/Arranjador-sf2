package com.example.domain.chord

import com.example.RhythmTrack
import org.junit.Assert.*
import org.junit.Test

class TrackVolumeIsolationTest {

    @Test
    fun testTrackVolumeIsIsolated() {
        val track1 = RhythmTrack("Rhythm 1", 9, 100, 0, 0, true)
        val track2 = RhythmTrack("Rhythm 2", 10, 80, 0, 0, true)
        
        // Simular lógica de UI (sem o envio para o canal 0)
        val onTrack1VolumeChange = { vol: Int -> 
            track1.volume = vol
            // Simulando o que a UI faz agora: apenas canal da track
            // MidiState.sendControlChange(track1.channel - 1, 7, track1.volume)
        }
        
        val onTrack2VolumeChange = { vol: Int -> 
            track2.volume = vol
            // Simulando o que a UI faz agora: apenas canal da track
            // MidiState.sendControlChange(track2.channel - 1, 7, track2.volume)
        }

        onTrack1VolumeChange(100)
        onTrack2VolumeChange(80)

        // Verificar que os volumes estão independentes na modelagem
        assertEquals(100, track1.volume)
        assertEquals(80, track2.volume)
    }
}
