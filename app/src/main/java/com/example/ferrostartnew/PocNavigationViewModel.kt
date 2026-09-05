package com.example.ferrostartnew

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.stadiamaps.ferrostar.core.DefaultNavigationViewModel
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.core.annotation.AnnotationPublisher
import com.stadiamaps.ferrostar.core.annotation.valhalla.valhallaExtendedOSRMAnnotationPublisher
import com.stadiamaps.ferrostar.core.location.NavigationLocationProvider
import com.stadiamaps.ferrostar.core.location.toUserLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointKind

@OptIn(ExperimentalCoroutinesApi::class)
class PocNavigationViewModel(
    val ferrostarCore: FerrostarCore = NavModule.ferrostarCore,
    val locationProvider: NavigationLocationProvider = NavModule.locationProvider,
    annotationPublisher: AnnotationPublisher<*> = valhallaExtendedOSRMAnnotationPublisher(),
) : DefaultNavigationViewModel(ferrostarCore, annotationPublisher) {

  private val _hasLocationPermission = MutableStateFlow(false)

  // Simulation drives the vehicle along the route automatically — ideal for
  // demoing on an emulator. Set to false to follow the device's real GPS.
  private val _simulated = MutableStateFlow(true)
  val simulated = _simulated.asStateFlow()

  private val locationStateFlow =
      MutableStateFlow<UserLocation?>(NavModule.initialSimulatedLocation)
  val location = locationStateFlow.asStateFlow()

  // Inject the last known location into the UI state while not navigating,
  // so the map shows the user puck before a route starts.
  override val navigationUiState: StateFlow<NavigationUiState> =
      combine(super.navigationUiState, locationStateFlow) { a, b -> Pair(a, b) }
          .map { (uiState, location) ->
            if (uiState.isNavigating()) uiState else uiState.copy(location = location)
          }
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(),
              initialValue = NavigationUiState.empty(),
          )

  init {
    viewModelScope.launch {
      _hasLocationPermission
          .flatMapLatest { hasPermission ->
            if (!hasPermission || _simulated.value) {
              flowOf(NavModule.initialSimulatedLocation)
            } else {
              locationProvider.locationUpdates(5000L).map { it.toUserLocation() }
            }
          }
          .collect { locationStateFlow.emit(it) }
    }
  }

  fun setLocationPermissions(permitted: Boolean) {
    _hasLocationPermission.value = permitted
  }

  /**
   * Navigate a pre-built route (Marine OS saved route) - no routing API call.
   * With [simulate] on, the puck runs the route automatically (demo mode);
   * off, the device GPS drives it.
   */
  fun startNavigationRoute(route: uniffi.ferrostar.Route, simulate: Boolean) {
    _simulated.value = simulate
    viewModelScope.launch(Dispatchers.IO) {
      try {
        if (simulate) {
          locationProvider.enableSimulationOn(route)
        } else {
          locationProvider.disableSimulation()
        }
        if (navigationUiState.value.isNavigating()) {
          ferrostarCore.replaceRoute(route = route)
        } else {
          ferrostarCore.startNavigation(route = route)
        }
      } catch (t: Throwable) {
        Log.e(TAG, "Failed to start marine route: $t")
      }
    }
  }

  fun startNavigationTo(destination: GeographicCoordinate) {
    viewModelScope.launch(Dispatchers.IO) {
      val lastLocation = location.value ?: return@launch
      try {
        Log.d(TAG, "Fetching route to $destination")
        val routes =
            ferrostarCore.getRoutes(
                lastLocation,
                listOf(Waypoint(coordinate = destination, kind = WaypointKind.BREAK)),
            )
        val route = routes.first()

        if (simulated.value) {
          locationProvider.enableSimulationOn(route)
        }

        if (navigationUiState.value.isNavigating()) {
          ferrostarCore.replaceRoute(route = route)
        } else {
          ferrostarCore.startNavigation(route = route)
        }
      } catch (t: Throwable) {
        Log.e(TAG, "Failed to fetch route: $t")
      }
    }
  }

  override fun toggleMute() {
    val spokenInstructionObserver = ferrostarCore.spokenInstructionObserver ?: return
    spokenInstructionObserver.setMuted(!spokenInstructionObserver.isMuted)
  }

  override fun stopNavigation() {
    locationProvider.disableSimulation()
    ferrostarCore.stopNavigation()
  }

  companion object {
    const val TAG = "PocNavViewModel"
  }
}
