package com.example.ferrostartnew

import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.stadiamaps.ferrostar.core.AndroidTtsStatusListener
import java.util.Locale
import uniffi.ferrostar.createFerrostarLogger

class MainActivity : ComponentActivity(), AndroidTtsStatusListener {

  companion object {
    private const val TAG = "MainActivity"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    NavModule.init(applicationContext)
    MarineApi.init(applicationContext)

    // Voice guidance: route spoken instructions from the core to Android TTS.
    NavModule.ttsObserver.statusObserver = this
    NavModule.ferrostarCore.spokenInstructionObserver = NavModule.ttsObserver

    createFerrostarLogger()

    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isNavigationBarContrastEnforced = false
    }

    setContent { MaterialTheme { Surface { AppRoot() } } }
  }

  override fun onStart() {
    super.onStart()
    NavModule.ttsObserver.start()
  }

  override fun onDestroy() {
    super.onDestroy()
    NavModule.ttsObserver.shutdown()
  }

  // TTS listener callbacks

  override fun onTtsInitialized(tts: TextToSpeech?, status: Int) {
    if (tts != null) {
      tts.language = Locale.US
      Log.i(TAG, "TTS initialized, setLanguage status: $status")
    } else {
      Log.e(TAG, "TTS setup failed! $status")
    }
  }

  override fun onTtsSpeakError(utteranceId: String, status: Int) {
    Log.e(TAG, "TTS error synthesizing $utteranceId, status: $status")
  }

  override fun onTtsShutdownAndRelease() {
    Log.i(TAG, "TTS shutdown and released")
  }
}
