package com.example.ferrostartnew

import android.util.Log
import java.time.Duration
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Central remote config. One bootstrap URL is hardcoded; everything else -
 * API base, latest/min APK version, notices - comes from the website and can
 * change without releasing a new APK. Fetched best-effort at startup and
 * cached in SharedPreferences (via MarineApi's prefs), so the app works
 * offline and on config-server failure with the last known values.
 */
object AppConfig {

  private const val TAG = "AppConfig"
  private const val CONFIG_URL = "https://marine-os-lime.vercel.app/api/public/app-config"

  private val client: OkHttpClient by lazy {
    OkHttpClient.Builder().callTimeout(Duration.ofSeconds(10)).build()
  }

  data class Config(
      val apiBase: String,
      val latestVersionCode: Int,
      val minVersionCode: Int,
      val apkUrl: String,
      val notice: String,
  )

  /** Last cached config (defaults on first run). */
  fun cached(): Config =
      Config(
          apiBase = MarineApi.configString("cfg_api_base", MarineApi.DEFAULT_BASE_URL),
          latestVersionCode = MarineApi.configInt("cfg_latest_vc", 1),
          minVersionCode = MarineApi.configInt("cfg_min_vc", 1),
          apkUrl = MarineApi.configString("cfg_apk_url", ""),
          notice = MarineApi.configString("cfg_notice", ""),
      )

  /** Blocking fetch + cache. Call from Dispatchers.IO; failures keep the cache. */
  fun refresh() {
    try {
      val request = Request.Builder().url(CONFIG_URL).build()
      client.newCall(request).execute().use { res ->
        if (!res.isSuccessful) return
        val json = JSONObject(res.body.string())
        val android = json.optJSONObject("android") ?: JSONObject()
        MarineApi.saveConfig(
            apiBase = json.optString("apiBase").ifBlank { MarineApi.DEFAULT_BASE_URL },
            latestVersionCode = android.optInt("latestVersionCode", 1),
            minVersionCode = android.optInt("minVersionCode", 1),
            apkUrl = android.optString("apkUrl", ""),
            notice = android.optString("notice", ""),
        )
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Config refresh failed (using cached): $t")
    }
  }
}
