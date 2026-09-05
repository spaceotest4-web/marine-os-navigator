package com.example.ferrostartnew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stadiamaps.ferrostar.googleplayservices.FusedNavigationLocationProvider
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Can I go out?" - wind, gusts, and waves at the phone's position, now and
 * for the next 12 hours, with the same comfort rating the website uses.
 * Data straight from Open-Meteo (free, no key).
 */

data class HourRow(
    val time: String,
    val windKn: Double?,
    val gustKn: Double?,
    val waveM: Double?,
    val comfort: Triple<Int, String, Long>?,
)

@Composable
fun WeatherScreen() {
  val context = LocalContext.current
  var rows by remember { mutableStateOf<List<HourRow>?>(null) }
  var place by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    try {
      withContext(Dispatchers.IO) {
        val provider = FusedNavigationLocationProvider(context)
        val loc =
            withTimeoutOrNull(15_000) { provider.locationUpdates(2000L).first() }
                ?: throw MarineApi.ApiException("No GPS fix yet - step outside and try again.")
        val lat = loc.coordinates.lat
        val lng = loc.coordinates.lng
        place = String.format("%.3f, %.3f", lat, lng)

        val wind =
            MarineApi.publicGetJson(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&hourly=wind_speed_10m,wind_gusts_10m&wind_speed_unit=kn&forecast_days=2&timezone=auto")
        val wave =
            try {
              MarineApi.publicGetJson(
                  "https://marine-api.open-meteo.com/v1/marine?latitude=$lat&longitude=$lng&hourly=wave_height,wave_period&forecast_days=2&timezone=auto")
            } catch (_: Exception) {
              null // inland: no wave model, wind still works
            }

        val hourly = wind.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val winds = hourly.getJSONArray("wind_speed_10m")
        val gusts = hourly.getJSONArray("wind_gusts_10m")
        val waveHourly = wave?.optJSONObject("hourly")
        val waves = waveHourly?.optJSONArray("wave_height")
        val periods = waveHourly?.optJSONArray("wave_period")

        // Find the current hour, then take 12 rows from there.
        val nowPrefix = java.time.LocalDateTime.now().toString().substring(0, 13)
        var start = 0
        for (i in 0 until times.length()) {
          if (times.getString(i).startsWith(nowPrefix)) {
            start = i
            break
          }
        }
        val out = mutableListOf<HourRow>()
        for (i in start until minOf(start + 12, times.length())) {
          val w = winds.optDouble(i).takeIf { it.isFinite() }
          val g = gusts.optDouble(i).takeIf { it.isFinite() }
          val h = waves?.optDouble(i)?.takeIf { it.isFinite() }
          val p = periods?.optDouble(i)?.takeIf { it.isFinite() }
          out.add(
              HourRow(
                  time = times.getString(i).substring(11, 16),
                  windKn = w,
                  gustKn = g,
                  waveM = h,
                  comfort = GeoUtils.comfortRating(w, g, h, p),
              ))
        }
        rows = out
      }
    } catch (e: Exception) {
      error = e.message ?: "Could not load the forecast"
    }
  }

  Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
      Text("Weather here", style = MaterialTheme.typography.headlineMedium)
      Text(
          if (place.isBlank()) "At your GPS position" else "At $place",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(14.dp))

      error?.let {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
              it,
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(12.dp),
          )
        }
      }

      when (val list = rows) {
        null ->
            if (error == null) {
              Column(
                  modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Getting your position and forecast…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
        else -> {
          val now = list.firstOrNull()
          if (now != null) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
              Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Column(Modifier.weight(1f)) {
                    Text("Right now", style = MaterialTheme.typography.titleMedium)
                    Text(
                        buildString {
                          append("${now.windKn?.toInt() ?: "—"} kn wind")
                          now.gustKn?.let { append(" · gusts ${it.toInt()}") }
                          now.waveM?.let { append(" · ${String.format("%.1f", it)} m waves") }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  now.comfort?.let { (_, label, color) ->
                    Surface(color = Color(color), shape = RoundedCornerShape(999.dp)) {
                      Text(
                          label,
                          color = Color.White,
                          style = MaterialTheme.typography.labelLarge,
                          modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                      )
                    }
                  }
                }
              }
            }
          }
          Spacer(Modifier.height(12.dp))
          Text(
              "Next 12 hours",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(6.dp))
          LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list) { r ->
              Card(
                  shape = RoundedCornerShape(12.dp),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                  modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                  Text(r.time, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(52.dp))
                  Text(
                      buildString {
                        append("${r.windKn?.toInt() ?: "—"} kn")
                        r.gustKn?.let { append(" (g${it.toInt()})") }
                        r.waveM?.let { append("  ·  ${String.format("%.1f", it)} m") }
                      },
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.weight(1f),
                  )
                  r.comfort?.let { (_, label, color) ->
                    Box(
                        modifier = Modifier.size(10.dp).background(Color(color), CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = Color(color))
                  }
                }
              }
            }
            item { Spacer(Modifier.height(16.dp)) }
          }
        }
      }
    }
  }
}
