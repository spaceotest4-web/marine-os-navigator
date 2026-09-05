package com.example.ferrostartnew

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.ferrostar.Route

/**
 * App flow: Login -> My Routes -> Navigation. The website stays the place
 * to plan routes and manage the Boater Pro subscription; the app signs in
 * to the same account and navigates the saved routes on the water.
 * Design: always-light, ocean-blue Marine OS theme (see MarineTheme).
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

  // System back: during navigation it returns to the route list (stopping
  // guidance) instead of closing the app. On the routes/login screens the
  // default behavior (exit) is correct.
  BackHandler(enabled = screen == AppScreen.Navigating) {
    viewModel.stopNavigation()
    screen = AppScreen.Routes
  }

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
private fun BrandMark() {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
        modifier =
            Modifier.size(44.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
      Text("⚓", fontSize = 22.sp)
    }
    Spacer(Modifier.width(12.dp))
    Column {
      Text(
          "Marine OS",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.primary,
      )
      Text(
          "Navigator",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
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

  val fieldShape = RoundedCornerShape(12.dp)
  val fieldColors =
      OutlinedTextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surface,
          unfocusedContainerColor = MaterialTheme.colorScheme.surface,
      )

  Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      BrandMark()
      Spacer(Modifier.height(28.dp))

      Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth(),
      ) {
        Column(Modifier.padding(20.dp)) {
          Text("Welcome back", style = MaterialTheme.typography.titleMedium)
          Text(
              "Sign in with your route planner account",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(bottom = 16.dp),
          )

          OutlinedTextField(
              value = email,
              onValueChange = { email = it },
              label = { Text("Email") },
              singleLine = true,
              shape = fieldShape,
              colors = fieldColors,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
              modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(12.dp))
          OutlinedTextField(
              value = password,
              onValueChange = { password = it },
              label = { Text("Password") },
              singleLine = true,
              shape = fieldShape,
              colors = fieldColors,
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
              modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(18.dp))
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
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().height(52.dp),
          ) {
            if (busy) {
              CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp,
              )
              Spacer(Modifier.width(10.dp))
              Text("Signing in…")
            } else {
              Text("Sign in", fontWeight = FontWeight.SemiBold)
            }
          }

          error?.let {
            Spacer(Modifier.height(12.dp))
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
        }
      }

      TextButton(
          onClick = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("${MarineApi.webBase()}/sign-up")),
            )
          },
          modifier = Modifier.padding(top = 14.dp),
      ) {
        Text("No account yet? Create one free")
      }
      Text(
          "Plan routes on the web - run them here on the water.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun StatPill(text: String) {
  Surface(
      color = MaterialTheme.colorScheme.surfaceVariant,
      shape = RoundedCornerShape(999.dp),
  ) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
  var expandedRouteId by remember { mutableStateOf<String?>(null) }
  var detailCache by remember { mutableStateOf(mapOf<String, MarineApi.RouteDetail>()) }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val config = AppConfig.cached()

  // Start navigation at a chosen waypoint: legs before it are dropped, so
  // guidance begins "head to waypoint N+1" instead of pointing back to the
  // start of the route.
  fun startNav(routeId: String, fromIndex: Int) {
    if (loadingRouteId != null) return
    loadingRouteId = routeId
    error = null
    scope.launch {
      try {
        val detail =
            detailCache[routeId]
                ?: withContext(Dispatchers.IO) { MarineApi.getRoute(routeId) }.also {
                  detailCache = detailCache + (routeId to it)
                }
        val remaining = detail.waypoints.drop(fromIndex)
        if (remaining.size < 2) {
          error = "Pick an earlier waypoint - at least two are needed to navigate."
          return@launch
        }
        onStartNavigation(
            MarineRouteBuilder.build(detail.copy(waypoints = remaining)),
            simulate,
        )
      } catch (e: Exception) {
        error = e.message ?: "Could not load the route"
      } finally {
        loadingRouteId = null
      }
    }
  }

  fun toggleWaypoints(routeId: String) {
    if (expandedRouteId == routeId) {
      expandedRouteId = null
      return
    }
    expandedRouteId = routeId
    if (detailCache[routeId] == null) {
      scope.launch {
        try {
          val detail = withContext(Dispatchers.IO) { MarineApi.getRoute(routeId) }
          detailCache = detailCache + (routeId to detail)
        } catch (e: Exception) {
          error = e.message ?: "Could not load waypoints"
          expandedRouteId = null
        }
      }
    }
  }

  LaunchedEffect(Unit) {
    try {
      routes = withContext(Dispatchers.IO) { MarineApi.listRoutes() }
    } catch (e: Exception) {
      error = e.message ?: "Could not load routes"
      if (!MarineApi.hasToken()) onLogout()
    }
  }

  Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
      Spacer(Modifier.height(20.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("My Routes", style = MaterialTheme.typography.headlineMedium)
          Text(
              MarineApi.userEmail(),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        TextButton(onClick = onLogout) { Text("Sign out") }
      }

      Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
      ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
          Column(Modifier.weight(1f)) {
            Text("Demo mode", style = MaterialTheme.typography.titleSmall)
            Text(
                if (simulate) "Boat runs the route by itself"
                else "Real GPS - use this on the water",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(checked = simulate, onCheckedChange = { simulate = it })
        }
      }

      if (config.notice.isNotBlank()) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        ) {
          Text(
              config.notice,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(12.dp),
          )
        }
      }

      if (config.latestVersionCode > BuildConfig.VERSION_CODE && config.apkUrl.isNotBlank()) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        ) {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
          ) {
            Text(
                "A newer version is available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(config.apkUrl)))
                },
            ) {
              Text("Update")
            }
          }
        }
      }

      if (!MarineApi.isPro()) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        ) {
          Column(Modifier.padding(14.dp)) {
            Text(
                "Navigation is part of Boater Pro",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Subscribe once on the website - the app unlocks automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            TextButton(
                onClick = {
                  context.startActivity(
                      Intent(
                          Intent.ACTION_VIEW,
                          Uri.parse("${MarineApi.webBase()}/route-planner/pro"),
                      ),
                  )
                },
            ) {
              Text("See Boater Pro")
            }
          }
        }
      }

      error?.let {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
          Text(
              it,
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(12.dp),
          )
        }
      }

      when (val list = routes) {
        null ->
            if (error == null) {
              Column(
                  modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Loading your routes…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
        else ->
            if (list.isEmpty()) {
              Card(
                  shape = RoundedCornerShape(16.dp),
                  colors =
                      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                  modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
              ) {
                Column(Modifier.padding(18.dp)) {
                  Text("No routes yet", style = MaterialTheme.typography.titleMedium)
                  Text(
                      "Plan your first route on the website and it appears here, ready to navigate.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  TextButton(
                      onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("${MarineApi.webBase()}/route-planner"),
                            ),
                        )
                      },
                  ) {
                    Text("Open the route planner")
                  }
                }
              }
            } else {
              LazyColumn(
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                  modifier = Modifier.padding(top = 4.dp),
              ) {
                items(list, key = { it.id }) { r ->
                  Card(
                      shape = RoundedCornerShape(16.dp),
                      colors =
                          CardDefaults.cardColors(
                              containerColor = MaterialTheme.colorScheme.surface),
                      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                      modifier = Modifier.fillMaxWidth(),
                  ) {
                    Column(Modifier.padding(16.dp)) {
                      Text(r.name, style = MaterialTheme.typography.titleMedium)
                      val endpoints =
                          listOf(r.start, r.end).filter { it.isNotBlank() }.joinToString("  →  ")
                      if (endpoints.isNotBlank()) {
                        Text(
                            endpoints,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                      }
                      Row(
                          horizontalArrangement = Arrangement.spacedBy(8.dp),
                          modifier = Modifier.padding(top = 10.dp),
                      ) {
                        StatPill("${r.nm} nm")
                        StatPill("${r.waypointCount} waypoints")
                        StatPill("${r.speedKnots} kn")
                      }
                      Spacer(Modifier.height(12.dp))
                      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { startNav(r.id, 0) },
                            enabled = loadingRouteId == null && r.waypointCount >= 2,
                            shape = RoundedCornerShape(12.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f).height(46.dp),
                        ) {
                          if (loadingRouteId == r.id) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Starting…")
                          } else {
                            Text("Navigate", fontWeight = FontWeight.SemiBold)
                          }
                        }
                        TextButton(
                            onClick = { toggleWaypoints(r.id) },
                            modifier = Modifier.height(46.dp),
                        ) {
                          Text(if (expandedRouteId == r.id) "Hide" else "Waypoints")
                        }
                      }

                      // Expanded: every waypoint with "Start here", so a boater
                      // already mid-route can navigate 5 -> 6 -> 7 instead of
                      // being pointed back to the start.
                      if (expandedRouteId == r.id) {
                        val detail = detailCache[r.id]
                        if (detail == null) {
                          Row(
                              verticalAlignment = Alignment.CenterVertically,
                              modifier = Modifier.padding(top = 10.dp),
                          ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Loading waypoints…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                          }
                        } else {
                          Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                "Start navigation from any waypoint:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            detail.waypoints.forEachIndexed { i, wp ->
                              val isLast = i == detail.waypoints.lastIndex
                              Row(
                                  verticalAlignment = Alignment.CenterVertically,
                                  modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                              ) {
                                Box(
                                    modifier =
                                        Modifier.size(22.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                CircleShape,
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                  Text(
                                      "${i + 1}",
                                      style = MaterialTheme.typography.labelSmall,
                                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                                  )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    wp.name.ifBlank { "Waypoint ${i + 1}" },
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!isLast) {
                                  TextButton(
                                      onClick = { startNav(r.id, i) },
                                      enabled = loadingRouteId == null,
                                  ) {
                                    Text(
                                        if (i == 0) "Start" else "Start here",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                item { Spacer(Modifier.height(20.dp)) }
              }
            }
      }
    }
  }
}
