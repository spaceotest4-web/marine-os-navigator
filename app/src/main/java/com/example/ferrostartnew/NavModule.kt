package com.example.ferrostartnew

import android.content.Context
import com.stadiamaps.ferrostar.composeui.notification.DefaultForegroundNotificationBuilder
import com.stadiamaps.ferrostar.core.AndroidTtsObserver
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.http.HttpClientProvider
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider.Companion.toOkHttpClientProvider
import com.stadiamaps.ferrostar.core.location.NavigationLocationProvider
import com.stadiamaps.ferrostar.core.location.SimulatedLocationProvider
import com.stadiamaps.ferrostar.core.location.toAndroidLocation
import com.stadiamaps.ferrostar.core.service.FerrostarForegroundServiceManager
import com.stadiamaps.ferrostar.core.service.ForegroundServiceManager
import com.stadiamaps.ferrostar.core.withJsonOptions
import com.stadiamaps.ferrostar.googleplayservices.FusedNavigationLocationProvider
import java.time.Duration
import java.time.Instant
import okhttp3.OkHttpClient
import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.GraphHopperVoiceUnits
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.RouteDeviationTracking
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.WellKnownRouteProvider
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep

/**
 * Wires up FerrostarCore with the Stadia Maps routing API (Valhalla) and the
 * device location + TTS plumbing. Modeled on the official Ferrostar demo AppModule.
 */
object NavModule {

  private lateinit var appContext: Context

  // ISKCON Cross Road, Ahmedabad — where the demo starts until real GPS takes over.
  val initialSimulatedLocation =
      UserLocation(
          GeographicCoordinate(23.0286, 72.5060),
          6.0,
          null,
          Instant.now(),
          null,
      )

  private val stadiaApiKey: String = BuildConfig.stadiaApiKey
  private val graphhopperApiKey: String = BuildConfig.graphhopperApiKey

  // Tiles: OpenFreeMap (free, no key). Styles: "bright" (flat, colorful),
  // "liberty" (3D buildings), "positron" (minimal gray). Or Stadia tiles:
  // "https://tiles.stadiamaps.com/styles/outdoors.json?api_key=$stadiaApiKey"
  val mapStyleUrl: String
    get() = "https://tiles.openfreemap.org/styles/bright"

  fun init(context: Context) {
    appContext = context.applicationContext
  }

  val locationProvider: NavigationLocationProvider by lazy {
    NavigationLocationProvider(
        liveProviding = FusedNavigationLocationProvider(appContext),
        simulatedProvider =
            SimulatedLocationProvider(
                warpFactor = 2u,
                initialLocation = initialSimulatedLocation.toAndroidLocation(),
            ),
    )
  }

  private val httpClient: HttpClientProvider by lazy {
    OkHttpClient.Builder().callTimeout(Duration.ofSeconds(15)).build().toOkHttpClientProvider()
  }

  private val foregroundServiceManager: ForegroundServiceManager by lazy {
    FerrostarForegroundServiceManager(appContext, DefaultForegroundNotificationBuilder(appContext))
  }

  val ferrostarCore: FerrostarCore by lazy {
    // Routing: GraphHopper when its key is set,
    // otherwise fall back to Stadia Maps (Valhalla).
    val (routeProvider, options) =
        if (graphhopperApiKey.isNotBlank()) {
          WellKnownRouteProvider.GraphHopper(
              "https://graphhopper.com/api/1/navigate/?key=$graphhopperApiKey",
              profile = "car",
              locale = "en",
              voiceUnits = GraphHopperVoiceUnits.METRIC,
          ) to emptyMap<String, Any>()
        } else {
          WellKnownRouteProvider.Valhalla(
              "https://api.stadiamaps.com/route/v1?api_key=$stadiaApiKey",
              "auto",
          ) to mapOf<String, Any>("units" to "kilometers")
        }

    FerrostarCore(
        routeProvider.withJsonOptions(options),
        httpClient = httpClient,
        locationProvider = locationProvider,
        foregroundServiceManager = foregroundServiceManager,
        navigationControllerConfig =
            NavigationControllerConfig(
                WaypointAdvanceMode.WaypointWithinRange(100.0),
                stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),
                stepAdvanceDistanceToEndOfStep(10u, 32u),
                RouteDeviationTracking.StaticThreshold(15U, 50.0),
                CourseFiltering.SNAP_TO_ROUTE,
            ),
    )
  }

  val ttsObserver: AndroidTtsObserver by lazy { AndroidTtsObserver(appContext) }

  val viewModel: PocNavigationViewModel by lazy { PocNavigationViewModel() }
}
