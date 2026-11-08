# تطبيق أكاديمية رؤية للتعليم والتحصيلي (`ruya-academy-smart-education-1290132`)
> منظومة smart_education المتكاملة مع ربط Smart Stylus Digitizer 4096-Level + BLE Beacon

- **Project ID**: `#1290132`
- **Commercial Archetype**: `smart_education` (Tech academies, interactive live classrooms, Tahsili biology and ecology exam prep)
- **Bound Hardware Peripheral**: `Smart Stylus Digitizer 4096-Level + BLE Beacon`
- **Package Name**: `sa.factory.ruyaacademysmarted`
- **Zero-Emoji Invariant**: Strictly Enforced (100% Vector Icons)

---

## [Sidekick Autonomous Update]

### 1. Architectural & Production Engines
- **`SmartStylusDigitizerProtocolEngine`**:
  * High-precision 4096-level pressure stylus digitizer protocol engine ($0$ to $4095$ levels).
  * Sub-pixel $(X, Y)$ coordinate tracking with dynamic brush stroke width calculations ($1.5$dp to $8.0$dp).
  * Intelligent capacitive Palm Rejection filter rejecting contact blobs exceeding $3.5$mm radius.
  * Stylus tilt ($0^\circ - 90^\circ$) and azimuth orientation tracking distinguishing between underlining, sketching, and equation solving.
  * Aggregate stroke statistics engine (average pressure, peak force, total path length in mm).

- **`BleAttendanceProximityEngine`**:
  * Bluetooth Low Energy (BLE) classroom proximity advertising scanner with log-distance path loss RF model ($d = 10^{\frac{Tx - RSSI}{10n}}$).
  * Automated 4-stage student presence state machine (`OUT_OF_RANGE`, `PROXIMITY_DETECTED`, `CLASSROOM_VERIFIED`, `ATTENDANCE_CONFIRMED`).
  * Cryptographic student check-in token generation upon 15-second classroom presence verification.
  * Curriculum progress tracker for the 192 Saudi Tahsili Biology and Ecology questions and 80 interactive lessons.

### 2. UI & Driver Modernization
- **`HardwareSensorDriver`**: Dual-engine telemetry loop streaming active stylus pressure, stroke intent, BLE classroom beacon proximity, and Tahsili curriculum progress into ViewModel StateFlows.
- **`MainActivity` & Compose UI**: Fixed missing typography imports, resolved single-quote template hazards, migrated deprecated `Divider` to `HorizontalDivider`, and eliminated non-standard unicode glyphs (checkmarks replaced with `[OK]`, crosses with `[X]`, bullets with `|`).
- **`RuyaAcademyViewModel`**: Enhanced `checkout(paymentMethod: String)` signature for dynamic Mada / Apple Pay POS processing.
- **`AndroidManifest.xml`**: Modernized application label to `أكاديمية رؤية للتعليم والتحصيلي - Ruya Edu`.

### 3. Unit Regression Test Suite
- Comprehensive 10-case JUnit test suite in `sa.factory.ruyaacademysmarted.RuyaAcademyRegressionTest`:
  * `test01_stylusPalmRejectionFilter`
  * `test02_stylusPressureLevelNormalizationAndBrushWidth`
  * `test03_stylusIntentClassificationByTiltAndPressure`
  * `test04_stylusStrokeMetricsAggregateAnalysis`
  * `test05_bleLogDistancePathLossEstimation`
  * `test06_bleAttendanceConfirmedInsideClassroom`
  * `test07_bleAttendanceOutOfRangeDetection`
  * `test08_tahsiliCurriculumProgressCalculation`
  * `test09_hardwareDriverDualEngineStream`
  * `test10_repositoryCartAndVatCalculation`

### 4. Build and Test Instructions
```bash
# Build Debug APK
./gradlew assembleDebug

# Run Unit Regression Tests
./gradlew testDebugUnitTest
```
