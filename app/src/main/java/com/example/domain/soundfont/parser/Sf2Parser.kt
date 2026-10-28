package com.example.domain.soundfont.parser

import com.example.domain.soundfont.model.InstrumentZone
import com.example.domain.soundfont.model.LoopMode
import com.example.domain.soundfont.model.SampleBuffer
import com.example.domain.soundfont.model.SampleHeader
import com.example.domain.soundfont.model.SoundFontMetadata
import com.example.domain.soundfont.model.SoundFontPreset
import com.example.domain.soundfont.model.SoundFontType
import com.example.domain.soundfont.model.StereoChannel
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Ultra-fast SoundFont 2 (SF2 v2.04) binary metadata parser and lazy on-demand sample reader.
 * 
 * Features:
 * 1. Zero-RAM indexing: reads only 'pdta' headers (< 50 KB) without loading the 11.5 MB 'smpl' PCM data.
 * 2. Full EMU8000 / Korg Triton stereo zone support (Left/Right sample pairs with stereo linking).
 * 3. Lazy Sample Streaming: seeks directly to sample offsets on demand when a preset is activated.
 */
object Sf2Parser {

    private data class RawGenerator(val oper: Int, val amount: Short)

    private data class RawBag(val genNdx: Int, val modNdx: Int)

    private data class RawPresetHeader(
        val name: String,
        val preset: Int,
        val bank: Int,
        val bagNdx: Int
    )

    private data class RawInstrumentHeader(
        val name: String,
        val bagNdx: Int
    )

