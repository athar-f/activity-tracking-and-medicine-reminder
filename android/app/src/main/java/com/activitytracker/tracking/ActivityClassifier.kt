package com.activitytracker.tracking

import com.activitytracker.data.ActivityType
import kotlin.math.sqrt

/**
 * Heuristic activity classifier fusing accelerometer and gyroscope data.
 *
 * For every sliding window (2 s, re-evaluated every second) it computes:
 *  - std-dev of the acceleration magnitude (motion energy, gravity-independent),
 *  - step cadence from peaks in the magnitude signal,
 *  - mean rotation rate from the gyroscope (phone sway while moving).
 *
 * Thresholds were chosen from typical pocket/hand-held signal ranges:
 *  stationary: accel σ < 0.6 m/s² and low rotation
 *  walking:    σ ≈ 0.6–4.5 m/s², cadence ≈ 1.2–2.4 steps/s
 *  running:    σ > 4.5 m/s² or cadence > 2.4 steps/s with σ > 3
 *
 * A new label must win [CONFIRMATION_WINDOWS] consecutive windows before it replaces the
 * current one, which removes flicker from single jolts (e.g. picking up the phone).
 */
class ActivityClassifier {

  data class Result(val activity: ActivityType, val intensity: Double, val cadenceHz: Double)

  private val accel = RingBuffer(WINDOW_CAPACITY)
  private val gyro = RingBuffer(WINDOW_CAPACITY)

  private var current = ActivityType.STATIONARY
  private var candidate = ActivityType.STATIONARY
  private var candidateCount = 0

  @Synchronized
  fun addAccelerometer(timestampNs: Long, x: Float, y: Float, z: Float) {
    accel.add(timestampNs, sqrt((x * x + y * y + z * z).toDouble()))
  }

  @Synchronized
  fun addGyroscope(timestampNs: Long, x: Float, y: Float, z: Float) {
    gyro.add(timestampNs, sqrt((x * x + y * y + z * z).toDouble()))
  }

  /** Evaluates the latest window. [gpsSpeed] (m/s, nullable) is used only as a tie-breaker. */
  @Synchronized
  fun evaluate(gpsSpeed: Float?): Result {
    val accelValues = accel.valuesWithin(WINDOW_NS)
    if (accelValues.size < MIN_SAMPLES) return Result(current, 0.0, 0.0)

    val mean = accelValues.average()
    val std = sqrt(accelValues.sumOf { (it - mean) * (it - mean) } / accelValues.size)
    val cadence = cadenceHz(accelValues, mean, std)
    val gyroValues = gyro.valuesWithin(WINDOW_NS)
    val rotation = if (gyroValues.isEmpty()) 0.0 else gyroValues.average()

    val raw =
        when {
          std < STATIONARY_STD && rotation < STATIONARY_ROTATION -> ActivityType.STATIONARY
          std < STATIONARY_STD * 1.5 && cadence < MIN_WALK_CADENCE -> ActivityType.STATIONARY
          std > RUNNING_STD -> ActivityType.RUNNING
          cadence > RUNNING_CADENCE && std > RUNNING_STD_WITH_CADENCE -> ActivityType.RUNNING
          gpsSpeed != null && gpsSpeed > RUNNING_SPEED && std > RUNNING_STD_WITH_CADENCE ->
              ActivityType.RUNNING
          else -> ActivityType.WALKING
        }

    if (raw == current) {
      candidateCount = 0
    } else if (raw == candidate) {
      if (++candidateCount >= CONFIRMATION_WINDOWS) {
        current = raw
        candidateCount = 0
      }
    } else {
      candidate = raw
      candidateCount = 1
    }
    return Result(current, std, cadence)
  }

  @Synchronized
  fun reset() {
    accel.clear()
    gyro.clear()
    current = ActivityType.STATIONARY
    candidate = ActivityType.STATIONARY
    candidateCount = 0
  }

  /** Counts peaks above mean + 0.5σ separated by at least [MIN_STEP_INTERVAL_NS]. */
  private fun cadenceHz(values: List<Double>, mean: Double, std: Double): Double {
    if (std < STATIONARY_STD / 2) return 0.0
    val timestamps = accel.timestampsWithin(WINDOW_NS)
    val threshold = mean + std * 0.5
    var peaks = 0
    var lastPeak = Long.MIN_VALUE
    for (i in 1 until values.size - 1) {
      val isPeak = values[i] > threshold && values[i] >= values[i - 1] && values[i] > values[i + 1]
      if (isPeak && timestamps[i] - lastPeak >= MIN_STEP_INTERVAL_NS) {
        peaks++
        lastPeak = timestamps[i]
      }
    }
    val spanSeconds = (timestamps.last() - timestamps.first()) / 1e9
    return if (spanSeconds > 0) peaks / spanSeconds else 0.0
  }

  /** Fixed-capacity buffer of (timestamp, value) pairs. */
  private class RingBuffer(private val capacity: Int) {
    private val ts = LongArray(capacity)
    private val values = DoubleArray(capacity)
    private var start = 0
    private var size = 0

    fun add(timestampNs: Long, value: Double) {
      val index = (start + size) % capacity
      ts[index] = timestampNs
      values[index] = value
      if (size < capacity) size++ else start = (start + 1) % capacity
    }

    fun clear() {
      start = 0
      size = 0
    }

    private fun firstIndexWithin(windowNs: Long): Int {
      if (size == 0) return 0
      val newest = ts[(start + size - 1) % capacity]
      var offset = 0
      while (offset < size && newest - ts[(start + offset) % capacity] > windowNs) offset++
      return offset
    }

    fun valuesWithin(windowNs: Long): List<Double> {
      val from = firstIndexWithin(windowNs)
      return (from until size).map { values[(start + it) % capacity] }
    }

    fun timestampsWithin(windowNs: Long): List<Long> {
      val from = firstIndexWithin(windowNs)
      return (from until size).map { ts[(start + it) % capacity] }
    }
  }

  private companion object {
    const val WINDOW_NS = 2_000_000_000L
    const val WINDOW_CAPACITY = 512 // > 2 s at SENSOR_DELAY_GAME (~50–100 Hz)
    const val MIN_SAMPLES = 20
    const val MIN_STEP_INTERVAL_NS = 250_000_000L // ≤ 4 steps/s

    const val STATIONARY_STD = 0.6
    const val STATIONARY_ROTATION = 0.35 // rad/s
    const val MIN_WALK_CADENCE = 1.0
    const val RUNNING_STD = 4.5
    const val RUNNING_STD_WITH_CADENCE = 3.0
    const val RUNNING_CADENCE = 2.4
    const val RUNNING_SPEED = 2.6f // m/s ≈ 9.4 km/h
    const val CONFIRMATION_WINDOWS = 2
  }
}
