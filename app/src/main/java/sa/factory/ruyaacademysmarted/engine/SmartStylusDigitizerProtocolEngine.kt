package sa.factory.ruyaacademysmarted.engine

import kotlin.math.sqrt

/**
 * 4096-Level Pressure Stylus Digitizer Protocol Engine.
 * Implements high-resolution stylus telemetry, sub-pixel coordinate tracking,
 * tilt azimuth calculation, and palm rejection filtering for interactive exams.
 */
class SmartStylusDigitizerProtocolEngine {

    companion object {
        const val MAX_PRESSURE_LEVEL = 4095
        const val PALM_REJECTION_MAX_RADIUS_MM = 3.5
        const val MIN_WRITING_PRESSURE = 120
    }

    enum class StrokeIntent {
        HOVER,
        ANNOTATION_UNDERLINE,
        DIAGRAM_SKETCH,
        EQUATION_SOLVE,
        PALM_REJECTED
    }

    data class StylusSample(
        val xCoord: Float,
        val yCoord: Float,
        val pressure: Int,          // 0 to 4095
        val tiltAngleDeg: Float,    // 0 to 90 degrees
        val azimuthDeg: Float,      // 0 to 360 degrees
        val contactRadiusMm: Float, // Contact blob size
        val timestampMs: Long = System.currentTimeMillis()
    )

    data class ProcessedStrokePoint(
        val x: Float,
        val y: Float,
        val normalizedPressure: Float, // 0.0f to 1.0f
        val strokeWidthDp: Float,
        val intent: StrokeIntent,
        val isValidTip: Boolean
    )

    data class StrokeMetrics(
        val sampleCount: Int,
        val averagePressure: Float,
        val peakPressure: Int,
        val strokeLengthMm: Float,
        val primaryIntent: StrokeIntent
    )

    /**
     * Filters raw digitizer input, applying palm rejection and pressure normalization.
     */
    fun processDigitizerSample(sample: StylusSample): ProcessedStrokePoint {
        // 1. Palm Rejection Check: if contact radius is too large, it is a palm/wrist touch
        if (sample.contactRadiusMm > PALM_REJECTION_MAX_RADIUS_MM) {
            return ProcessedStrokePoint(
                x = sample.xCoord,
                y = sample.yCoord,
                normalizedPressure = 0f,
                strokeWidthDp = 0f,
                intent = StrokeIntent.PALM_REJECTED,
                isValidTip = false
            )
        }

        // 2. Hover detection
        if (sample.pressure < MIN_WRITING_PRESSURE) {
            return ProcessedStrokePoint(
                x = sample.xCoord,
                y = sample.yCoord,
                normalizedPressure = 0f,
                strokeWidthDp = 1.0f,
                intent = StrokeIntent.HOVER,
                isValidTip = true
            )
        }

        // 3. Normalization (0.0 to 1.0) and dynamic brush width calculation
        val clampedPressure = sample.pressure.coerceIn(0, MAX_PRESSURE_LEVEL)
        val normalized = clampedPressure.toFloat() / MAX_PRESSURE_LEVEL.toFloat()
        val calculatedWidth = 1.5f + (normalized * 6.5f) // 1.5dp to 8.0dp stroke width

        val intent = when {
            sample.tiltAngleDeg > 45f -> StrokeIntent.DIAGRAM_SKETCH
            normalized > 0.65f -> StrokeIntent.EQUATION_SOLVE
            else -> StrokeIntent.ANNOTATION_UNDERLINE
        }

        return ProcessedStrokePoint(
            x = sample.xCoord,
            y = sample.yCoord,
            normalizedPressure = normalized,
            strokeWidthDp = calculatedWidth,
            intent = intent,
            isValidTip = true
        )
    }

    /**
     * Analyzes a sequence of points to calculate aggregate stroke statistics.
     */
    fun analyzeStrokeSequence(samples: List<StylusSample>): StrokeMetrics {
        if (samples.isEmpty()) {
            return StrokeMetrics(0, 0f, 0, 0f, StrokeIntent.HOVER)
        }

        var totalPressure = 0
        var maxPressure = 0
        var totalDistMm = 0f

        for (i in samples.indices) {
            val curr = samples[i]
            totalPressure += curr.pressure
            if (curr.pressure > maxPressure) maxPressure = curr.pressure

            if (i > 0) {
                val prev = samples[i - 1]
                val dx = curr.xCoord - prev.xCoord
                val dy = curr.yCoord - prev.yCoord
                totalDistMm += sqrt(dx * dx + dy * dy)
            }
        }

        val avgPressure = totalPressure.toFloat() / samples.size.toFloat()
        val dominantIntent = when {
            avgPressure < MIN_WRITING_PRESSURE -> StrokeIntent.HOVER
            maxPressure > 3000 -> StrokeIntent.EQUATION_SOLVE
            else -> StrokeIntent.ANNOTATION_UNDERLINE
        }

        return StrokeMetrics(
            sampleCount = samples.size,
            averagePressure = avgPressure,
            peakPressure = maxPressure,
            strokeLengthMm = totalDistMm,
            primaryIntent = dominantIntent
        )
    }
}
