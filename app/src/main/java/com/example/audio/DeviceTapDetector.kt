package com.example.audio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

enum class DeviceTapAction(val label: String) {
    RANDOMIZE_OPTIONS("Randomize Next Options"),
    MUTATE_SCOPES("Mutate Within Scopes"),
    RANDOM_PRESET("Switch Random Preset"),
}

data class DeviceTapConfig(
    val enabled: Boolean = true,
    val action: DeviceTapAction = DeviceTapAction.RANDOMIZE_OPTIONS,
    val sensitivityThreshold: Float = 5.5f,
    val cooldownMs: Long = 650L,
)

/**
 * Hardware accelerometer impulse detector for "tap the device to randomly choose
 * the next set of visual options".
 *
 * Uses Android's [Sensor.TYPE_LINEAR_ACCELERATION] (with automatic high-pass
 * [Sensor.TYPE_ACCELEROMETER] fallback) and requires zero dangerous permissions.
 */
class DeviceTapDetector(
    context: Context,
    private val onTapDetected: () -> Unit,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val linearSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelSensor: Sensor? = if (linearSensor == null) {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    } else null

    @Volatile
    var config: DeviceTapConfig = DeviceTapConfig()

    private var isListening = false
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var warmupSamples = 0
    private var lastTriggerMs = 0L

    val hasHardwareSensor: Boolean
        get() = linearSensor != null || accelSensor != null

    fun start() {
        if (isListening || !config.enabled) return
        val sensor = linearSensor ?: accelSensor ?: return
        warmupSamples = 0
        gravityX = 0f
        gravityY = 0f
        gravityZ = 0f
        val registered = sensorManager?.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_GAME,
        ) ?: false
        isListening = registered
    }

    fun stop() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
    }

    fun syncListeningState() {
        if (config.enabled) start() else stop()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val ev = event ?: return
        if (!config.enabled || ev.values.size < 3) return
        val isLinear = ev.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION
        if (processSample(ev.values[0], ev.values[1], ev.values[2], isLinear, System.currentTimeMillis())) {
            onTapDetected()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * Pure impulse detection logic usable both by live sensor callbacks and JVM unit tests.
     */
    fun processSample(
        x: Float,
        y: Float,
        z: Float,
        isLinearAcceleration: Boolean,
        timestampMs: Long,
    ): Boolean {
        if (!config.enabled) return false

        val linearMag: Float = if (isLinearAcceleration) {
            sqrt(x * x + y * y + z * z)
        } else {
            val alpha = 0.8f
            if (warmupSamples == 0) {
                gravityX = x
                gravityY = y
                gravityZ = z
            } else {
                gravityX = alpha * gravityX + (1f - alpha) * x
                gravityY = alpha * gravityY + (1f - alpha) * y
                gravityZ = alpha * gravityZ + (1f - alpha) * z
            }
            warmupSamples++
            if (warmupSamples < 4) return false
            val lx = x - gravityX
            val ly = y - gravityY
            val lz = z - gravityZ
            sqrt(lx * lx + ly * ly + lz * lz)
        }

        val threshold = config.sensitivityThreshold.coerceIn(2.0f, 15.0f)
        if (linearMag >= threshold && (timestampMs - lastTriggerMs) >= config.cooldownMs) {
            lastTriggerMs = timestampMs
            return true
        }
        return false
    }
}
