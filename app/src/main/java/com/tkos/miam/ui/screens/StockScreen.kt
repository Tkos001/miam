package com.tkos.miam.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.PushPin as PushPinOutlined
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tkos.miam.data.MiamRepository
import com.tkos.miam.data.model.Aisle
import com.tkos.miam.data.model.MeasureUnit
import com.tkos.miam.data.model.StockItem
import com.tkos.miam.data.model.UnitFamily
import com.tkos.miam.data.shopping.ShoppingAggregator
import com.tkos.miam.ui.components.ConfirmDialog
import com.tkos.miam.ui.components.DropdownField
import kotlinx.coroutines.launch

/**
 * Ce qu'il y a à la maison. Alimenté par « Courses terminées », vidé par les repas cochés « mangé »,
 * ajustable à la main (− 1 L de lait bu au petit-déjeuner, bouteille d'huile finie → supprimer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    repository: MiamRepository,
    onBack: () -> Unit,
    onOpenReceipt: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val stock by repository.stock.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<StockItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("En stock") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") } },
                actions = {
                    IconButton(onClick = onOpenReceipt) { Icon(Icons.Default.ReceiptLong, contentDescription = "Lire un ticket de caisse") }
                    IconButton(onClick = { confirmClear = true }, enabled = stock.isNotEmpty()) { Icon(Icons.Default.DeleteSweep, contentDescription = "Vider le stock") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Ajouter") },
            )
        },
    ) { padding ->
        if (stock.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🧺", style = MaterialTheme.typography.headlineLarge)
                Text("Stock vide", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "Après les courses, « Courses terminées » met ici ce que tu as acheté. Ce qui est en stock n'est plus " +
                        "proposé à l'achat ; cocher un repas « mangé » retire ses ingrédients. Tu peux aussi ajouter à la main " +
                        "ce que tu as déjà (épingle 📌 = basique de placard, jamais remis sur la liste), ou lire un ticket de caisse (icône 🧾 en haut).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                Aisle.entries.forEach { aisle ->
                    val group = stock.filter { it.aisleEnum == aisle }
                    if (group.isNotEmpty()) {
                        item(key = "h_${aisle.name}") {
                            Text(
                                "${aisle.emoji} ${aisle.label}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                            )
                        }
                        items(group, key = { it.id }) { item ->
                            StockRow(
                                item = item,
                                onClick = { edit = item },
                                onMinus = { scope.launch { adjust(repository, item, -1, snackbar) } },
                                onPlus = { scope.launch { adjust(repository, item, +1, snackbar) } },
                                onTogglePin = { scope.launch { repository.updateStockItem(item.copy(always = !item.always)) } },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        StockItemDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { name, qty, unit, aisle, always ->
                showAdd = false
                scope.launch { repository.addStockManually(name, qty, unit, aisle, always) }
            },
            onDelete = null,
        )
    }

    edit?.let { item ->
        StockItemDialog(
            initial = item,
            onDismiss = { edit = null },
            onSave = { name, qty, unit, aisle, always ->
                edit = null
                scope.launch {
                    repository.updateStockItem(
                        item.copy(name = name, quantity = qty?.let { it * unit.factor }, family = unit.family.name, aisle = aisle.name, always = always,
                            key = ShoppingAggregator.keyOf(name, unit.family))
                    )
                }
            },
            onDelete = { edit = null; scope.launch { repository.deleteStockItem(item.id) } },
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Vider le stock ?",
            text = "Tous les articles seront retirés ; tout ce que demandent les repas reviendra sur la liste de courses.",
            confirmLabel = "Vider",
            destructive = true,
            onConfirm = { scope.launch { repository.clearStock() } },
            onDismiss = { confirmClear = false },
        )
    }
}

/** Pas d'ajustement adapté à l'ordre de grandeur : 6 L de lait → ±1 L ; 300 g → ±100 g ; 6 œufs → ±1. */
private fun stepFor(item: StockItem): Double {
    val q = item.quantity ?: return 1.0
    return when (item.familyEnum) {
        UnitFamily.WEIGHT, UnitFamily.VOLUME -> when {
            q >= 2000 -> 1000.0
            q >= 500 -> 100.0
            q >= 100 -> 50.0
            else -> 10.0
        }
        else -> 1.0
    }
}

