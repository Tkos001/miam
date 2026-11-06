package com.tkos.miam.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tkos.miam.BuildConfig
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.tkos.miam.data.MiamRepository
import com.tkos.miam.data.catalog.Catalog
import com.tkos.miam.data.catalog.CatalogSyncResult
import com.tkos.miam.data.update.AppUpdate
import com.tkos.miam.data.settings.AppTheme
import com.tkos.miam.data.settings.Settings
import com.tkos.miam.ui.components.ConfirmDialog
import com.tkos.miam.ui.components.Stepper
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repository: MiamRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings by repository.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    var url by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings.recipesUrl) { if (url == null) url = settings.recipesUrl }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }
    val context = LocalContext.current
    var catalogUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings.catalogUrl) { if (catalogUrl == null) catalogUrl = settings.catalogUrl }
    var syncing by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings.githubToken) { if (token == null) token = settings.githubToken }
    var contributor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings.contributorName) { if (contributor == null) contributor = settings.contributorName }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdate.Info?>(null) }
    val availableUpdate by repository.availableUpdate.collectAsStateWithLifecycle()
    LaunchedEffect(availableUpdate) { if (updateInfo == null) updateInfo = availableUpdate }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { repository.exportToUri(uri) }
                .onSuccess { snackbar.showSnackbar("Export terminé") }
                .onFailure { snackbar.showSnackbar("Échec de l'export : ${it.message}") }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Thème ----
            Section("Apparence") {
                AppTheme.entries.forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { scope.launch { repository.settings.setTheme(t) } }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ThemeSwatch(t)
                        Spacer(Modifier.width(14.dp))
                        Text(t.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (settings.theme == t) Icon(Icons.Default.Check, contentDescription = "Sélectionné", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ---- Liste de courses ----
            Section("Liste de courses") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Grouper par rayon", style = MaterialTheme.typography.bodyLarge)
                        Text("Sinon : liste plate triée par nom", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.groupByAisle, onCheckedChange = { scope.launch { repository.settings.setGroupByAisle(it) } })
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Personnes par défaut", style = MaterialTheme.typography.bodyLarge)
                        Text("Proposé lors de l'ajout d'un plat au planning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Stepper(value = settings.defaultServings, onValueChange = { scope.launch { repository.settings.setDefaultServings(it) } })
                }
            }

            // ---- Catalogue de recettes (dépôt git) ----
            Section("Catalogue de recettes") {
                val last = settings.catalogLastSync.takeIf { it > 0 }?.let {
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
                }
                Text(
                    if (last == null) "Jamais synchronisé" else "Version ${settings.catalogVersion} · synchronisé le $last",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            syncing = true
                            scope.launch {
                                val msg = when (val r = repository.syncCatalog(force = true)) {
                                    is CatalogSyncResult.Updated -> "Catalogue v${r.version} : ${r.added} ajouté(s), ${r.updated} mis à jour"
                                    is CatalogSyncResult.UpToDate -> "Catalogue déjà à jour (v${r.version})"
                                    is CatalogSyncResult.Failed -> "Échec : ${r.message}"
                                }
                                syncing = false
                                snackbar.showSnackbar(msg)
                            }
                        },
                        enabled = !syncing,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("Mettre à jour maintenant")
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Synchroniser au lancement", style = MaterialTheme.typography.bodyLarge)
                        Text("Récupère les nouvelles recettes si le catalogue a changé", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.catalogAutoSync, onCheckedChange = { scope.launch { repository.settings.setCatalogAutoSync(it) } })
                }
                OutlinedTextField(
                    value = catalogUrl ?: "",
                    onValueChange = { catalogUrl = it },
                    label = { Text("URL du catalogue (vide = dépôt Miam)") },
                    placeholder = { Text(Catalog.DEFAULT_URL) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedButton(
                    onClick = { scope.launch { repository.settings.setCatalogUrl(catalogUrl ?: ""); snackbar.showSnackbar("Adresse du catalogue enregistrée") } },
                    enabled = (catalogUrl ?: "") != settings.catalogUrl,
                    modifier = Modifier.padding(top = 6.dp),
                ) { Text("Enregistrer l'adresse") }
            }

            // ---- Contribuer au catalogue ----
            Section("Contribuer au catalogue") {
                Text(
                    "Avec un token GitHub, le menu « ⋮ » d'une recette propose « Publier dans le catalogue » : la recette (et sa photo) est commitée dans le dépôt et arrive chez les autres à leur prochaine sync. Token fine-grained, dépôt Tkos001/miam, permission Contents : Read and write.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = token ?: "",
                    onValueChange = { token = it },
                    label = { Text("Token GitHub") },
                    placeholder = { Text("github_pat_…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = contributor ?: "",
                    onValueChange = { contributor = it },
                    label = { Text("Ton prénom (auteur des publications)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                repository.settings.setGithubToken(token ?: "")
                                repository.settings.setContributorName(contributor ?: "")
                                snackbar.showSnackbar("Enregistré")
                            }
                        },
                        enabled = (token ?: "") != settings.githubToken || (contributor ?: "") != settings.contributorName,
                    ) { Text("Enregistrer") }
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new"))) },
                    ) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Créer un token") }
                }
                if (settings.dirtyRecipeIds.isNotEmpty()) {
                    Text(
                        "${settings.dirtyRecipeIds.size} recette(s) modifiée(s) ici et pas encore publiée(s) : la sync ne les écrase pas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            // ---- Mise à jour de l'app ----
            Section("Mise à jour de l'app") {
                val info = updateInfo
                Text(
                    when {
                        info == null -> "Version installée : ${AppUpdate.installedVersion}"
                        info.isNewer -> "Nouvelle version disponible : ${info.latest} (installée : ${info.installed})"
                        else -> "Tu as la dernière version (${info.installed})"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (info?.isNewer == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            checkingUpdate = true
                            scope.launch {
                                repository.checkAppUpdate()
                                    .onSuccess { updateInfo = it }
                                    .onFailure { snackbar.showSnackbar("Vérification impossible : ${it.message}") }
                                checkingUpdate = false
                            }
                        },
                        enabled = !checkingUpdate,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (checkingUpdate) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.SystemUpdate, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("Vérifier")
                    }
                }
                if (info?.isNewer == true) {
                    com.tkos.miam.ui.components.UpdateButton(info, Modifier.padding(top = 8.dp))
                }
                Text(
                    if (com.tkos.miam.data.update.AppInstaller.isSelfInstaller(context) && android.os.Build.VERSION.SDK_INT >= 31)
                        "Mise à jour installée directement par Miam, sans confirmation. L'app se ferme à la fin : rouvre-la."
                    else
                        "La première fois, Android demande d'autoriser Miam à installer des applis, puis une confirmation. Ensuite (Android 12+), les mises à jour s'installent sans rien demander.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // ---- Trouver des recettes ----
            Section("Trouver des recettes") {
                Text(
                    "Adresse du site de recettes (à venir). Laisse vide pour afficher la page d'attente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url ?: "",
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedButton(
                    onClick = { scope.launch { repository.settings.setRecipesUrl(url ?: ""); snackbar.showSnackbar("Adresse enregistrée") } },
                    enabled = (url ?: "") != settings.recipesUrl,
                    modifier = Modifier.padding(top = 6.dp),
                ) { Text("Enregistrer l'adresse") }
            }

            // ---- Données ----
            Section("Données") {
                OutlinedButton(onClick = { exporter.launch("miam-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileDownload, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Exporter (JSON)")
                }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileUpload, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Importer (JSON)")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val n = repository.restoreBuiltIn()
                            snackbar.showSnackbar(if (n == 0) "Les 15 plats de base sont déjà présents" else "$n plat(s) de base restauré(s)")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Restaurer les plats de base")
                }
                Text(
                    "L'export contient plats, planning et articles « Autres ». Les photos ne sont pas exportées.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Text(
                "Miam ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            )
        }
    }

    confirmImport?.let { uri ->
        ConfirmDialog(
            title = "Importer ce fichier ?",
            text = "Les plats portant le même nom seront remplacés, les autres ajoutés. Le planning et les articles du fichier sont ajoutés.",
            confirmLabel = "Importer",
            onConfirm = {
                scope.launch {
                    runCatching { repository.importFromUri(uri, replaceAll = false) }
                        .onSuccess { snackbar.showSnackbar("$it plat(s) importé(s)") }
                        .onFailure { snackbar.showSnackbar("Fichier invalide : ${it.message}") }
                }
            },
            onDismiss = { confirmImport = null },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

/** Petite pastille représentant chaque thème (fond + accent). */
@Composable
private fun ThemeSwatch(theme: AppTheme) {
    val (bg, accent) = when (theme) {
        AppTheme.SYSTEM -> Color(0xFF8A8A8A) to Color(0xFFFCF8F4)
        AppTheme.LIGHT -> Color(0xFFFCF8F4) to Color(0xFFB4552D)
        AppTheme.DARK -> Color(0xFF141414) to Color(0xFFF2B84B)
        AppTheme.PURPLE_DARK -> Color(0xFF14101C) to Color(0xFFCFBCFF)
        AppTheme.RED_DARK -> Color(0xFF1B1011) to Color(0xFFFFB3AD)
    }
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(accent)
        )
    }
}