    fun parseMetadata(file: File): SoundFontMetadata {
        RandomAccessFile(file, "r").use { raf ->
            val riff = readAscii(raf, 4)
            require(riff == "RIFF") { "Not a RIFF file: $riff" }
            val fileSize = readUInt32(raf)
            val sfbk = readAscii(raf, 4)
            require(sfbk == "sfbk") { "Not a SoundFont bank: $sfbk" }

            var sfName = file.nameWithoutExtension
            var smplOffset = 0L
            var smplLength = 0L

            var rawPresets = emptyList<RawPresetHeader>()
            var pBags = emptyList<RawBag>()
            var pGens = emptyList<RawGenerator>()
            var rawInstruments = emptyList<RawInstrumentHeader>()
            var iBags = emptyList<RawBag>()
            var iGens = emptyList<RawGenerator>()
            var sampleHeaders = emptyList<SampleHeader>()

            // Traverse top-level LIST chunks
            while (raf.filePointer < raf.length() - 8) {
                val chunkType = readAscii(raf, 4)
                val chunkSize = readUInt32(raf)
                val chunkEnd = raf.filePointer + chunkSize

                if (chunkType == "LIST") {
                    val listType = readAscii(raf, 4)
                    when (listType) {
                        "INFO" -> {
                            // Parse metadata strings like INAM
                            while (raf.filePointer < chunkEnd - 8) {
                                val subId = readAscii(raf, 4)
                                val subSize = readUInt32(raf)
                                val subEnd = raf.filePointer + subSize
                                if (subId == "INAM") {
                                    val nameBytes = ByteArray(subSize.toInt().coerceAtMost(256))
                                    raf.readFully(nameBytes)
                                    val parsedName = String(nameBytes, Charsets.US_ASCII).trimEnd(0.toChar(), ' ')
                                    if (parsedName.isNotEmpty()) sfName = parsedName
                                }
                                raf.seek(subEnd)
                            }
                        }
                        "sdta" -> {
                            // Find 'smpl' sub-chunk
                            while (raf.filePointer < chunkEnd - 8) {
                                val subId = readAscii(raf, 4)
                                val subSize = readUInt32(raf)
                                if (subId == "smpl") {
                                    smplOffset = raf.filePointer
                                    smplLength = subSize
                                    break
                                } else {
                                    raf.seek(raf.filePointer + subSize)
                                }
                            }
                        }
                        "pdta" -> {
                            // Parse hydra chunks: phdr, pbag, pmod, pgen, inst, ibag, imod, igen, shdr
                            while (raf.filePointer < chunkEnd - 8) {
                                val subId = readAscii(raf, 4)
                                val subSize = readUInt32(raf)
                                val subEnd = raf.filePointer + subSize
                                val bytes = ByteArray(subSize.toInt())
                                raf.readFully(bytes)
                                val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

                                when (subId) {
                                    "phdr" -> rawPresets = parsePhdr(buf, subSize.toInt())
                                    "pbag" -> pBags = parseBags(buf, subSize.toInt())
                                    "pgen" -> pGens = parseGens(buf, subSize.toInt())
                                    "inst" -> rawInstruments = parseInst(buf, subSize.toInt())
                                    "ibag" -> iBags = parseBags(buf, subSize.toInt())
                                    "igen" -> iGens = parseGens(buf, subSize.toInt())
                                    "shdr" -> sampleHeaders = parseShdr(buf, subSize.toInt())
                                }
                                raf.seek(subEnd)
                            }
                        }
                    }
                }
                raf.seek(chunkEnd)
            }

            // Build Presets & Instrument Zones
            val sampleMap = sampleHeaders.associateBy { it.id }
            val presets = mutableListOf<SoundFontPreset>()

            for (pIdx in 0 until (rawPresets.size - 1)) {
                val pHead = rawPresets[pIdx]
                val nextPHead = rawPresets[pIdx + 1]
                val pBagStart = pHead.bagNdx
                val pBagEnd = nextPHead.bagNdx.coerceAtMost(pBags.size)

                val presetZones = mutableListOf<InstrumentZone>()

                for (bIdx in pBagStart until pBagEnd) {
                    val bag = pBags[bIdx]
                    val nextBagGen = if (bIdx + 1 < pBags.size) pBags[bIdx + 1].genNdx else pGens.size
                    val genStart = bag.genNdx
                    val genEnd = nextBagGen.coerceAtMost(pGens.size)

                    var instId: Int? = null
                    var presetLoKey = 0
                    var presetHiKey = 127
                    var presetLoVel = 0
                    var presetHiVel = 127

                    for (g in genStart until genEnd) {
                        val gen = pGens[g]
                        when (gen.oper) {
                            41 -> instId = gen.amount.toInt() and 0xFFFF
                            43 -> {
                                presetLoKey = gen.amount.toInt() and 0xFF
                                presetHiKey = (gen.amount.toInt() shr 8) and 0xFF
                            }
                            44 -> {
                                presetLoVel = gen.amount.toInt() and 0xFF
                                presetHiVel = (gen.amount.toInt() shr 8) and 0xFF
                            }
                        }
                    }

                    if (instId != null && instId in 0 until (rawInstruments.size - 1)) {
                        val inst = rawInstruments[instId]
                        val nextInst = rawInstruments[instId + 1]
                        val iBagStart = inst.bagNdx
                        val iBagEnd = nextInst.bagNdx.coerceAtMost(iBags.size)

                        var globalInstGens = mutableMapOf<Int, Short>()

                        for (ibIdx in iBagStart until iBagEnd) {
                            val iBag = iBags[ibIdx]
                            val nextIBagGen = if (ibIdx + 1 < iBags.size) iBags[ibIdx + 1].genNdx else iGens.size
                            val iGenStart = iBag.genNdx
                            val iGenEnd = nextIBagGen.coerceAtMost(iGens.size)

                            var sampleId: Int? = null
                            var zoneLoKey = presetLoKey
                            var zoneHiKey = presetHiKey
                            var zoneLoVel = presetLoVel
                            var zoneHiVel = presetHiVel
                            var rootKey = -1
                            var fineTune = 0
                            var coarseTune = 0
                            var pan = 0.0f
                            var loopMode = LoopMode.NO_LOOP
                            var attackSec = 0.001f
                            var decaySec = 0.1f
                            var sustainLevel = 1.0f
                            var releaseSec = 0.2f
                            var attenuationDb = 0.0f

                            for (ig in iGenStart until iGenEnd) {
                                val gen = iGens[ig]
                                when (gen.oper) {
                                    53 -> sampleId = gen.amount.toInt() and 0xFFFF
                                    43 -> {
                                        zoneLoKey = (gen.amount.toInt() and 0xFF).coerceAtLeast(presetLoKey)
                                        zoneHiKey = ((gen.amount.toInt() shr 8) and 0xFF).coerceAtMost(presetHiKey)
                                    }
                                    44 -> {
                                        zoneLoVel = (gen.amount.toInt() and 0xFF).coerceAtLeast(presetLoVel)
                                        zoneHiVel = ((gen.amount.toInt() shr 8) and 0xFF).coerceAtMost(presetHiVel)
                                    }
                                    58 -> rootKey = gen.amount.toInt() and 0x7F
                                    51 -> fineTune = gen.amount.toInt()
                                    52 -> coarseTune = gen.amount.toInt()
                                    17 -> {
                                        // pan: units of 0.1%, range -500 (Left) to +500 (Right)
                                        pan = (gen.amount / 500.0f).coerceIn(-1.0f, 1.0f)
                                    }
                                    54 -> {
                                        val modeVal = gen.amount.toInt() and 0x03
                                        loopMode = when (modeVal) {
                                            1 -> LoopMode.CONTINUOUS_LOOP
                                            3 -> LoopMode.LOOP_DURING_RELEASE
                                            else -> LoopMode.NO_LOOP
                                        }
                                    }
                                    33 -> attenuationDb = (gen.amount.toInt() / 10.0f)
                                    34 -> attackSec = timecentsToSec(gen.amount.toInt())
                                    38 -> decaySec = timecentsToSec(gen.amount.toInt())
                                    39 -> sustainLevel = (1.0f - (gen.amount.toInt() / 1000.0f)).coerceIn(0.0f, 1.0f)
                                    40 -> releaseSec = timecentsToSec(gen.amount.toInt())
                                }
                            }

                            if (sampleId != null) {
                                val sh = sampleMap[sampleId]
                                val effectiveLoopMode = if (loopMode == LoopMode.NO_LOOP && sh != null && sh.startLoop < sh.endLoop) {
                                    LoopMode.CONTINUOUS_LOOP
                                } else {
                                    loopMode
                                }

                                val effectivePan = if (pan == 0.0f && sh != null) {
                                    when (sh.stereoChannel) {
                                        StereoChannel.LEFT -> -0.5f
                                        StereoChannel.RIGHT -> 0.5f
                                        StereoChannel.MONO -> 0.0f
                                    }
                                } else {
                                    pan
                                }

                                val zone = InstrumentZone(
                                    lowKey = zoneLoKey,
                                    highKey = zoneHiKey,
                                    lowVel = zoneLoVel,
                                    highVel = zoneHiVel,
                                    rootKey = rootKey,
                                    fineTuneCents = fineTune,
                                    coarseTuneSemitones = coarseTune,
                                    pan = effectivePan,
                                    sampleId = sampleId,
                                    sampleHeader = sh,
                                    loopMode = effectiveLoopMode,
                                    attackSec = attackSec,
                                    decaySec = decaySec,
                                    sustainLevel = sustainLevel,
                                    releaseSec = releaseSec,
                                    attenuationDb = attenuationDb
                                )
                                presetZones.add(zone)
                            }
                        }
                    }
                }

                val isStereo = presetZones.any { it.isStereoLeft || it.isStereoRight }
                presets.add(
                    SoundFontPreset(
                        bank = pHead.bank,
                        program = pHead.preset,
                        name = pHead.name,
                        zones = presetZones,
                        isStereo = isStereo
                    )
                )
            }

            val hasAnyStereo = presets.any { it.isStereo } || sampleHeaders.any { it.isStereo }

            return SoundFontMetadata(
                name = sfName,
                filePath = file.absolutePath,
                type = SoundFontType.SF2,
                presets = presets,
                totalSamplesCount = sampleHeaders.size,
                smplChunkOffset = smplOffset,
                smplChunkLength = smplLength,
                isStereo = hasAnyStereo
            )
        }
    }

