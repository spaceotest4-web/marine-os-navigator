package com.example.ferrostartnew

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Small geo helpers shared by the anchor alarm, MOB overlay, and fuel list. */
object GeoUtils {

  private const val EARTH_RADIUS_M = 6_371_000.0

  fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val h =
        sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
  }

  /** True bearing in degrees from point 1 toward point 2 (0-359). */
  fun bearingDeg(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
    val y = sin(Math.toRadians(lng2 - lng1)) * cos(Math.toRadians(lat2))
    val x =
        cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
            sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(Math.toRadians(lng2 - lng1))
    val deg = Math.toDegrees(atan2(y, x))
    return ((deg + 360) % 360).toInt()
  }

  fun metersToNmText(m: Double): String {
    val nm = m / 1852.0
    return if (nm < 0.1) "${m.toInt()} m" else String.format("%.2f nm", nm)
  }

  /**
   * Sea-state comfort rating, ported from the web app's comfort.js.
   * Returns Triple(level 0-5, label, hex color) or null without usable input.
   */
  fun comfortRating(windKn: Double?, gustKn: Double?, waveM: Double?, wavePeriod: Double?): Triple<Int, String, Long>? {
    val labels = listOf("Calm", "Comfortable", "Moderate", "Lively", "Rough", "Severe")
    val colors = listOf(0xFF059669, 0xFF10B981, 0xFF84CC16, 0xFFF59E0B, 0xFFEA580C, 0xFFDC2626)
    fun levelFor(value: Double, thresholds: List<Double>): Int {
      thresholds.forEachIndexed { i, t -> if (value < t) return i }
      return thresholds.size
    }
    var level = 0
    var known = false
    if (windKn != null && windKn.isFinite()) {
      known = true
      level = maxOf(level, levelFor(windKn, listOf(8.0, 13.0, 18.0, 24.0, 31.0)))
    }
    if (waveM != null && waveM.isFinite()) {
      known = true
      level = maxOf(level, levelFor(waveM, listOf(0.4, 0.8, 1.4, 2.2, 3.2)))
    }
    if (!known) return null
    if (wavePeriod != null && waveM != null && waveM > 0.3 && wavePeriod < 4 + 2 * waveM) level += 1
    if (gustKn != null && windKn != null && gustKn - windKn > 10) level += 1
    level = minOf(level, 5)
    return Triple(level, labels[level], colors[level])
  }
}
