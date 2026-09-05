package com.example.ferrostartnew

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.ferrostar.Route

/**
 * App flow: Login -> My Routes -> Navigation. The website stays the place
 * to plan routes and manage the Boater Pro subscription; the app signs in
 * to the same account and navigates the saved routes on the water.
 */

sealed interface AppScreen {
  data object Login : AppScreen

  data object Routes : AppScreen

  data object Navigating : AppScreen
}

@Composable
fun AppRoot(viewModel: PocNavigationViewModel = NavModule.viewModel) {
  var screen by remember {
    mutableStateOf<AppScreen>(if (MarineApi.hasToken()) AppScreen.Routes else AppScreen.Login)
  }

  // Pull the central web config once at startup (API base, update info,
  // notices). Best-effort: failures keep the cached/default values.
  LaunchedEffect(Unit) { withContext(Dispatchers.IO) { AppConfig.refresh() } }

  // Ask for location (and the notification/foreground permissions newer
  // Android needs) up front, so navigation's foreground service can start
  // the moment the user taps Navigate.
  val context = LocalContext.current
  val permissions =
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
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        viewModel.setLocationPermissions(
            granted.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false),
        )
      }
  LaunchedEffect(screen) {
    if (screen == AppScreen.Routes) {
      if (
          ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
              PackageManager.PERMISSION_GRANTED
      ) {
        viewModel.setLocationPermissions(true)
      } else {
        permissionsLauncher.launch(permissions)
      }
    }
  }

  when (screen) {
    AppScreen.Login -> LoginScreen(onLoggedIn = { screen = AppScreen.Routes })
    AppScreen.Routes ->
        RouteListScreen(
            onStartNavigation = { route, simulate ->
              viewModel.startNavigationRoute(route, simulate)
              screen = AppScreen.Navigating
            },
            onLogout = {
              MarineApi.logout()
              screen = AppScreen.Login
            },
        )
    AppScreen.Navigating -> NavigationScene(onExit = { screen = AppScreen.Routes })
  }
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current

  Column(
      modifier = Modifier.fillMaxSize().padding(28.dp),
      verticalArrangement = Arrangement.Center,
  ) {
    Text("Marine OS", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    Text(
        "Sign in with your route planner account",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
    )

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = {
          if (busy) return@Button
          busy = true
          error = null
          scope.launch {
            try {
              withContext(Dispatchers.IO) { MarineApi.login(email, password) }
              onLoggedIn()
            } catch (e: Exception) {
              error = e.message ?: "Login failed"
            } finally {
              busy = false
            }
          }
        },
        enabled = email.isNotBlank() && password.isNotBlank() && !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(if (busy) "Signing in…" else "Sign in")
    }
    error?.let {
      Text(
          it,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(top = 10.dp),
      )
    }
    TextButton(
        onClick = {
          context.startActivity(
              Intent(Intent.ACTION_VIEW, Uri.parse("${MarineApi.webBase()}/sign-up")),
          )
        },
        modifier = Modifier.padding(top = 10.dp),
    ) {
      Text("No account yet? Create one free")
    }
    Text(
        "Plan routes on the web, run them here on the water.",
        style = MaterialTheme.typography.bodySmall,
    )
  }
}

@Composable
fun RouteListScreen(
    onStartNavigation: (Route, Boolean) -> Unit,
    onLogout: () -> Unit,
) {
  var routes by remember { mutableStateOf<List<MarineApi.RouteSummary>?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var loadingRouteId by remember { mutableStateOf<String?>(null) }
  var simulate by remember { mutableStateOf(true) }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val config = AppConfig.cached()

  LaunchedEffect(Unit) {
    try {
      routes = withContext(Dispatchers.IO) { MarineApi.listRoutes() }
    } catch (e: Exception) {
      error = e.message ?: "Could not load routes"
      if (!MarineApi.hasToken()) onLogout()
    }
  }

  Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("My Routes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(MarineApi.userEmail(), style = MaterialTheme.typography.bodySmall)
      }
      TextButton(onClick = onLogout) { Text("Sign out") }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
      Checkbox(checked = simulate, onCheckedChange = { simulate = it })
      Text(
          "Simulate the run (demo) - untick on the water to use real GPS",
          style = MaterialTheme.typography.bodySmall,
      )
    }

    if (config.notice.isNotBlank()) {
      Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(config.notice, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
      }
    }

    if (config.latestVersionCode > BuildConfig.VERSION_CODE && config.apkUrl.isNotBlank()) {
      Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
          modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
      ) {
        Column(Modifier.padding(12.dp)) {
          Text("A newer version of the app is available.", style = MaterialTheme.typography.bodySmall)
          TextButton(
              onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(config.apkUrl)))
              },
          ) {
            Text("Download update")
          }
        }
      }
    }

    if (!MarineApi.isPro()) {
      Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
          modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
      ) {
        Text(
            "Navigation is part of Boater Pro. Subscribe on the website (Route Planner -> Boater Pro), then pull to refresh here.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
      }
    }

    error?.let {
      Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
    }

    when (val list = routes) {
      null ->
          if (error == null) {
            Row(modifier = Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
              CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp))
              Spacer(Modifier.width(10.dp))
              Text("Loading your routes…")
            }
          }
      else ->
          if (list.isEmpty()) {
            Text(
                "No saved routes yet. Plan one on marine-os-lime.vercel.app/route-planner and it appears here.",
                modifier = Modifier.padding(top = 24.dp),
            )
          } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
              items(list, key = { it.id }) { r ->
                Card(modifier = Modifier.fillMaxWidth()) {
                  Column(Modifier.padding(14.dp)) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val endpoints =
                        listOf(r.start, r.end).filter { it.isNotBlank() }.joinToString(" -> ")
                    if (endpoints.isNotBlank()) {
                      Text(endpoints, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "${r.nm} nm - ${r.waypointCount} waypoints - ${r.speedKnots} kn",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                          if (loadingRouteId != null) return@Button
                          loadingRouteId = r.id
                          error = null
                          scope.launch {
                            try {
                              val detail = withContext(Dispatchers.IO) { MarineApi.getRoute(r.id) }
                              onStartNavigation(MarineRouteBuilder.build(detail), simulate)
                            } catch (e: Exception) {
                              error = e.message ?: "Could not load the route"
                            } finally {
                              loadingRouteId = null
                            }
                          }
                        },
                        enabled = loadingRouteId == null && r.waypointCount >= 2,
                    ) {
                      Text(if (loadingRouteId == r.id) "Starting…" else "Navigate")
                    }
                  }
                }
              }
            }
          }
    }
  }
}
