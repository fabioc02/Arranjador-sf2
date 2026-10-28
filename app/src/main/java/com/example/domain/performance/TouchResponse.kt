package com.example.domain.performance

data class TouchResponse(
    var enabled: Boolean = true,
    var curve: VelocityCurve = VelocityCurve()
)
