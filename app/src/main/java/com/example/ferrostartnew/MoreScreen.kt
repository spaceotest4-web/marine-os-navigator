package com.example.ferrostartnew

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Menu: account, links to the web product, support, and app info. */
@Composable
fun MoreScreen(onLogout: () -> Unit) {
  val context = LocalContext.current
  val config = AppConfig.cached()

  fun open(url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
  }

  @Composable
  fun MenuItem(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = onClick,
    ) {
      Column(Modifier.padding(14.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }

  Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("More", style = MaterialTheme.typography.headlineMedium)
          Text(
              MarineApi.userEmail() + if (MarineApi.isPro()) "  ·  Boater Pro" else "",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Spacer(Modifier.height(14.dp))

      if (!MarineApi.isPro()) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            onClick = { open("${MarineApi.webBase()}/route-planner/pro") },
        ) {
          Column(Modifier.padding(14.dp)) {
            Text(
                "Upgrade to Boater Pro",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Unlocks navigation here plus the Passage Router, Voyage Timeline, Multi-Stop Optimizer and more on the web.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
      }

      MenuItem("Open the route planner", "Plan and edit routes on the website") {
        open("${MarineApi.webBase()}/route-planner")
      }
      MenuItem("My account", "Subscription and profile on the website") {
        open("${MarineApi.webBase()}/route-planner/pro")
      }
      MenuItem("Contact support", "support.marineos@gmail.com") {
        context.startActivity(
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support.marineos@gmail.com")))
      }
      if (config.apkUrl.isNotBlank()) {
        MenuItem("Check for app updates", "Version ${BuildConfig.VERSION_NAME} installed") {
          open(config.apkUrl)
        }
      }
      MenuItem("Sign out", MarineApi.userEmail()) { onLogout() }

      Spacer(Modifier.weight(1f))
      Text(
          "Marine OS Navigator v${BuildConfig.VERSION_NAME} - planning on the web, navigation on the water. Not a substitute for official charts.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 8.dp),
      )
    }
  }
}
