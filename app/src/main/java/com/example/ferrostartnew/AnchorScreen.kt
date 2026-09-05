package com.example.ferrostartnew

import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.composeui.runtime.KeepScreenOnDisposableEffect
import com.stadiamaps.ferrostar.googleplayservices.FusedNavigationLocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/**
 * Anchor alarm: drop the anchor point at the current GPS position, choose a
 * swing radius, and the phone alarms (sound + red screen) if the boat drifts
 * outside it. Keeps the screen on while watching. V1 runs with the app in
 * the foreground - keep the phone plugged in overnight.
 */
@Composable
fun AnchorScreen() {
  val context = LocalContext.current
  var anchor by remember { mutableStateOf<Pair<Double, Double>?>(null) }
  var radiusM by remember { mutableStateOf(40f) }
  var current by remember { mutableStateOf<Pair<Double, Double>?>(null) }
  var watching by remember { mutableStateOf(false) }
  var alarming by remember { mutableStateOf(false) }
  var status by remember { mutableStateOf("") }

  val ringtone: Ringtone? = remember {
    try {
      RingtoneManager.getRingtone(
          context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
    } catch (_: Exception) {
      null
    }
  }

  if (watching) KeepScreenOnDisposableEffect()

  DisposableEffect(Unit) { onDispose { try { ringtone?.stop() } catch (_: Exception) {} } }

  // Live GPS while watching: distance from the anchor decides the alarm.
  LaunchedEffect(watching) {
    if (!watching) {
      alarming = false
      try { ringtone?.stop() } catch (_: Exception) {}
      return@LaunchedEffect
    }
    withContext(Dispatchers.IO) {
      val provider = FusedNavigationLocationProvider(context)
      provider.locationUpdates(5000L).collectLatest { loc ->
        val lat = loc.coordinates.lat
        val lng = loc.coordinates.lng
        current = Pair(lat, lng)
        val a = anchor ?: return@collectLatest
        val dist = GeoUtils.haversineMeters(a.first, a.second, lat, lng)
        status = "${dist.toInt()} m from anchor (limit ${radiusM.toInt()} m)"
        if (dist > radiusM) {
          alarming = true
          try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ringtone?.isLooping = true
            if (ringtone?.isPlaying != true) ringtone?.play()
          } catch (_: Exception) {}
        } else if (alarming) {
          // Back inside the circle: quiet down but stay armed.
          alarming = false
          try { ringtone?.stop() } catch (_: Exception) {}
        }
      }
    }
  }

  Surface(
      color =
          if (alarming) MaterialTheme.colorScheme.errorContainer
          else MaterialTheme.colorScheme.background,
      modifier = Modifier.fillMaxSize(),
  ) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
      Text("Anchor alarm", style = MaterialTheme.typography.headlineMedium)
      Text(
          "Drop the pin where you anchor - the phone alarms if you drag outside the circle.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(16.dp))

      if (alarming) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) {
          Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "DRAGGING ANCHOR",
                color = MaterialTheme.colorScheme.onError,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                status,
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
        Spacer(Modifier.height(12.dp))
      }

      Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth(),
      ) {
        Column(Modifier.padding(18.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("Swing radius", style = MaterialTheme.typography.titleSmall)
              Text(
                  "${radiusM.toInt()} m - rode length plus margin",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Slider(
              value = radiusM,
              onValueChange = { radiusM = it },
              valueRange = 15f..150f,
              enabled = !watching,
          )

          anchor?.let { a ->
            Text(
                "Anchor set at ${String.format("%.5f", a.first)}, ${String.format("%.5f", a.second)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (watching && status.isNotBlank() && !alarming) {
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
          }

          Spacer(Modifier.height(12.dp))
          if (!watching) {
            Button(
                onClick = {
                  val c = current
                  if (c != null) {
                    anchor = c
                    watching = true
                    status = "Watching…"
                  } else {
                    // No fix yet: arm anyway; the first GPS update sets the pin.
                    watching = true
                    status = "Waiting for GPS fix…"
                  }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
              Text("Set anchor here & watch", fontWeight = FontWeight.SemiBold)
            }
          } else {
            Button(
                onClick = {
                  watching = false
                  alarming = false
                  status = ""
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
              Text("Stop watching", fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }

      Spacer(Modifier.height(12.dp))
      Text(
          "Keep the app open and the phone plugged in while at anchor. The screen stays on automatically.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  // Arm-before-fix case: once a location arrives, it becomes the anchor.
  LaunchedEffect(current, watching) {
    if (watching && anchor == null && current != null) {
      anchor = current
      status = "Anchor set - watching…"
    }
  }
}
