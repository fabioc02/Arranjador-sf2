package com.example.domain.soundfont

import com.example.domain.soundfont.model.InstrumentZone
import com.example.domain.soundfont.model.LoopMode
import com.example.domain.soundfont.model.SoundFontType
import com.example.domain.soundfont.model.StereoChannel
import com.example.domain.soundfont.parser.Sf2Parser
import com.example.domain.soundfont.parser.SfzParser
import com.example.domain.soundfont.parser.WavReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SoundFontParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testWavReader_Parses16BitPcm() {
        val sampleRate = 44100
        val numSamples = 200
        val pcmShorts = ShortArray(numSamples) { (it * 100).toShort() }

        val wavFile = tempFolder.newFile("test_sample_L.wav")
        wavFile.writeBytes(createMockWavBytes(pcmShorts, sampleRate, channels = 1))

        val buffer = WavReader.readWav(wavFile, sampleId = 1, defaultRootKey = 60)
        assertEquals("test_sample_L", buffer.name)
        assertEquals(sampleRate, buffer.sampleRate)
        assertEquals(numSamples, buffer.data.size)
        assertEquals(StereoChannel.LEFT, buffer.stereoChannel)
        // Verify normalized float values
        assertEquals(0.0f, buffer.data[0], 0.001f)
        assertEquals(100 / 32768.0f, buffer.data[1], 0.0001f)
    }

    @Test
    fun testSfzParser_ParsesTritonStyleStereoRegions() {
        val sfzFile = tempFolder.newFile("EXBF084_Pan_Flute_Symphonic_F.sfz")
        val samplesDir = File(tempFolder.root, "samples").apply { mkdirs() }

        // Create sample WAV files for L and R
        val wavL = File(samplesDir, "C1_L.wav").apply {
            writeBytes(createMockWavBytes(ShortArray(100) { 1000 }, 44100, 1))
        }
        val wavR = File(samplesDir, "C1_R.wav").apply {
            writeBytes(createMockWavBytes(ShortArray(100) { 2000 }, 44100, 1))
        }

        val sfzContent = """
            <control>
            default_path=samples/
            
            <global>
            ampeg_attack=0.005
            ampeg_release=0.3
            
            // Region 1: Left channel (C1_L)
            <region>
            sample=C1_L.wav
            lokey=0 hikey=24
            pitch_keycenter=24
            pan=-50
            loop_mode=loop_continuous
            
            // Region 2: Right channel (C1_R)
            <region>
            sample=C1_R.wav
            lokey=0 hikey=24
            pitch_keycenter=24
            pan=50
            loop_mode=loop_continuous
        """.trimIndent()
        sfzFile.writeText(sfzContent)

        // 1. Parse Metadata (Zero-RAM, does not load WAVs)
        val metadata = SfzParser.parseMetadata(sfzFile)
        assertEquals("EXBF084_Pan_Flute_Symphonic_F", metadata.name)
        assertEquals(SoundFontType.SFZ, metadata.type)
        assertTrue("Must detect stereo soundfont", metadata.isStereo)
        assertEquals(1, metadata.presets.size)

        val preset = metadata.presets[0]
        assertEquals(2, preset.zones.size)

        val zoneL = preset.zones[0]
        val zoneR = preset.zones[1]
        assertEquals(0, zoneL.lowKey)
        assertEquals(24, zoneL.highKey)
        assertEquals(24, zoneL.rootKey)
        assertEquals(-0.5f, zoneL.pan, 0.01f)
        assertTrue(zoneL.isStereoLeft)

        assertEquals(0, zoneR.lowKey)
        assertEquals(24, zoneR.highKey)
        assertEquals(24, zoneR.rootKey)
        assertEquals(0.5f, zoneR.pan, 0.01f)
        assertTrue(zoneR.isStereoRight)

        // Find matching zones for note C1 (MIDI 24)
        val matched = preset.findMatchingZones(24, 100)
        assertEquals("Both Left and Right stereo zones must be matched for note 24", 2, matched.size)

        // 2. Load Samples on demand
        val loadedCache = SfzParser.loadSamplesForPreset(sfzFile, preset)
        assertEquals(2, loadedCache.size)
        assertNotNull(zoneL.sampleBuffer)
        assertNotNull(zoneR.sampleBuffer)
        assertEquals(StereoChannel.LEFT, zoneL.sampleBuffer?.stereoChannel)
        assertEquals(StereoChannel.RIGHT, zoneR.sampleBuffer?.stereoChannel)
        assertEquals(100, zoneL.sampleBuffer?.data?.size)
    }

    @Test
    fun testSf2Parser_ParsesBinarySfbkAndExtractsOnDemand() {
        val sf2File = tempFolder.newFile("Triton_Test.sf2")
        val samplePcm = ShortArray(256) { (it * 50).toShort() }
        val sf2Bytes = createMinimalSf2Binary("Triton_Test", samplePcm)
        sf2File.writeBytes(sf2Bytes)

        // 1. Parse Metadata
        val metadata = Sf2Parser.parseMetadata(sf2File)
        assertEquals("Triton_Test", metadata.name)
        assertEquals(SoundFontType.SF2, metadata.type)
        assertEquals(1, metadata.presets.size)
        assertTrue(metadata.smplChunkOffset > 0L)

        val preset = metadata.presets[0]
        assertEquals(0, preset.bank)
        assertEquals(0, preset.program)
        assertEquals(1, preset.zones.size)

        val zone = preset.zones[0]
        assertEquals(0, zone.lowKey)
        assertEquals(127, zone.highKey)
        assertNotNull(zone.sampleHeader)
        assertEquals(60, zone.sampleHeader?.originalPitch)

        // 2. Lazy On-Demand Sample Extraction
        val loaded = Sf2Parser.loadSamplesForPreset(sf2File, metadata, preset)
        assertEquals(1, loaded.size)
        assertNotNull(zone.sampleBuffer)
        assertEquals(256, zone.sampleBuffer?.data?.size)
        assertEquals(0.0f, zone.sampleBuffer?.data?.get(0) ?: -1f, 0.001f)
    }

    // --- Helper functions to generate mock binary data ---

    private fun createMockWavBytes(pcmData: ShortArray, sampleRate: Int, channels: Int): ByteArray {
        val byteRate = sampleRate * channels * 2
        val blockAlign = channels * 2
        val dataSize = pcmData.size * 2
        val totalSize = 36 + dataSize

        val buf = ByteBuffer.allocate(totalSize + 8).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(totalSize)
        buf.put("WAVE".toByteArray())

        // fmt chunk
        buf.put("fmt ".toByteArray())
        buf.putInt(16)
        buf.putShort(1) // PCM
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(16) // bitsPerSample

        // data chunk
        buf.put("data".toByteArray())
        buf.putInt(dataSize)
        for (s in pcmData) {
            buf.putShort(s)
        }
        return buf.array()
    }

    private fun createMinimalSf2Binary(name: String, samplePcm: ShortArray): ByteArray {
        fun chunk(id: String, payload: ByteArray): ByteArray {
            val buf = ByteBuffer.allocate(payload.size + 8).order(ByteOrder.LITTLE_ENDIAN)
            buf.put(id.toByteArray(Charsets.US_ASCII))
            buf.putInt(payload.size)
            buf.put(payload)
            return buf.array()
        }

        // INFO
        val inamPayload = ByteArray(20)
        val nameAscii = name.toByteArray(Charsets.US_ASCII)
        System.arraycopy(nameAscii, 0, inamPayload, 0, nameAscii.size.coerceAtMost(19))
        val inamChunk = chunk("INAM", inamPayload)
        val infoList = chunk("LIST", "INFO".toByteArray(Charsets.US_ASCII) + inamChunk)

        // sdta
        val smplBuf = ByteBuffer.allocate(samplePcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samplePcm) smplBuf.putShort(s)
        val smplChunk = chunk("smpl", smplBuf.array())
        val sdtaList = chunk("LIST", "sdta".toByteArray(Charsets.US_ASCII) + smplChunk)

        // pdta
        // phdr: 2 records * 38 bytes
        val phdrBuf = ByteBuffer.allocate(76).order(ByteOrder.LITTLE_ENDIAN)
        val pNameBytes = ByteArray(20)
        System.arraycopy(nameAscii, 0, pNameBytes, 0, nameAscii.size.coerceAtMost(19))
        phdrBuf.put(pNameBytes)
        phdrBuf.putShort(0); phdrBuf.putShort(0); phdrBuf.putShort(0)
        phdrBuf.putInt(0); phdrBuf.putInt(0); phdrBuf.putInt(0)
        // EOP
        phdrBuf.put("EOP".toByteArray(Charsets.US_ASCII).copyOf(20))
        phdrBuf.putShort(0); phdrBuf.putShort(0); phdrBuf.putShort(1)
        phdrBuf.putInt(0); phdrBuf.putInt(0); phdrBuf.putInt(0)

        // pbag: 2 records * 4 bytes
        val pbagBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        pbagBuf.putShort(0); pbagBuf.putShort(0)
        pbagBuf.putShort(1); pbagBuf.putShort(0)

        // pmod: 1 record * 10 bytes
        val pmodBuf = ByteArray(10)

        // pgen: 2 records * 4 bytes
        val pgenBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        pgenBuf.putShort(41); pgenBuf.putShort(0) // inst 0
        pgenBuf.putShort(0); pgenBuf.putShort(0)

        // inst: 2 records * 22 bytes
        val instBuf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        instBuf.put(pNameBytes)
        instBuf.putShort(0)
        instBuf.put("EOI".toByteArray(Charsets.US_ASCII).copyOf(20))
        instBuf.putShort(1)

        // ibag: 2 records * 4 bytes
        val ibagBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        ibagBuf.putShort(0); ibagBuf.putShort(0)
        ibagBuf.putShort(1); ibagBuf.putShort(0)

        // imod: 1 record * 10 bytes
        val imodBuf = ByteArray(10)

        // igen: 2 records * 4 bytes
        val igenBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        igenBuf.putShort(53); igenBuf.putShort(0) // sample 0
        igenBuf.putShort(0); igenBuf.putShort(0)

        // shdr: 2 records * 46 bytes
        val shdrBuf = ByteBuffer.allocate(92).order(ByteOrder.LITTLE_ENDIAN)
        shdrBuf.put("Sample0".toByteArray(Charsets.US_ASCII).copyOf(20))
        shdrBuf.putInt(0); shdrBuf.putInt(samplePcm.size); shdrBuf.putInt(0); shdrBuf.putInt(samplePcm.size)
        shdrBuf.putInt(44100); shdrBuf.put(60.toByte()); shdrBuf.put(0.toByte()); shdrBuf.putShort(0); shdrBuf.putShort(1)
        // EOS
        shdrBuf.put("EOS".toByteArray(Charsets.US_ASCII).copyOf(20))
        shdrBuf.putInt(0); shdrBuf.putInt(0); shdrBuf.putInt(0); shdrBuf.putInt(0)
        shdrBuf.putInt(0); shdrBuf.put(0.toByte()); shdrBuf.put(0.toByte()); shdrBuf.putShort(0); shdrBuf.putShort(0)

        val pdtaPayload = "pdta".toByteArray(Charsets.US_ASCII) +
            chunk("phdr", phdrBuf.array()) +
            chunk("pbag", pbagBuf.array()) +
            chunk("pmod", pmodBuf) +
            chunk("pgen", pgenBuf.array()) +
            chunk("inst", instBuf.array()) +
            chunk("ibag", ibagBuf.array()) +
            chunk("imod", imodBuf) +
            chunk("igen", igenBuf.array()) +
            chunk("shdr", shdrBuf.array())

        val pdtaList = chunk("LIST", pdtaPayload)

        val sfbkPayload = "sfbk".toByteArray(Charsets.US_ASCII) + infoList + sdtaList + pdtaList
        return chunk("RIFF", sfbkPayload)
    }
}
