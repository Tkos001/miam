package com.tkos.miam.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tkos.miam.data.update.AppInstaller
import com.tkos.miam.data.update.AppUpdate
import kotlinx.coroutines.launch

/**
 * Bouton « Installer la mise à jour » : télécharge l'APK dans l'app, montre la progression, lance l'installation.
 * Si Miam n'a pas encore le droit d'installer des applis, ouvre le réglage correspondant (une seule fois).
 */
@Composable
fun UpdateButton(info: AppUpdate.Info, modifier: Modifier = Modifier, compact: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by AppInstaller.state.collectAsStateWithLifecycle()
    var askedPermission by remember { mutableStateOf(false) }
    LaunchedEffect(askedPermission) {
        // Retour du réglage « sources inconnues » : relancer si c'est maintenant autorisé.
        if (askedPermission && AppInstaller.canInstallPackages(context)) {
            askedPermission = false
            scope.launch { AppInstaller.downloadAndInstall(context, info.downloadUrl) }
        }
    }
    Column(modifier) {
        when (val s = state) {
            is AppInstaller.State.Downloading -> {
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                Text("Téléchargement… ${(s.progress * 100).toInt()} %", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            AppInstaller.State.Installing -> {
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Installation…")
                }
                if (!compact) Text("Si Android demande confirmation, valide. L'app se ferme puis se rouvre à la main.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            else -> {
                Button(
                    onClick = {
                        if (AppInstaller.canInstallPackages(context)) scope.launch { AppInstaller.downloadAndInstall(context, info.downloadUrl) }
                        else { askedPermission = true; context.startActivity(AppInstaller.unknownSourcesSettingsIntent(context)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Installer Miam ${info.latest}")
                }
                if (s is AppInstaller.State.Failed) {
                    Text("Échec : ${s.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))) }) { Text("Télécharger avec le navigateur") }
                }
            }
        }
    }
}