/** « − » ne descend jamais sous zéro et ne supprime pas : à 0 l'article reste affiché « épuisé » (suppression via la fiche). */
private suspend fun adjust(repository: MiamRepository, item: StockItem, sign: Int, snackbar: SnackbarHostState) {
    val q = item.quantity ?: return
    val next = (q + sign * stepFor(item)).coerceAtLeast(0.0)
    if (next == q) return
    repository.updateStockItem(item.copy(quantity = next))
    if (next == 0.0) snackbar.showSnackbar("${item.name} épuisé : revient sur la liste de courses. Touche l'article pour le supprimer.")
}

@Composable
private fun StockRow(item: StockItem, onClick: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit, onTogglePin: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val empty = item.quantity != null && item.quantity <= 0.0 && !item.always
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Text(
                when {
                    item.always -> "Basique : toujours considéré en stock"
                    item.quantity == null -> "Présent (sans quantité) · supprimer quand c'est fini"
                    empty -> "Épuisé · touche l'article pour le supprimer ou le recompléter"
                    else -> "Touche le chiffre pour saisir la quantité"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (empty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.quantity != null) {
            FilledTonalIconButton(onClick = onMinus, enabled = item.quantity > 0.0, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Remove, contentDescription = "Moins") }
            Text(
                if (empty) "0" else item.quantityLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (empty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 64.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, contentDescription = "Plus") }
        }
        IconButton(onClick = onTogglePin) {
            Icon(
                if (item.always) Icons.Filled.PushPin else Icons.Outlined.PushPinOutlined,
                contentDescription = if (item.always) "Ne plus traiter comme basique" else "Basique de placard",
                tint = if (item.always) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Unité d'affichage la plus lisible pour pré-remplir le dialogue d'édition. */
private fun displayUnit(item: StockItem): Pair<Double?, MeasureUnit> {
    val q = item.quantity
    return when (item.familyEnum) {
        UnitFamily.WEIGHT -> if (q != null && q >= 1000) (q / 1000) to MeasureUnit.KG else q to MeasureUnit.G
        UnitFamily.VOLUME -> when {
            q != null && q >= 1000 -> (q / 1000) to MeasureUnit.L
            q != null && q >= 100 && q % 10 == 0.0 -> (q / 10) to MeasureUnit.CL
            else -> q to MeasureUnit.ML
        }
        else -> q to (MeasureUnit.entries.firstOrNull { it.family == item.familyEnum } ?: MeasureUnit.NONE)
    }
}

@Composable
private fun StockItemDialog(
    initial: StockItem?,
    onDismiss: () -> Unit,
    onSave: (name: String, qty: Double?, unit: MeasureUnit, aisle: Aisle, always: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val (q0, u0) = initial?.let { displayUnit(it) } ?: (null to MeasureUnit.NONE)
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var qty by remember { mutableStateOf(q0?.let { ShoppingAggregator.fmt(it) } ?: "") }
    var unit by remember { mutableStateOf(u0) }
    var aisle by remember { mutableStateOf(initial?.aisleEnum ?: Aisle.AUTRE) }
    var always by remember { mutableStateOf(initial?.always ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Ajouter au stock" else "Article en stock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Row(Modifier.fillMaxWidth().clickable { always = !always }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = always, onCheckedChange = { always = it })
                    Column {
                        Text("Basique de placard")
                        Text("Sel, huile, farine… : jamais remis sur la liste tant qu'il est ici.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "Sans quantité = « présent » : couvre toute demande jusqu'à ce que tu le supprimes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), qty.replace(',', '.').toDoubleOrNull(), unit, aisle, always) }) {
                Text(if (initial == null) "Ajouter" else "Enregistrer")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Fini / supprimer", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}
