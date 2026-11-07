package com.tkos.miam.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.tkos.miam.data.MiamRepository
import com.tkos.miam.data.ai.ReceiptItem
import com.tkos.miam.data.model.Aisle
import com.tkos.miam.data.model.MeasureUnit
import com.tkos.miam.data.shopping.ShoppingAggregator
import com.tkos.miam.ui.components.DropdownField
import kotlinx.coroutines.launch
import java.io.File

/** Lecture d'un ticket de caisse : photo → OCR local → (assistant IA) → relecture ligne par ligne → stock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScreen(
    repository: MiamRepository,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val items = remember { mutableStateListOf<ReceiptItem>() }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var rawLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var showRaw by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }

    fun analyse(uri: Uri) {
        busy = true
        scope.launch {
            val r = runCatching { repository.readReceipt(uri) }
            busy = false
            r.onSuccess { res ->
                items.clear(); items.addAll(res.items); rawLines = res.rawLines
                info = when {
                    res.rawLines.isEmpty() -> "Aucun texte lu : photo floue, trop sombre ou ticket trop pâle. Réessaie à plat, bien éclairé."
                    res.usedAi -> "${res.items.size} ligne(s) interprétées par l'assistant. Vérifie puis valide."
                    res.aiError != null -> "Assistant indisponible (${res.aiError}) : lecture locale, ${res.items.count { it.selected }} ligne(s) reconnues sur ${res.items.size}."
                    else -> "Lecture locale : ${res.items.count { it.selected }} ligne(s) reconnues sur ${res.items.size}. Les autres sont décochées : corrige ce qui est utile."
                }
            }.onFailure { info = "Échec : ${it.message}" }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { analyse(it) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) captureUri?.let { analyse(it) } }
    fun takePhoto() {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "ticket-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        captureUri = uri
        camera.launch(uri)
    }

    val selectedCount = items.count { it.selected }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Ticket de caisse") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") } },
                actions = {
                    if (rawLines.isNotEmpty()) TextButton(onClick = { showRaw = true }) { Text("Texte lu") }
                },
            )
        },
        bottomBar = {
            if (items.isNotEmpty()) {
                BottomAppBar {
                    Button(
                        enabled = selectedCount > 0 && !busy,
                        onClick = {
                            scope.launch {
                                val n = repository.addReceiptToStock(items.toList())
                                snackbar.showSnackbar("$n article(s) ajouté(s) au stock")
                                onDone()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) { Icon(Icons.Default.Inventory2, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Ajouter au stock · $selectedCount") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { takePhoto() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Photographier")
                }
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Galerie")
                }
            }
            if (busy) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(24.dp)); Spacer(Modifier.width(12.dp)); Text("Lecture du ticket…")
                }
            }
            info?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (items.isEmpty() && !busy) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🧾", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Photographie le ticket à plat, bien éclairé, en entier. Les produits lus sont proposés ligne par ligne : tu corriges, tu coches, tu valides. Rien n'entre en stock sans toi.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    itemsIndexed(items, key = { i, _ -> i }) { i, it ->
                        Row(
                            Modifier.fillMaxWidth().clickable { editIndex = i }.padding(start = 4.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = it.selected, onCheckedChange = { c -> items[i] = it.copy(selected = c) })
                            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                Text(it.name, style = MaterialTheme.typography.bodyLarge, color = if (it.selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    (if (it.matched) "" else "Non reconnu · ") + it.raw,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (it.matched) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                )
                            }
                            val q = ShoppingAggregator.formatIngredient(it.quantity, it.unitEnum)
                            Text(if (q.isBlank()) "—" else q, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    editIndex?.let { i ->
        val it = items[i]
        var name by remember(i) { mutableStateOf(it.name) }
        var qty by remember(i) { mutableStateOf(it.quantity?.let { q -> ShoppingAggregator.fmt(q) } ?: "") }
        var unit by remember(i) { mutableStateOf(it.unitEnum) }
        var aisle by remember(i) { mutableStateOf(it.aisleEnum) }
        AlertDialog(
            onDismissRequest = { editIndex = null },
            title = { Text("Ligne du ticket") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(it.raw, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Article") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = qty, onValueChange = { qty = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                            label = { Text("Qté") }, singleLine = true, modifier = Modifier.weight(0.8f),
                        )
                        DropdownField(
                            label = "Unité", value = unit, options = MeasureUnit.entries,
                            optionLabel = { u -> if (u == MeasureUnit.NONE) "—" else u.label },
                            onSelected = { unit = it }, modifier = Modifier.weight(1.2f),
                        )
                    }
                    DropdownField(label = "Rayon", value = aisle, options = Aisle.entries, optionLabel = { a -> "${a.emoji} ${a.label}" }, onSelected = { aisle = it })
                }
            },
            confirmButton = {
                Button(enabled = name.isNotBlank(), onClick = {
                    items[i] = it.copy(name = name.trim(), quantity = qty.replace(',', '.').toDoubleOrNull(), unit = unit.name, aisle = aisle.name, selected = true, matched = true)
                    editIndex = null
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { items.removeAt(i); editIndex = null }) { Text("Retirer", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editIndex = null }) { Text("Annuler") }
                }
            },
        )
    }

    if (showRaw) {
        AlertDialog(
            onDismissRequest = { showRaw = false },
            confirmButton = { TextButton(onClick = { showRaw = false }) { Text("Fermer") } },
            title = { Text("Texte lu sur le ticket") },
            text = {
                LazyColumn { itemsIndexed(rawLines) { _, l -> Text(l, style = MaterialTheme.typography.bodySmall) } }
            },
        )
    }
}
