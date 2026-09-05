package com.example.ferrostartnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stadiamaps.ferrostar.googleplayservices.FusedNavigationLocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fuel near me: nearest fuel docks with prices from the Marine OS fuel feed
 * (marina live prices + boater reports), sorted by distance from the phone.
 */

private data class FuelRow(
    val station: MarineApi.FuelStation,
    val distanceM: Double,
)

@Composable
fun FuelScreen() {
  val context = LocalContext.current
  var rows by remember { mutableStateOf<List<FuelRow>?>(null) }
  var error by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    try {
      withContext(Dispatchers.IO) {
        val provider = FusedNavigationLocationProvider(context)
        val loc =
            withTimeoutOrNull(15_000) { provider.locationUpdates(2000L).first() }
                ?: throw MarineApi.ApiException("No GPS fix yet - step outside and try again.")
        val lat = loc.latitude
        val lng = loc.longitude
        rows =
            MarineApi.fuelStations(lat, lng)
                .map { FuelRow(it, GeoUtils.haversineMeters(lat, lng, it.lat, it.lng)) }
                .sortedBy { it.distanceM }
                .take(25)
      }
    } catch (e: Exception) {
      error = e.message ?: "Could not load fuel prices"
    }
  }

  Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
      Text("Fuel near me", style = MaterialTheme.typography.headlineMedium)
      Text(
          "Marina live prices and boater reports, nearest first.",
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
                    "Finding fuel docks near you…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
        else ->
            if (list.isEmpty()) {
              Card(
                  shape = RoundedCornerShape(16.dp),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                  modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                    "No reported fuel prices within about 35 nm. Prices come from marinas on Marine OS and boater reports - report one on the website's fuel map to help the next boater.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
              }
            } else {
              LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list) { r ->
                  Card(
                      shape = RoundedCornerShape(14.dp),
                      colors =
                          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                      modifier = Modifier.fillMaxWidth(),
                  ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp),
                    ) {
                      Column(Modifier.weight(1f)) {
                        Text(r.station.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${GeoUtils.metersToNmText(r.distanceM)} away" +
                                if (r.station.source == "marina") " · live marina price"
                                else " · boater reported",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      Column(horizontalAlignment = Alignment.End) {
                        r.station.prices.forEach { (type, price) ->
                          Text(
                              "${type.replaceFirstChar { it.uppercase() }} $${String.format("%.2f", price)}",
                              style = MaterialTheme.typography.titleSmall,
                              fontWeight = FontWeight.SemiBold,
                              color = MaterialTheme.colorScheme.primary,
                          )
                        }
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
