package sa.factory.ruyaacademysmarted.hardware

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import sa.factory.ruyaacademysmarted.engine.BleAttendanceProximityEngine
import sa.factory.ruyaacademysmarted.engine.SmartStylusDigitizerProtocolEngine
import kotlin.random.Random

data class HardwareTelemetryFrame(
    val peripheralName: String,
    val connectionState: String,
    val livePayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HardwareSensorDriver(
    private val stylusEngine: SmartStylusDigitizerProtocolEngine = SmartStylusDigitizerProtocolEngine(),
    private val attendanceEngine: BleAttendanceProximityEngine = BleAttendanceProximityEngine()
) {
    private val peripheral = "Smart Stylus Digitizer 4096-Level + BLE Beacon"

    fun observePeripheralStream(): Flow<HardwareTelemetryFrame> = flow {
        emit(HardwareTelemetryFrame(peripheral, "CONNECTING", "HID Digitizer & BLE Proximity Beacon Initializing..."))
        delay(250)

        // Initial Telemetry Frame
        val initialSample = stylusEngine.processDigitizerSample(
            SmartStylusDigitizerProtocolEngine.StylusSample(
                xCoord = 540.0f,
                yCoord = 960.0f,
                pressure = 2048,
                tiltAngleDeg = 25.0f,
                azimuthDeg = 180.0f,
                contactRadiusMm = 1.2f
            )
        )

        val initialBeacon = attendanceEngine.verifyStudentPresence(
            studentId = "STU-9941",
            beacon = BleAttendanceProximityEngine.BleBeaconSignal(
                uuid = BleAttendanceProximityEngine.ACADEMY_BEACON_UUID,
                major = 101,
                minor = 12,
                rssiDbm = -62
            ),
            dwellSeconds = 25
        )

        val courseProgress = attendanceEngine.evaluateTahsiliProgress(142, 64)

        emit(HardwareTelemetryFrame(
            peripheral,
            "ONLINE",
            "Stylus #01 [OK] | Pressure: ${initialSample.normalizedPressure * 4095} / 4095 (${String.format("%.1f", initialSample.strokeWidthDp)}dp) | BLE Hall #101: ${initialBeacon.presenceStatus.name} (${String.format("%.2f", initialBeacon.estimatedDistanceMeters)}m) | Tahsili: ${courseProgress.reviewedQuestions}/${courseProgress.totalQuestions} Qs"
        ))

        var frameCounter = 300
        while (true) {
            delay(1500)
            frameCounter++

            val rawPressure = 1500 + Random.nextInt(0, 2000)
            val tilt = 15.0f + Random.nextInt(0, 30).toFloat()
            val sample = stylusEngine.processDigitizerSample(
                SmartStylusDigitizerProtocolEngine.StylusSample(
                    xCoord = 500f + Random.nextInt(-50, 50),
                    yCoord = 800f + Random.nextInt(-50, 50),
                    pressure = rawPressure,
                    tiltAngleDeg = tilt,
                    azimuthDeg = 175.0f,
                    contactRadiusMm = 1.1f
                )
            )

            val rssi = -60 + Random.nextInt(-6, 8)
            val beaconRecord = attendanceEngine.verifyStudentPresence(
                studentId = "STU-9941",
                beacon = BleAttendanceProximityEngine.BleBeaconSignal(
                    uuid = BleAttendanceProximityEngine.ACADEMY_BEACON_UUID,
                    major = 101,
                    minor = 12,
                    rssiDbm = rssi
                ),
                dwellSeconds = 30
            )

            val livePayload = "Stylus: ACTIVE [OK] | Pressure: $rawPressure/4095 (${sample.intent.name}) | BLE Beacon: ${beaconRecord.verificationCode} (${String.format("%.2f", beaconRecord.estimatedDistanceMeters)}m) | Progress: 142/192 Qs Reviewed"

            emit(HardwareTelemetryFrame(
                peripheralName = peripheral,
                connectionState = "ACTIVE_STREAMING",
                livePayload = livePayload
            ))
        }
    }
}
