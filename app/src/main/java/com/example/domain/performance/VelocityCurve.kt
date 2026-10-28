package com.example.domain.performance

enum class VelocityCurveType {
    Normal,
    Soft,
    Hard,
    Fixed
}

class VelocityCurve(var type: VelocityCurveType = VelocityCurveType.Normal, var fixedValue: Int = 100) {
    fun apply(inputVelocity: Int): Int {
        if (type == VelocityCurveType.Fixed) return fixedValue
        // Basic placeholder for curve math
        return inputVelocity
    }
}
