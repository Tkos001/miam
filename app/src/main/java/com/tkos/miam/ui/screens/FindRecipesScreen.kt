package com.tkos.miam.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tkos.miam.data.MiamRepository
import com.tkos.miam.data.settings.Settings

/**
 * « Trouver des recettes » : affiche le site de recettes configuré dans les paramètres.
 * Tant qu'il n'y a pas de site, page d'attente.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindRecipesScreen(repository: MiamRepository, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val settings by repository.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val context = LocalContext.current
    val url = settings.recipesUrl.trim().let { if (it.isNotBlank() && !it.startsWith("http")) "https://$it" else it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trouver des recettes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") } },
                actions = {
                    if (url.isNotBlank()) {
                        IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Ouvrir dans le navigateur")
                        }
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Paramètres") }
                },
            )
        },
    ) { padding ->
        if (url.isBlank()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🔎", style = MaterialTheme.typography.headlineLarge)
                Text("Bientôt disponible", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "Un site dédié regroupera toutes les recettes, avec import direct dans l'app. En attendant, tu peux renseigner n'importe quelle adresse dans les paramètres pour l'afficher ici.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 20.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Configurer l'adresse")
                }
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=recette"))) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Chercher une recette sur le web") }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(padding),
                factory = { ctx ->
                    WebView(ctx).apply {
                        this.settings.javaScriptEnabled = true
                        this.settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        loadUrl(url)
                    }
                },
                update = { if (it.url != url) it.loadUrl(url) },
            )
        }
    }
}
