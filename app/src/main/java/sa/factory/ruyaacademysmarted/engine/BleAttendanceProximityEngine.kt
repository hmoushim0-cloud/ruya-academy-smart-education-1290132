package sa.factory.ruyaacademysmarted.engine

import kotlin.math.pow

/**
 * BLE Proximity Beacon and Smart Classroom Attendance Engine.
 * Evaluates indoor RF signal propagation using log-distance path loss,
 * verifies student physical presence in academy halls, and logs verified attendance.
 */
class BleAttendanceProximityEngine {

    companion object {
        // Standard Saudi Ministry of Education / Academy Hall Beacon UUID
        const val ACADEMY_BEACON_UUID = "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0"
        const val DEFAULT_TX_POWER_1M = -59 // RSSI at 1 meter distance in dBm
        const val PATH_LOSS_EXPONENT = 2.2  // Indoor academy classroom attenuation factor
    }

    enum class PresenceStatus {
        OUT_OF_RANGE,
        PROXIMITY_DETECTED,
        CLASSROOM_VERIFIED,
        ATTENDANCE_CONFIRMED
    }

    data class BleBeaconSignal(
        val uuid: String,
        val major: Int,      // Classroom / Academy Hall ID
        val minor: Int,      // Desk / Section ID
        val rssiDbm: Int,
        val txPower1mDbm: Int = DEFAULT_TX_POWER_1M,
        val timestampMs: Long = System.currentTimeMillis()
    )

    data class AttendanceVerificationRecord(
        val studentId: String,
        val classroomId: Int,
        val estimatedDistanceMeters: Double,
        val presenceStatus: PresenceStatus,
        val verificationCode: String,
        val isVerified: Boolean
    )

    data class TahsiliCourseProgress(
        val courseName: String = "Tahsili Biology & Ecology Exam Prep",
        val totalQuestions: Int = 192,
        val reviewedQuestions: Int,
        val totalLessons: Int = 80,
        val completedLessons: Int,
        val masteryScorePct: Double
    )

    /**
     * Estimates distance in meters based on RSSI and 1-meter reference Tx Power.
     * Formula: d = 10 ^ ((TxPower - RSSI) / (10 * n))
     */
    fun estimateDistanceMeters(rssiDbm: Int, txPowerDbm: Int = DEFAULT_TX_POWER_1M): Double {
        val exponent = (txPowerDbm - rssiDbm).toDouble() / (10.0 * PATH_LOSS_EXPONENT)
        return 10.0.pow(exponent)
    }

    /**
     * Evaluates attendance state based on distance and RSSI signal stability.
     */
    fun verifyStudentPresence(
        studentId: String,
        beacon: BleBeaconSignal,
        dwellSeconds: Int = 20
    ): AttendanceVerificationRecord {
        val distance = estimateDistanceMeters(beacon.rssiDbm, beacon.txPower1mDbm)

        val status = when {
            distance > 8.0 || beacon.rssiDbm < -85 -> PresenceStatus.OUT_OF_RANGE
            distance > 2.5 -> PresenceStatus.PROXIMITY_DETECTED
            dwellSeconds >= 15 -> PresenceStatus.ATTENDANCE_CONFIRMED
            else -> PresenceStatus.CLASSROOM_VERIFIED
        }

        val isConfirmed = status == PresenceStatus.ATTENDANCE_CONFIRMED
        val verificationToken = if (isConfirmed) {
            "ATT-%04d-%04d".format(beacon.major, (studentId.hashCode() and 0xFFFF))
        } else {
            "PENDING_PROXIMITY"
        }

        return AttendanceVerificationRecord(
            studentId = studentId,
            classroomId = beacon.major,
            estimatedDistanceMeters = distance,
            presenceStatus = status,
            verificationCode = verificationToken,
            isVerified = isConfirmed
        )
    }

    /**
     * Tracks the 192 Tahsili biology questions and 80 lesson review curriculum.
     */
    fun evaluateTahsiliProgress(reviewedCount: Int, completedLessonCount: Int): TahsiliCourseProgress {
        val clampedQuestions = reviewedCount.coerceIn(0, 192)
        val clampedLessons = completedLessonCount.coerceIn(0, 80)
        val questionPct = (clampedQuestions.toDouble() / 192.0) * 100.0
        val lessonPct = (clampedLessons.toDouble() / 80.0) * 100.0
        val compositeScore = (questionPct * 0.6) + (lessonPct * 0.4)

        return TahsiliCourseProgress(
            reviewedQuestions = clampedQuestions,
            completedLessons = clampedLessons,
            masteryScorePct = compositeScore
        )
    }
}
