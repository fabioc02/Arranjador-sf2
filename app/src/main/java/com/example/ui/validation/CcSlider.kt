package com.example.ui.validation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CcSlider(label: String, value: Int, onValueChange: (Int) -> Unit, onSend: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label:", fontSize = 12.sp, color = Color.LightGray, modifier = Modifier.width(60.dp))
        Slider(value = value.toFloat(), onValueChange = { onValueChange(it.toInt()) }, onValueChangeFinished = onSend, valueRange = 0f..127f, modifier = Modifier.weight(1f))
        Text(value.toString(), fontSize = 12.sp, modifier = Modifier.width(24.dp))
    }
}
