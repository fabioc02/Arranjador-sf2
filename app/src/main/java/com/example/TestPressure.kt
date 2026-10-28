package com.example

import androidx.compose.ui.input.pointer.PointerInputChange

fun testPressure(change: PointerInputChange) {
    val p: Float = change.pressure
}
