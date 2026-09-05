package com.example.ferrostartnew

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.stadiamaps.ferrostar.composeui.runtime.KeepScreenOnDisposableEffect
import com.stadiamaps.ferrostar.maplibreui.NavigationMapClickResult
import com.stadiamaps.ferrostar.maplibreui.runtime.rememberNavigationMapState
import com.stadiamaps.ferrostar.maplibreui.views.DynamicallyOrientingNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.compose.style.BaseStyle
import uniffi.ferrostar.GeographicCoordinate

/** Lifebuoy glyph for the MOB button: white ring with four spokes. */
@Composable
private fun LifebuoyIcon(sizeDp: androidx.compose.ui.unit.Dp, color: Color) {
  Canvas(modifier = Modifier.size(sizeDp)) {
    val r = size.minDimension / 2f
    val stroke = r * 0.42f
    // Ring
    drawCircle(color = color, radius = r * 0.66f, style = Stroke(width = stroke))
    // Spokes at 45/135/225/315 degrees
    for (angleDeg in listOf(45.0, 135.0, 225.0, 315.0)) {
      val a = Math.toRadians(angleDeg)
      val from =
          Offset(
              center.x + (r * 0.40f * kotlin.math.cos(a)).toFloat(),
              center.y + (r * 0.40f * kotlin.math.sin(a)).toFloat(),
          )
      val to =
          Offset(
              center.x + (r * 0.95f * kotlin.math.cos(a)).toFloat(),
              center.y + (r * 0.95f * kotlin.math.sin(a)).toFloat(),
          )
      drawLine(color = color, start = from, end = to, strokeWidth = stroke * 0.5f)
    }
  }
}

/** Share glyph: three nodes joined by two lines (drawn, no icon library). */
@Composable
private fun ShareIcon(sizeDp: androidx.compose.ui.unit.Dp, color: Color) {
  Canvas(modifier = Modifier.size(sizeDp)) {
    val w = size.width
    val h = size.height
    val r = w * 0.13f
    val right = Offset(w * 0.74f, h * 0.20f)
    val left = Offset(w * 0.26f, h * 0.50f)
    val bottom = Offset(w * 0.74f, h * 0.80f)
    drawLine(color, left, right, strokeWidth = r * 0.75f)
    drawLine(color, left, bottom, strokeWidth = r * 0.75f)
    drawCircle(color, radius = r, center = right)
    drawCircle(color, radius = r, center = left)
    drawCircle(color, radius = r, center = bottom)
  }
}

/**
 * Full-screen navigation scene with marine extras:
 * - MOB (man overboard): one tap pins the spot; a banner shows live bearing
 *   and distance back to it until cleared.
 * - Share trip: creates the route's public link (family watches on the web)
 *   and posts the live position to it every 60 s while navigating.
 * Long-press anywhere still fetches a demo route (original POC behavior).
 */
@Composable
fun NavigationScene(
    viewModel: PocNavigationViewModel = NavModule.viewModel,
    routeId: String? = null,
    onExit: (() -> Unit)? = null,
) {
  KeepScreenOnDisposableEffect()

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val uiState by viewModel.navigationUiState.collectAsState()

  var mobPoint by remember { mutableStateOf<GeographicCoordinate?>(null) }
  var sharing by remember { mutableStateOf(false) }
  var shareMsg by remember { mutableStateOf("") }

  val allPermissions =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.FOREGROUND_SERVICE_LOCATION,
        )
      } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
      }

  val permissionsLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
          permissions ->
        viewModel.setLocationPermissions(
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false),
        )
      }

  LaunchedEffect(Unit) {
    if (
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    ) {
      viewModel.setLocationPermissions(true)
    } else {
      permissionsLauncher.launch(allPermissions)
    }
  }

  // Share trip: post the live position to the route's track once a minute so
  // the public share page shows a near-live position for people ashore.
  LaunchedEffect(sharing) {
    if (!sharing || routeId == null) return@LaunchedEffect
    while (sharing) {
      val loc = uiState.location
      if (loc != null) {
        try {
          withContext(Dispatchers.IO) {
            MarineApi.postTrackPoint(routeId, loc.coordinates.lat, loc.coordinates.lng)
          }
        } catch (_: Exception) {
          // Offline moment - the next tick retries.
        }
      }
      delay(60_000)
    }
  }

  Box(Modifier.fillMaxSize()) {
    DynamicallyOrientingNavigationView(
        modifier = Modifier.fillMaxSize(),
        baseStyle = BaseStyle.Uri(NavModule.mapStyleUrl),
        navigationMapState = rememberNavigationMapState(),
        viewModel = viewModel,
        onTapExit = {
          sharing = false
          viewModel.stopNavigation()
          onExit?.invoke()
        },
        onMapLongClick = { _, _ -> NavigationMapClickResult.Pass },
    )

    // MOB banner: live distance and bearing back to the pinned point.
    mobPoint?.let { mob ->
      val loc = uiState.location
      Surface(
          color = Color(0xFFDC2626),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.align(Alignment.TopCenter).padding(top = 110.dp, start = 16.dp, end = 16.dp),
      ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
          Column(Modifier.weight(1f)) {
            Text("MAN OVERBOARD", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                if (loc != null) {
                  val d =
                      GeoUtils.haversineMeters(
                          loc.coordinates.lat, loc.coordinates.lng, mob.lat, mob.lng)
                  val b =
                      GeoUtils.bearingDeg(
                          loc.coordinates.lat, loc.coordinates.lng, mob.lat, mob.lng)
                  "${GeoUtils.metersToNmText(d)} · steer ${b}°"
                } else "Position pinned",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
          }
          TextButton(onClick = { mobPoint = null }) {
            Text("Clear", color = Color.White, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // Share confirmation toast-ish chip.
    if (shareMsg.isNotBlank()) {
      Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 210.dp),
      ) {
        Text(
            shareMsg,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }
      LaunchedEffect(shareMsg) {
        delay(4000)
        shareMsg = ""
      }
    }

    // Bottom-left action stack: MOB always; Share when this is a saved route.
    Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 130.dp)) {
      Button(
          onClick = {
            uiState.location?.let { mobPoint = it.coordinates }
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
          shape = CircleShape,
          contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
          modifier = Modifier.size(64.dp),
      ) {
        LifebuoyIcon(sizeDp = 34.dp, color = Color.White)
      }
      if (routeId != null && MarineApi.hasToken()) {
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
              scope.launch {
                try {
                  val url = withContext(Dispatchers.IO) { MarineApi.shareRoute(routeId) }
                  sharing = true
                  shareMsg = "Sharing live - link ready to send"
                  val send =
                      Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Follow my trip live on Marine OS: $url")
                      }
                  context.startActivity(Intent.createChooser(send, "Share your trip"))
                } catch (e: Exception) {
                  shareMsg = e.message ?: "Could not create the share link"
                }
              }
            },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (sharing) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary),
            shape = CircleShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.size(64.dp),
        ) {
          ShareIcon(sizeDp = 28.dp, color = Color.White)
        }
      }
    }
  }
}
