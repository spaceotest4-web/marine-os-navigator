package com.example.ferrostartnew

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.stadiamaps.ferrostar.composeui.runtime.KeepScreenOnDisposableEffect
import com.stadiamaps.ferrostar.maplibreui.NavigationMapClickResult
import com.stadiamaps.ferrostar.maplibreui.runtime.rememberNavigationMapState
import com.stadiamaps.ferrostar.maplibreui.views.DynamicallyOrientingNavigationView
import org.maplibre.compose.style.BaseStyle
import uniffi.ferrostar.GeographicCoordinate

/**
 * Full-screen navigation scene: shows the map; long-press anywhere to fetch a
 * route from the current location and start turn-by-turn navigation (with voice).
 */
@Composable
fun NavigationScene(viewModel: PocNavigationViewModel = NavModule.viewModel) {
  KeepScreenOnDisposableEffect()

  val context = LocalContext.current

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

  DynamicallyOrientingNavigationView(
      modifier = Modifier.fillMaxSize(),
      baseStyle = BaseStyle.Uri(NavModule.mapStyleUrl),
      navigationMapState = rememberNavigationMapState(),
      viewModel = viewModel,
      onTapExit = { viewModel.stopNavigation() },
      onMapLongClick = { position, _ ->
        viewModel.startNavigationTo(GeographicCoordinate(position.lat, position.lng))
        NavigationMapClickResult.Consume
      },
  )
}
