package com.example.ferrostartnew

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import uniffi.ferrostar.Route

/** Bottom-tab shell: Routes, Weather, Anchor, Fuel, More. */

enum class Tab(val label: String, val emoji: String) {
  Routes("Routes", "🗺️"),
  Weather("Weather", "🌤️"),
  Anchor("Anchor", "⚓"),
  Fuel("Fuel", "⛽"),
  More("More", "☰"),
}

@Composable
fun MainTabs(
    onStartNavigation: (String, Route, Boolean) -> Unit,
    onLogout: () -> Unit,
) {
  var tab by remember { mutableStateOf(Tab.Routes) }

  Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
          Tab.entries.forEach { t ->
            NavigationBarItem(
                selected = tab == t,
                onClick = { tab = t },
                icon = { Text(t.emoji, fontSize = 20.sp) },
                label = { Text(t.label) },
            )
          }
        }
      },
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      when (tab) {
        Tab.Routes -> RouteListScreen(onStartNavigation = onStartNavigation, onLogout = onLogout)
        Tab.Weather -> WeatherScreen()
        Tab.Anchor -> AnchorScreen()
        Tab.Fuel -> FuelScreen()
        Tab.More -> MoreScreen(onLogout = onLogout)
      }
    }
  }
}