    /**
     * Loads raw 16-bit PCM audio samples on-demand for all zones in a preset.
     * Uses RandomAccessFile seeking directly to each sample start offset, avoiding loading the entire SF2.
     */
    fun loadSamplesForPreset(
        file: File,
        metadata: SoundFontMetadata,
        preset: SoundFontPreset
    ): Map<Int, SampleBuffer> {
        val cache = mutableMapOf<Int, SampleBuffer>()
        if (metadata.smplChunkOffset <= 0L) return cache

        RandomAccessFile(file, "r").use { raf ->
            for (zone in preset.zones) {
                val sh = zone.sampleHeader ?: continue
                val sampleBuffer = cache.getOrPut(sh.id) {
                    val numSamples = (sh.endOffset - sh.startOffset).toInt().coerceAtLeast(0)
                    if (numSamples <= 0) {
                        SampleBuffer(sh.id, sh.name, FloatArray(64), sh.sampleRate, sh.originalPitch, sh.pitchCorrection)
                    } else {
                        val fileByteOffset = metadata.smplChunkOffset + sh.startOffset * 2L
                        raf.seek(fileByteOffset)

                        val byteCount = numSamples * 2
                        val pcmBytes = ByteArray(byteCount)
                        raf.readFully(pcmBytes)

                        val pcmBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
                        val floatData = FloatArray(numSamples)
                        for (i in 0 until numSamples) {
                            val s16 = pcmBuffer.short
                            floatData[i] = s16 / 32768.0f
                        }

                        val loopStartIdx = (sh.startLoop - sh.startOffset).toInt().coerceIn(0, numSamples - 1)
                        val loopEndIdx = (sh.endLoop - sh.startOffset).toInt().coerceIn(loopStartIdx, numSamples)

                        SampleBuffer(
                            id = sh.id,
                            name = sh.name,
                            data = floatData,
                            sampleRate = sh.sampleRate,
                            rootKey = sh.originalPitch,
                            tuningCents = sh.pitchCorrection,
                            isLooped = loopEndIdx > loopStartIdx,
                            loopStart = loopStartIdx,
                            loopEnd = loopEndIdx,
                            stereoChannel = sh.stereoChannel
                        )
                    }
                }
                zone.sampleBuffer = sampleBuffer
            }
        }
        return cache
    }

