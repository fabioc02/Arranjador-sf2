package com.example.domain.performance

data class Registration(
    val id: Int,
    val tempo: Double,
    val voiceId: String,
    val styleId: String,
    val transpose: Int,
    val splitPoint: SplitPoint,
    val harmonyEnabled: Boolean,
    val otsMode: Boolean
)
