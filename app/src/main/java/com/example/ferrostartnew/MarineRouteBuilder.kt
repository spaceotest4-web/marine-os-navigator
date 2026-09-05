package com.example.ferrostartnew

import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import uniffi.ferrostar.BoundingBox
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.ManeuverType
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteStep
import uniffi.ferrostar.SpokenInstruction
import uniffi.ferrostar.VisualInstruction
import uniffi.ferrostar.VisualInstructionContent
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointKind

/**
 * Builds a Ferrostar [Route] directly from a Marine OS saved route - no
 * routing API involved. The planned waypoints ARE the route; each leg
 * becomes one step with a "head to <name>" instruction, so guidance is
 * waypoint-sequenced the way marine navigation works, not street turns.
 */
object MarineRouteBuilder {

  private const val EARTH_RADIUS_M = 6_371_000.0
  private const val MS_PER_KNOT = 0.514444

  private fun haversineMeters(a: GeographicCoordinate, b: GeographicCoordinate): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h =
        sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
  }

  fun build(detail: MarineApi.RouteDetail): Route {
    require(detail.waypoints.size >= 2) { "Route needs at least two waypoints" }

    val coords = detail.waypoints.map { GeographicCoordinate(it.lat, it.lng) }
    val speedMs = (if (detail.speedKnots > 0) detail.speedKnots else 6.0) * MS_PER_KNOT

    val bbox =
        BoundingBox(
            sw =
                GeographicCoordinate(
                    coords.minOf { it.lat },
                    coords.minOf { it.lng },
                ),
            ne =
                GeographicCoordinate(
                    coords.maxOf { it.lat },
                    coords.maxOf { it.lng },
                ),
        )

    // Ferrostar waypoints: ends are breaks, intermediates are vias. The
    // navigation controller advances through them by proximity
    // (WaypointAdvanceMode.WaypointWithinRange in NavModule).
    val ferrostarWaypoints =
        coords.mapIndexed { i, c ->
          Waypoint(
              coordinate = c,
              kind =
                  if (i == 0 || i == coords.lastIndex) WaypointKind.BREAK else WaypointKind.VIA,
              properties = null,
          )
        }

    var totalMeters = 0.0
    val steps =
        (1 until coords.size).map { i ->
          val from = coords[i - 1]
          val to = coords[i]
          val meters = haversineMeters(from, to)
          totalMeters += meters
          val toName =
              detail.waypoints[i].name.ifBlank {
                if (i == coords.lastIndex) "destination" else "waypoint ${i + 1}"
              }
          val isLast = i == coords.lastIndex
          val nmText = String.format("%.1f", meters / 1852.0)
          val instruction =
              if (isLast) "Arrive at $toName" else "Head to $toName ($nmText nm)"

          RouteStep(
              geometry = listOf(from, to),
              distance = meters,
              duration = if (speedMs > 0) meters / speedMs else 0.0,
              roadName = detail.name,
              exits = emptyList(),
              instruction = instruction,
              visualInstructions =
                  listOf(
                      VisualInstruction(
                          primaryContent =
                              VisualInstructionContent(
                                  text = if (isLast) "Arrive: $toName" else "To: $toName",
                                  maneuverType =
                                      when {
                                        isLast -> ManeuverType.ARRIVE
                                        i == 1 -> ManeuverType.DEPART
                                        else -> ManeuverType.CONTINUE
                                      },
                                  maneuverModifier = null,
                                  roundaboutExitDegrees = null,
                                  laneInfo = null,
                                  exitNumbers = emptyList(),
                              ),
                          secondaryContent = null,
                          subContent = null,
                          triggerDistanceBeforeManeuver = meters,
                      ),
                  ),
              spokenInstructions =
                  buildList {
                    // Spoken when the leg begins.
                    add(
                        SpokenInstruction(
                            text = instruction.replace(" nm)", " nautical miles)"),
                            ssml = null,
                            triggerDistanceBeforeManeuver = meters,
                            utteranceId = UUID.randomUUID(),
                        ),
                    )
                    // Approach call ~200 m out on legs long enough for it to be distinct.
                    if (meters > 500) {
                      add(
                          SpokenInstruction(
                              text =
                                  if (isLast) "Arriving at $toName" else "Approaching $toName",
                              ssml = null,
                              triggerDistanceBeforeManeuver = 200.0,
                              utteranceId = UUID.randomUUID(),
                          ),
                      )
                    }
                  },
              annotations = null,
              incidents = emptyList(),
              drivingSide = null,
              roundaboutExitNumber = null,
          )
        }

    return Route(
        geometry = coords,
        bbox = bbox,
        distance = totalMeters,
        waypoints = ferrostarWaypoints,
        steps = steps,
    )
  }
}
