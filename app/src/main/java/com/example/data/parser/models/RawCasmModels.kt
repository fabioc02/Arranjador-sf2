package com.example.data.parser.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RawCasmDump(
    val offset: Long,
    val size: Long,
    val csegList: List<RawCsegDump>,
    val warnings: List<String>
)

@JsonClass(generateAdapter = true)
data class RawCsegDump(
    val offset: Long,
    val size: Long,
    val sdec: RawSdecDump?,
    val ctb2List: List<RawCtb2Dump>
)

@JsonClass(generateAdapter = true)
data class RawSdecDump(
    val offset: Long,
    val size: Long,
    val sections: List<String>,
    val rawBytesHex: String
)

@JsonClass(generateAdapter = true)
data class RawCtb2Dump(
    val offset: Long,
    val size: Long,
    val sourceChannel: Int,
    val destChannel: Int,
    val partName: String,
    val ntr: Int,
    val ntt: Int,
    val rawBytesHex: String,
    val unknownFieldsHex: String
)
