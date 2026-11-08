package sa.factory.ruyaacademysmarted

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import sa.factory.ruyaacademysmarted.data.repository.RuyaAcademyRepositoryImpl
import sa.factory.ruyaacademysmarted.engine.BleAttendanceProximityEngine
import sa.factory.ruyaacademysmarted.engine.SmartStylusDigitizerProtocolEngine
import sa.factory.ruyaacademysmarted.hardware.HardwareSensorDriver
import sa.factory.ruyaacademysmarted.model.RuyaAcademyItem

class RuyaAcademyRegressionTest {

    private val stylusEngine = SmartStylusDigitizerProtocolEngine()
    private val attendanceEngine = BleAttendanceProximityEngine()

    @Test
    fun test01_stylusPalmRejectionFilter() {
        val palmTouch = SmartStylusDigitizerProtocolEngine.StylusSample(
            xCoord = 350.0f,
            yCoord = 720.0f,
            pressure = 1800,
            tiltAngleDeg = 15.0f,
            azimuthDeg = 90.0f,
            contactRadiusMm = 5.2f // Exceeds 3.5mm threshold -> Palm touch
        )

        val processed = stylusEngine.processDigitizerSample(palmTouch)
        assertFalse(processed.isValidTip)
        assertEquals(SmartStylusDigitizerProtocolEngine.StrokeIntent.PALM_REJECTED, processed.intent)
        assertEquals(0f, processed.normalizedPressure, 0.001f)
    }

    @Test
    fun test02_stylusPressureLevelNormalizationAndBrushWidth() {
        val validStroke = SmartStylusDigitizerProtocolEngine.StylusSample(
            xCoord = 200.0f,
            yCoord = 400.0f,
            pressure = 2048, // ~50% of 4095
            tiltAngleDeg = 20.0f,
            azimuthDeg = 180.0f,
            contactRadiusMm = 1.2f
        )

        val processed = stylusEngine.processDigitizerSample(validStroke)
        assertTrue(processed.isValidTip)
        assertEquals(0.5001f, processed.normalizedPressure, 0.01f)
        assertTrue(processed.strokeWidthDp > 4.0f && processed.strokeWidthDp < 6.0f)
    }

    @Test
    fun test03_stylusIntentClassificationByTiltAndPressure() {
        // High tilt angle -> Diagram Sketch
        val sketchSample = SmartStylusDigitizerProtocolEngine.StylusSample(
            xCoord = 100f, yCoord = 100f, pressure = 1500,
            tiltAngleDeg = 65.0f, azimuthDeg = 45.0f, contactRadiusMm = 1.0f
        )
        val sketchPoint = stylusEngine.processDigitizerSample(sketchSample)
        assertEquals(SmartStylusDigitizerProtocolEngine.StrokeIntent.DIAGRAM_SKETCH, sketchPoint.intent)

        // Heavy pressure -> Equation Solve
        val equationSample = SmartStylusDigitizerProtocolEngine.StylusSample(
            xCoord = 100f, yCoord = 100f, pressure = 3500,
            tiltAngleDeg = 20.0f, azimuthDeg = 45.0f, contactRadiusMm = 1.0f
        )
        val eqPoint = stylusEngine.processDigitizerSample(equationSample)
        assertEquals(SmartStylusDigitizerProtocolEngine.StrokeIntent.EQUATION_SOLVE, eqPoint.intent)
    }

    @Test
    fun test04_stylusStrokeMetricsAggregateAnalysis() {
        val samples = listOf(
            SmartStylusDigitizerProtocolEngine.StylusSample(0f, 0f, 1000, 20f, 0f, 1.0f),
            SmartStylusDigitizerProtocolEngine.StylusSample(30f, 40f, 2000, 20f, 0f, 1.0f),
            SmartStylusDigitizerProtocolEngine.StylusSample(60f, 80f, 3000, 20f, 0f, 1.0f)
        )

        val metrics = stylusEngine.analyzeStrokeSequence(samples)
        assertEquals(3, metrics.sampleCount)
        assertEquals(2000f, metrics.averagePressure, 0.01f)
        assertEquals(3000, metrics.peakPressure)
        assertEquals(100f, metrics.strokeLengthMm, 0.1f) // 50 + 50
    }

