package com.example.ferrostartnew

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position

/**
 * One route in full: the whole line on a map, stats, share and delete
 * actions, and every waypoint with "Start here" so the boater begins
 * navigation from wherever they actually are.
 */

private fun routeGeoJson(waypoints: List<MarineApi.MarineWaypoint>): String {
  val coords = waypoints.joinToString(",") { "[${it.lng},${it.lat}]" }
  val points =
      waypoints.joinToString(",") {
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${it.lng},${it.lat}]},"properties":{}}"""
      }
  return """{"type":"FeatureCollection","features":[
    {"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{}},
    $points
  ]}"""
}

@Composable
fun RouteDetailScreen(
    summary: MarineApi.RouteSummary,
    detail: MarineApi.RouteDetail?,
    onStartFrom: (Int) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var confirmDelete by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  BackHandler { onBack() }

  Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
      // Header
      Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
      ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Column(Modifier.weight(1f)) {
          Text(summary.name, style = MaterialTheme.typography.titleMedium)
          Text(
              "${summary.nm} nm · ${summary.waypointCount} waypoints · ${summary.speedKnots} kn",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Whole route on the map
      if (detail != null && detail.waypoints.size >= 2) {
        val wps = detail.waypoints
        val pad = 0.02
        val bbox =
            BoundingBox(
                west = wps.minOf { it.lng } - pad,
                south = wps.minOf { it.lat } - pad,
                east = wps.maxOf { it.lng } + pad,
                north = wps.maxOf { it.lat } + pad,
            )
        val camera =
            rememberCameraState(
                firstPosition =
                    CameraPosition(
                        target =
                            Position(
                                latitude = (bbox.south + bbox.north) / 2,
                                longitude = (bbox.west + bbox.east) / 2,
                            ),
                        zoom = 8.0,
                    ))
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(300.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp)),
        ) {
          MaplibreMap(
              modifier = Modifier.fillMaxSize(),
              baseStyle = BaseStyle.Uri(NavModule.mapStyleUrl),
              cameraState = camera,
              boundingBox = bbox,
          ) {
            val routeSource =
                rememberGeoJsonSource(GeoJsonData.JsonString(routeGeoJson(wps)))
            LineLayer(
                id = "route-line",
                source = routeSource,
                color = const(Color(0xFF0369A1)),
                width = const(4.dp),
            )
            CircleLayer(
                id = "route-points",
                source = routeSource,
                color = const(Color(0xFF0369A1)),
                radius = const(4.dp),
            )
          }
        }
      } else {
        Box(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        }
      }

      // Actions
      Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      ) {
        Button(
            onClick = { onStartFrom(0) },
            enabled = detail != null && detail.waypoints.size >= 2 && !busy,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).height(46.dp),
        ) {
          Text("Navigate", fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = {
              scope.launch {
                try {
                  val url = withContext(Dispatchers.IO) { MarineApi.shareRoute(summary.id) }
                  val send =
                      Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "My planned route on Marine OS: $url")
                      }
                  context.startActivity(Intent.createChooser(send, "Share this route"))
                } catch (e: Exception) {
                  error = e.message ?: "Could not create the share link"
                }
              }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(46.dp),
        ) {
          Text("Share")
        }
        OutlinedButton(
            onClick = { confirmDelete = true },
            enabled = !busy,
            shape = RoundedCornerShape(12.dp),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.height(46.dp),
        ) {
          Text("Delete")
        }
      }

      error?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
      }

      // Waypoints: start anywhere
      Text(
          "Start navigation from any waypoint",
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
      )
      if (detail == null) {
        Text(
            "Loading waypoints…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
      } else {
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
          itemsIndexed(detail.waypoints) { i, wp ->
            val isLast = i == detail.waypoints.lastIndex
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
              Box(
                  modifier =
                      Modifier.size(24.dp)
                          .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                  contentAlignment = Alignment.Center,
              ) {
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Spacer(Modifier.width(10.dp))
              Text(
                  wp.name.ifBlank { "Waypoint ${i + 1}" },
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.weight(1f),
              )
              if (!isLast) {
                TextButton(onClick = { onStartFrom(i) }, enabled = !busy) {
                  Text("Start here", style = MaterialTheme.typography.labelMedium)
                }
              }
            }
          }
          item { Spacer(Modifier.height(16.dp)) }
        }
      }
    }
  }

  if (confirmDelete) {
    AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this route?") },
        text = { Text("\"${summary.name}\" will be removed from your account - on the website too. This cannot be undone.") },
        confirmButton = {
          TextButton(
              onClick = {
                confirmDelete = false
                busy = true
                scope.launch {
                  try {
                    withContext(Dispatchers.IO) { MarineApi.deleteRoute(summary.id) }
                    onDeleted()
                  } catch (e: Exception) {
                    error = e.message ?: "Could not delete the route"
                  } finally {
                    busy = false
                  }
                }
              },
          ) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
          }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
  }
}
