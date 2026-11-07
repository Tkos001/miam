package com.tkos.miam.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tkos.miam.data.MiamRepository
import com.tkos.miam.data.model.Aisle
import com.tkos.miam.data.model.ExtraItem
import com.tkos.miam.data.model.MeasureUnit
import com.tkos.miam.data.settings.Settings
import com.tkos.miam.data.shopping.ShoppingAggregator
import com.tkos.miam.data.shopping.ShoppingLine
import com.tkos.miam.ui.components.DropdownField
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    repository: MiamRepository,
    onBack: () -> Unit,
    onOpenMeals: () -> Unit,
    onOpenStock: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val lines by repository.shoppingLines.collectAsStateWithLifecycle(initialValue = emptyList())
    val extras by repository.extras.collectAsStateWithLifecycle(initialValue = emptyList())
    val mealsTodo by repository.mealsTodoCount.collectAsStateWithLifecycle(initialValue = 0)
    val stockCount by repository.stockCount.collectAsStateWithLifecycle(initialValue = 0)
    val settings by repository.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    var menuOpen by remember { mutableStateOf(false) }
    var showAddExtra by remember { mutableStateOf(false) }

    val toBuy = lines.filter { !it.inStock }
    val total = toBuy.size + extras.size
    val inCart = toBuy.count { it.checked } + extras.count { it.checked }
    val covered = lines.count { it.inStock }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Ma liste de courses") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") } },
                actions = {
                    IconButton(onClick = onOpenStock) { Icon(Icons.Default.Inventory2, contentDescription = "En stock") }
                    IconButton(onClick = { shareText(context, "Liste de courses", shoppingShareText(lines, extras, settings.groupByAisle)) }) {
                        Icon(Icons.Default.Share, contentDescription = "Partager")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Plus") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Copier le texte") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    copyText(context, "Liste de courses", shoppingShareText(lines, extras, settings.groupByAisle))
                                    scope.launch { snackbar.showSnackbar("Liste copiée") }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (settings.groupByAisle) "Afficher en liste plate" else "Grouper par rayon") },
                                onClick = { menuOpen = false; scope.launch { repository.settings.setGroupByAisle(!settings.groupByAisle) } },
                            )
                            DropdownMenuItem(
                                text = { Text("Tout décocher") },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        repository.clearChecks()
                                        extras.filter { it.checked }.forEach { repository.updateExtra(it.copy(checked = false)) }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Supprimer les articles « Autres » cochés") },
                                onClick = { menuOpen = false; scope.launch { repository.deleteCheckedExtras() } },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddExtra = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Autre article") },
            )
        },
        bottomBar = {
            if (inCart > 0) {
                BottomAppBar {
                    Button(
                        onClick = {
                            scope.launch {
                                val n = repository.finishShopping()
                                snackbar.showSnackbar("$n article(s) ajouté(s) au stock")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) { Icon(Icons.Default.Inventory2, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Courses terminées · $inCart dans le stock") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (total > 0 || covered > 0) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$inCart / $total dans le caddie", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "$mealsTodo repas à faire" + (if (covered > 0) " · $covered déjà en stock" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { if (total == 0) 1f else inCart.toFloat() / total },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
            if (lines.isEmpty() && extras.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("🛒", style = MaterialTheme.typography.headlineLarge)
                    Text("Liste vide", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        if (mealsTodo == 0) "Ajoute des repas à faire : leurs ingrédients apparaîtront ici, fusionnés et triés par rayon, moins ce que tu as déjà en stock."
                        else "Tout ce qu'il faut pour tes $mealsTodo repas est en stock ($stockCount articles).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    FilledTonalButton(onClick = onOpenMeals, modifier = Modifier.padding(top = 16.dp)) {
                        Icon(Icons.Default.RestaurantMenu, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Mes repas")
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    if (settings.groupByAisle) {
                        Aisle.entries.forEach { aisle ->
                            val group = lines.filter { it.aisle == aisle }
                            if (group.isNotEmpty()) {
                                item(key = "h_${aisle.name}") { SectionHeader("${aisle.emoji} ${aisle.label}", group.count { !it.isDone }) }
                                items(group, key = { it.key }) { line -> LineRow(line) { checked -> scope.launch { toggleLine(repository, line, checked, snackbar) } } }
                            }
                        }
                    } else {
                        items(lines.sortedBy { it.name.lowercase() }, key = { it.key }) { line -> LineRow(line) { checked -> scope.launch { toggleLine(repository, line, checked, snackbar) } } }
                    }
                    if (extras.isNotEmpty()) {
                        item(key = "h_extras") { SectionHeader("📝 Autres articles", extras.count { !it.checked }) }
                        items(extras, key = { "x_${it.id}" }) { e ->
                            ExtraRow(
                                item = e,
                                onCheck = { scope.launch { repository.updateExtra(e.copy(checked = it)) } },
                                onDelete = { scope.launch { repository.deleteExtra(e) } },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddExtra) {
        AddExtraDialog(
            onDismiss = { showAddExtra = false },
            onAdd = { item -> showAddExtra = false; scope.launch { repository.addExtra(item) } },
        )
    }
}

/** Coche = dans le caddie. Décocher une ligne « en stock » = c'est fini : l'article sort du stock et revient à acheter. */
private suspend fun toggleLine(repository: MiamRepository, line: ShoppingLine, checked: Boolean, snackbar: SnackbarHostState) {
    if (line.inStock) {
        if (!checked) {
            repository.deleteStockByKey(line.key)
            snackbar.showSnackbar("${line.name} retiré du stock : à acheter")
        }
    } else {
        repository.setLineChecked(line.key, checked)
    }
}

@Composable
private fun SectionHeader(title: String, remaining: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (remaining > 0) Text("$remaining", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LineRow(line: ShoppingLine, onCheck: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheck(!line.isDone) }
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val done = line.isDone
        Checkbox(checked = done, onCheckedChange = onCheck)
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                line.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                when {
                    line.inStock -> "En stock · décocher si c'est fini"
                    line.stockLabel != null -> "${line.stockLabel} · " + line.fromRecipes.joinToString(", ")
                    else -> line.fromRecipes.joinToString(", ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (line.inStock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (line.quantityLabel.isNotBlank()) {
            Text(
                line.quantityLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ExtraRow(item: ExtraItem, onCheck: (Boolean) -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheck(!item.checked) }
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = onCheck)
        Text(
            item.name,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
        )
        val q = ShoppingAggregator.formatIngredient(item.quantity, item.unitEnum)
        if (q.isNotBlank()) Text(q, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        IconButton(onClick = onDelete) { Icon(Icons.Default.Close, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun AddExtraDialog(onDismiss: () -> Unit, onAdd: (ExtraItem) -> Unit) {
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(MeasureUnit.NONE) }
    var aisle by remember { mutableStateOf(Aisle.AUTRE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Autre article") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Hors plats : papier toilette, lessive, café…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Article") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qty, onValueChange = { qty = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Qté") }, singleLine = true, modifier = Modifier.weight(0.8f),
                    )
                    DropdownField(
                        label = "Unité", value = unit, options = MeasureUnit.entries,
                        optionLabel = { if (it == MeasureUnit.NONE) "—" else it.label },
                        onSelected = { unit = it }, modifier = Modifier.weight(1.2f),
                    )
                }
                DropdownField(label = "Rayon", value = aisle, options = Aisle.entries, optionLabel = { "${it.emoji} ${it.label}" }, onSelected = { aisle = it })
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onAdd(ExtraItem(name = name.trim(), quantity = qty.replace(',', '.').toDoubleOrNull(), unit = unit.name, aisle = aisle.name))
                },
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

fun shoppingShareText(allLines: List<ShoppingLine>, extras: List<ExtraItem>, groupByAisle: Boolean): String {
    val lines = allLines.filter { !it.inStock }  // ce qui est en stock n'a rien à faire sur une liste partagée
    val sb = StringBuilder("LISTE DE COURSES\n")
    fun mark(checked: Boolean) = if (checked) "☑" else "☐"
    if (groupByAisle) {
        Aisle.entries.forEach { aisle ->
            val group = lines.filter { it.aisle == aisle }
            if (group.isNotEmpty()) {
                sb.appendLine().appendLine("${aisle.emoji} ${aisle.label.uppercase()}")
                group.forEach { sb.appendLine("${mark(it.checked)} ${it.shareLabel}") }
            }
        }
    } else {
        sb.appendLine()
        lines.sortedBy { it.name.lowercase() }.forEach { sb.appendLine("${mark(it.checked)} ${it.shareLabel}") }
    }
    if (extras.isNotEmpty()) {
        sb.appendLine().appendLine("📝 AUTRES")
        extras.forEach {
            val q = ShoppingAggregator.formatIngredient(it.quantity, it.unitEnum)
            sb.appendLine("${mark(it.checked)} ${it.name}${if (q.isBlank()) "" else " — $q"}")
        }
    }
    return sb.toString().trimEnd()
}