    @Test
    fun test05_bleLogDistancePathLossEstimation() {
        // At RSSI = TxPower (-59 dBm), distance should be exactly 1.0 meter
        val dist1m = attendanceEngine.estimateDistanceMeters(rssiDbm = -59, txPowerDbm = -59)
        assertEquals(1.0, dist1m, 0.01)

        // At weaker RSSI (-75 dBm), distance increases
        val distFar = attendanceEngine.estimateDistanceMeters(rssiDbm = -75, txPowerDbm = -59)
        assertTrue(distFar > 4.0 && distFar < 6.5)
    }

    @Test
    fun test06_bleAttendanceConfirmedInsideClassroom() {
        val beacon = BleAttendanceProximityEngine.BleBeaconSignal(
            uuid = BleAttendanceProximityEngine.ACADEMY_BEACON_UUID,
            major = 104,
            minor = 8,
            rssiDbm = -62
        )

        val record = attendanceEngine.verifyStudentPresence(
            studentId = "STU-5512",
            beacon = beacon,
            dwellSeconds = 20
        )

        assertTrue(record.isVerified)
        assertEquals(BleAttendanceProximityEngine.PresenceStatus.ATTENDANCE_CONFIRMED, record.presenceStatus)
        assertTrue(record.verificationCode.startsWith("ATT-0104-"))
    }

    @Test
    fun test07_bleAttendanceOutOfRangeDetection() {
        val farBeacon = BleAttendanceProximityEngine.BleBeaconSignal(
            uuid = BleAttendanceProximityEngine.ACADEMY_BEACON_UUID,
            major = 104,
            minor = 8,
            rssiDbm = -90 // very weak
        )

        val record = attendanceEngine.verifyStudentPresence(
            studentId = "STU-5512",
            beacon = farBeacon,
            dwellSeconds = 5
        )

        assertFalse(record.isVerified)
        assertEquals(BleAttendanceProximityEngine.PresenceStatus.OUT_OF_RANGE, record.presenceStatus)
        assertEquals("PENDING_PROXIMITY", record.verificationCode)
    }

    @Test
    fun test08_tahsiliCurriculumProgressCalculation() {
        // 96 out of 192 questions (50%), 40 out of 80 lessons (50%) -> 50%
        val progress = attendanceEngine.evaluateTahsiliProgress(
            reviewedCount = 96,
            completedLessonCount = 40
        )

        assertEquals(192, progress.totalQuestions)
        assertEquals(96, progress.reviewedQuestions)
        assertEquals(80, progress.totalLessons)
        assertEquals(40, progress.completedLessons)
        assertEquals(50.0, progress.masteryScorePct, 0.01)
    }

    @Test
    fun test09_hardwareDriverDualEngineStream() = runBlocking {
        val driver = HardwareSensorDriver(stylusEngine, attendanceEngine)
        val frame = driver.observePeripheralStream().first()

        assertEquals("CONNECTING", frame.connectionState)
        assertTrue(frame.peripheralName.contains("Smart Stylus"))
        assertTrue(frame.livePayload.contains("HID Digitizer"))
    }

    @Test
    fun test10_repositoryCartAndVatCalculation() = runBlocking {
        val driver = HardwareSensorDriver(stylusEngine, attendanceEngine)
        val repository = RuyaAcademyRepositoryImpl(driver)

        val item1 = RuyaAcademyItem("edu-1", "باقة مراجعة التحصيلي المكثفة (أحياء وبيئة)", "مراجعة 192 سؤالاً", 350.0, "معتمد", "الخدمات الرئيسية")
        val item2 = RuyaAcademyItem("edu-2", "جلسة حل تفاعلي بالقلم الرقمي", "ساعة تدريب فردية", 150.0, "فوري", "المراقبة الحية")

        repository.addToCart(item1)
        repository.addToCart(item2)

        val order = repository.checkout("Mada Contactless EMV")

        assertEquals(500.0, order.subtotalSar, 0.01)
        assertEquals(75.0, order.vatSar, 0.01) // 15% VAT
        assertEquals(575.0, order.totalSar, 0.01)
        assertEquals("Mada Contactless EMV", order.paymentMethod)
        assertEquals(0, repository.activeCart.value.size)
    }
}
