package com.example.ferrostartnew

import android.content.Context
import android.content.SharedPreferences
import java.time.Duration
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Marine OS backend client. Auth is the same JWT the website uses, carried
 * as a Bearer header; obtained from POST /api/app/login and kept in
 * SharedPreferences. All calls are blocking - invoke from Dispatchers.IO.
 */
object MarineApi {

  const val DEFAULT_BASE_URL = "https://marine-os-lime.vercel.app"
  private const val PREFS = "marine_os"
  private const val KEY_TOKEN = "token"
  private const val KEY_EMAIL = "email"
  private const val KEY_PRO = "pro"

  private lateinit var prefs: SharedPreferences

  // API base is remote-config driven (see AppConfig); default until fetched.
  private val baseUrl: String
    get() = prefs.getString("cfg_api_base", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL

  fun webBase(): String = baseUrl

  fun configString(key: String, fallback: String): String =
      prefs.getString(key, fallback) ?: fallback

  fun configInt(key: String, fallback: Int): Int = prefs.getInt(key, fallback)

  fun saveConfig(
      apiBase: String,
      latestVersionCode: Int,
      minVersionCode: Int,
      apkUrl: String,
      notice: String,
  ) {
    prefs
        .edit()
        .putString("cfg_api_base", apiBase)
        .putInt("cfg_latest_vc", latestVersionCode)
        .putInt("cfg_min_vc", minVersionCode)
        .putString("cfg_apk_url", apkUrl)
        .putString("cfg_notice", notice)
        .apply()
  }

  private val client: OkHttpClient by lazy {
    OkHttpClient.Builder().callTimeout(Duration.ofSeconds(20)).build()
  }

  private val jsonMedia = "application/json; charset=utf-8".toMediaType()

  fun init(context: Context) {
    prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  }

  fun hasToken(): Boolean = prefs.getString(KEY_TOKEN, null) != null

  fun userEmail(): String = prefs.getString(KEY_EMAIL, "") ?: ""

  fun isPro(): Boolean = prefs.getBoolean(KEY_PRO, false)

  fun logout() {
    // Keep the cached remote config; only drop the session.
    prefs.edit().remove(KEY_TOKEN).remove(KEY_EMAIL).remove(KEY_PRO).apply()
  }

  data class RouteSummary(
      val id: String,
      val name: String,
      val waypointCount: Int,
      val nm: Double,
      val speedKnots: Double,
      val start: String,
      val end: String,
  )

  data class MarineWaypoint(val lat: Double, val lng: Double, val name: String)

  data class RouteDetail(
      val id: String,
      val name: String,
      val speedKnots: Double,
      val waypoints: List<MarineWaypoint>,
  )

  class ApiException(message: String) : Exception(message)

  private fun errorMessage(body: String?, fallback: String): String =
      try {
        JSONObject(body ?: "").optString("error").ifBlank { fallback }
      } catch (_: Exception) {
        fallback
      }

  /** Returns true when the account is Pro. Throws ApiException with a user-facing message. */
  fun login(email: String, password: String): Boolean {
    val payload =
        JSONObject().put("email", email.trim()).put("password", password).toString()
    val request =
        Request.Builder()
            .url("$baseUrl/api/app/login")
            .post(payload.toRequestBody(jsonMedia))
            .build()
    client.newCall(request).execute().use { res ->
      val body = res.body.string()
      if (!res.isSuccessful) throw ApiException(errorMessage(body, "Login failed"))
      val json = JSONObject(body)
      val token = json.optString("token")
      if (token.isBlank()) throw ApiException("Login failed")
      val user = json.optJSONObject("user")
      prefs
          .edit()
          .putString(KEY_TOKEN, token)
          .putString(KEY_EMAIL, user?.optString("email") ?: email.trim())
          .putBoolean(KEY_PRO, user?.optBoolean("pro") ?: false)
          .apply()
      return user?.optBoolean("pro") ?: false
    }
  }

  private fun authedGet(path: String): JSONObject {
    val token = prefs.getString(KEY_TOKEN, null) ?: throw ApiException("Not signed in")
    val request =
        Request.Builder().url("$baseUrl$path").header("Authorization", "Bearer $token").build()
    client.newCall(request).execute().use { res ->
      val body = res.body.string()
      if (res.code == 401) {
        logout()
        throw ApiException("Session expired - sign in again")
      }
      if (!res.isSuccessful) throw ApiException(errorMessage(body, "Request failed (${res.code})"))
      return JSONObject(body)
    }
  }

  fun listRoutes(): List<RouteSummary> {
    val json = authedGet("/api/app/routes")
    prefs.edit().putBoolean(KEY_PRO, json.optBoolean("pro", isPro())).apply()
    val arr = json.optJSONArray("routes") ?: return emptyList()
    return (0 until arr.length()).map { i ->
      val r = arr.getJSONObject(i)
      RouteSummary(
          id = r.getString("id"),
          name = r.optString("name", "Untitled route"),
          waypointCount = r.optInt("waypointCount"),
          nm = r.optDouble("nm", 0.0),
          speedKnots = r.optDouble("speedKnots", 6.0),
          start = r.optString("start", ""),
          end = r.optString("end", ""),
      )
    }
  }

  fun getRoute(id: String): RouteDetail {
    val json = authedGet("/api/app/routes/$id")
    val r = json.getJSONObject("route")
    val wps = r.getJSONArray("waypoints")
    return RouteDetail(
        id = r.getString("id"),
        name = r.optString("name", "Untitled route"),
        speedKnots = r.optDouble("speedKnots", 6.0),
        waypoints =
            (0 until wps.length()).map { i ->
              val w = wps.getJSONObject(i)
              MarineWaypoint(w.getDouble("lat"), w.getDouble("lng"), w.optString("name", ""))
            },
    )
  }
}
