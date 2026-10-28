package com.example.domain.playback

data class GeneratedMidiEvent(
    val tick: Long,
    val trackName: String,
    val channel: Int,
    val eventType: String,
    val note: Int = -1,
    val originalNote: Int = -1,
    val velocity: Int = -1,
    val ccController: Int = -1,
    val ccValue: Int = -1,
    val program: Int = -1,
    val pitchBendValue: Int = -1,
    val sysExData: ByteArray? = null,
    val ntrApplied: String = "",
    val nttApplied: String = "",
    val voiceLeadingApplied: String = ""
)
