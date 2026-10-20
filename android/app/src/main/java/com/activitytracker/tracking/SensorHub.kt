package com.activitytracker.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler

/**
 * Registers the motion and environment sensors on a background [Handler] and forwards
 * readings. Light and pressure are exponentially smoothed so the UI does not jitter.
 */
class SensorHub(context: Context, private val handler: Handler) : SensorEventListener {

  interface Listener {
    fun onAccelerometer(timestampNs: Long, x: Float, y: Float, z: Float) {}

    fun onGyroscope(timestampNs: Long, x: Float, y: Float, z: Float) {}
  }

  private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
  private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
  private val light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
  private val pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

  private var listener: Listener? = null

  @Volatile var lightLux: Double? = null
    private set

  @Volatile var pressureHpa: Double? = null
    private set

  val availability: Map<String, Boolean>
    get() =
        mapOf(
            "accelerometer" to (accelerometer != null),
            "gyroscope" to (gyroscope != null),
            "light" to (light != null),
            "pressure" to (pressure != null),
        )

  fun startMotion(listener: Listener) {
    this.listener = listener
    // ~50 Hz is enough for step detection while keeping battery use modest.
    accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, handler) }
    gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, handler) }
  }

  fun startEnvironment() {
    light?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, handler) }
    pressure?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, handler) }
  }

  fun stop() {
    sensorManager.unregisterListener(this)
    listener = null
  }

  override fun onSensorChanged(event: SensorEvent) {
    val v = event.values
    when (event.sensor.type) {
      Sensor.TYPE_ACCELEROMETER -> listener?.onAccelerometer(event.timestamp, v[0], v[1], v[2])
      Sensor.TYPE_GYROSCOPE -> listener?.onGyroscope(event.timestamp, v[0], v[1], v[2])
      Sensor.TYPE_LIGHT -> lightLux = smooth(lightLux, v[0].toDouble(), 0.3)
      Sensor.TYPE_PRESSURE -> pressureHpa = smooth(pressureHpa, v[0].toDouble(), 0.1)
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

  private fun smooth(previous: Double?, next: Double, alpha: Double): Double =
      if (previous == null) next else previous + alpha * (next - previous)
}