    private fun parsePhdr(buf: ByteBuffer, size: Int): List<RawPresetHeader> {
        val count = size / 38
        val list = mutableListOf<RawPresetHeader>()
        for (i in 0 until count) {
            val nameBytes = ByteArray(20)
            buf.get(nameBytes)
            val name = String(nameBytes, Charsets.US_ASCII).trimEnd(0.toChar(), ' ')
            val preset = buf.short.toInt() and 0xFFFF
            val bank = buf.short.toInt() and 0xFFFF
            val bagNdx = buf.short.toInt() and 0xFFFF
            buf.int // library
            buf.int // genre
            buf.int // morphology
            list.add(RawPresetHeader(name, preset, bank, bagNdx))
        }
        return list
    }

    private fun parseInst(buf: ByteBuffer, size: Int): List<RawInstrumentHeader> {
        val count = size / 22
        val list = mutableListOf<RawInstrumentHeader>()
        for (i in 0 until count) {
            val nameBytes = ByteArray(20)
            buf.get(nameBytes)
            val name = String(nameBytes, Charsets.US_ASCII).trimEnd(0.toChar(), ' ')
            val bagNdx = buf.short.toInt() and 0xFFFF
            list.add(RawInstrumentHeader(name, bagNdx))
        }
        return list
    }

    private fun parseBags(buf: ByteBuffer, size: Int): List<RawBag> {
        val count = size / 4
        val list = mutableListOf<RawBag>()
        for (i in 0 until count) {
            val genNdx = buf.short.toInt() and 0xFFFF
            val modNdx = buf.short.toInt() and 0xFFFF
            list.add(RawBag(genNdx, modNdx))
        }
        return list
    }

    private fun parseGens(buf: ByteBuffer, size: Int): List<RawGenerator> {
        val count = size / 4
        val list = mutableListOf<RawGenerator>()
        for (i in 0 until count) {
            val oper = buf.short.toInt() and 0xFFFF
            val amount = buf.short
            list.add(RawGenerator(oper, amount))
        }
        return list
    }

    private fun parseShdr(buf: ByteBuffer, size: Int): List<SampleHeader> {
        val count = size / 46
        val list = mutableListOf<SampleHeader>()
        for (i in 0 until count) {
            val nameBytes = ByteArray(20)
            buf.get(nameBytes)
            val name = String(nameBytes, Charsets.US_ASCII).trimEnd(0.toChar(), ' ')
            val start = readUInt32(buf)
            val end = readUInt32(buf)
            val startLoop = readUInt32(buf)
            val endLoop = readUInt32(buf)
            val sampleRate = readUInt32(buf).toInt()
            val originalPitch = buf.get().toInt() and 0xFF
            val pitchCorrection = buf.get().toInt() // signed cents (-99..99)
            val sampleLink = buf.short.toInt() and 0xFFFF
            val sampleType = buf.short.toInt() and 0xFFFF

            list.add(
                SampleHeader(
                    id = i,
                    name = name,
                    startOffset = start,
                    endOffset = end,
                    startLoop = startLoop,
                    endLoop = endLoop,
                    sampleRate = sampleRate,
                    originalPitch = originalPitch,
                    pitchCorrection = pitchCorrection,
                    sampleLink = sampleLink,
                    sampleType = sampleType
                )
            )
        }
        return list
    }

    private fun timecentsToSec(timecents: Int): Float {
        if (timecents <= -32768) return 0.001f
        return Math.pow(2.0, timecents / 1200.0).toFloat().coerceIn(0.0001f, 30.0f)
    }

    private fun readAscii(raf: RandomAccessFile, length: Int): String {
        val bytes = ByteArray(length)
        raf.readFully(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun readUInt32(raf: RandomAccessFile): Long {
        val b0 = raf.read().toLong() and 0xFF
        val b1 = raf.read().toLong() and 0xFF
        val b2 = raf.read().toLong() and 0xFF
        val b3 = raf.read().toLong() and 0xFF
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

    private fun readUInt32(buf: ByteBuffer): Long {
        return buf.int.toLong() and 0xFFFFFFFFL
    }
}
