package com.tkos.miam.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tkos.miam.data.MiamRepository
import com.tkos.miam.data.model.PlanEntryWithRecipe
import com.tkos.miam.data.model.Recipe
import com.tkos.miam.data.model.Slot
import com.tkos.miam.ui.DateUtil
import com.tkos.miam.ui.components.ConfirmDialog
import com.tkos.miam.ui.components.RecipeImage
import com.tkos.miam.ui.components.Stepper
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    repository: MiamRepository,
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onOpenShopping: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val today = remember { DateUtil.today() }
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    val weekStart = remember(weekOffset) { DateUtil.startOfWeek(today).plusWeeks(weekOffset.toLong()) }
    val range = remember(weekStart) { DateUtil.weekWindow(weekStart) }
    val entries by repository.planRange(range.first, range.second).collectAsStateWithLifecycle(initialValue = emptyList())
    val recipes by repository.recipes.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by repository.settings.settings.collectAsStateWithLifecycle(initialValue = com.tkos.miam.data.settings.Settings())

    var pickTarget by remember { mutableStateOf<Pair<LocalDate, Slot>?>(null) }
    var editEntry by remember { mutableStateOf<PlanEntryWithRecipe?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Planning") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") } },
                actions = {
                    IconButton(onClick = { weekOffset = 0 }, enabled = weekOffset != 0) { Icon(Icons.Default.Today, contentDescription = "Cette semaine") }
                    IconButton(onClick = { confirmClear = true }, enabled = entries.isNotEmpty()) { Icon(Icons.Default.DeleteSweep, contentDescription = "Vider la semaine") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenShopping,
                icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                text = { Text("Liste de courses") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { weekOffset-- }) { Icon(Icons.Default.ChevronLeft, contentDescription = "Semaine précédente") }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when (weekOffset) { 0 -> "Cette semaine"; 1 -> "Semaine prochaine"; -1 -> "Semaine dernière"; else -> "Semaine ${if (weekOffset > 0) "+" else ""}$weekOffset" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(DateUtil.weekLabel(weekStart), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { weekOffset++ }) { Icon(Icons.Default.ChevronRight, contentDescription = "Semaine suivante") }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items((0..6).map { weekStart.plusDays(it.toLong()) }, key = { it.toEpochDay() }) { day ->
                    val isToday = day == today
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                DateUtil.dayLabel(day) + if (isToday) "  · aujourd'hui" else "",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Slot.entries.forEach { slot ->
                                val forSlot = entries.filter { it.entry.epochDay == day.toEpochDay() && it.entry.slotEnum == slot }
                                SlotRow(
                                    slot = slot,
                                    entries = forSlot,
                                    onAdd = { pickTarget = day to slot },
                                    onEntryClick = { editEntry = it },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- Choix d'un plat pour une case ----
    pickTarget?.let { (day, slot) ->
        RecipePickerSheet(
            title = "${DateUtil.relativeDay(day)} · ${slot.label}",
            recipes = recipes,
            defaultServings = settings.defaultServings,
            onDismiss = { pickTarget = null },
            onPick = { recipe, pers ->
                pickTarget = null
                scope.launch { repository.addToPlan(day.toEpochDay(), slot, recipe.id, pers) }
            },
        )
    }

    // ---- Édition d'une case existante ----
    editEntry?.let { e ->
        var pers by remember(e.entry.id) { mutableIntStateOf(e.entry.servings) }
        AlertDialog(
            onDismissRequest = { editEntry = null },
            title = { Text(e.recipe.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${DateUtil.relativeDay(LocalDate.ofEpochDay(e.entry.epochDay))} · ${e.entry.slotEnum.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Personnes")
                        Stepper(value = pers, onValueChange = { pers = it })
                    }
                    TextButton(onClick = { editEntry = null; onOpenRecipe(e.recipe.id) }) { Text("Voir la recette") }
                }
            },
            confirmButton = {
                Button(onClick = {
                    editEntry = null
                    scope.launch { repository.updatePlanEntry(e.entry.copy(servings = pers)) }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    editEntry = null
                    scope.launch { repository.removeFromPlan(e.entry.id) }
                }) { Text("Retirer", color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Vider la semaine ?",
            text = "Tous les plats prévus du ${DateUtil.weekLabel(weekStart)} seront retirés du planning.",
            confirmLabel = "Vider",
            destructive = true,
            onConfirm = { scope.launch { repository.clearPlan(range.first, range.second) } },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun SlotRow(slot: Slot, entries: List<PlanEntryWithRecipe>, onAdd: () -> Unit, onEntryClick: (PlanEntryWithRecipe) -> Unit) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
        Text(
            slot.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp).padding(top = 8.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            entries.forEach { e ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onEntryClick(e) }
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RecipeImage(e.recipe.imagePath, Modifier.size(36.dp).clip(MaterialTheme.shapes.extraSmall), fallbackIconSize = 18)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.recipe.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${e.entry.servings} pers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (entries.isEmpty()) "Ajouter un plat" else "Ajouter un autre plat",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Feuille de sélection d'un plat avec recherche + nombre de personnes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipePickerSheet(
    title: String,
    recipes: List<Recipe>,
    defaultServings: Int,
    onDismiss: () -> Unit,
    onPick: (Recipe, Int) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var pers by remember { mutableIntStateOf(defaultServings) }
    val filtered = recipes.filter { query.isBlank() || norm(it.name).contains(norm(query)) }
        .sortedWith(compareByDescending<Recipe> { it.isFavorite }.thenBy { it.name.lowercase() })

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Personnes", style = MaterialTheme.typography.labelLarge)
                Stepper(value = pers, onValueChange = { pers = it })
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Rechercher…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.fillMaxWidth().height(420.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                items(filtered, key = { it.id }) { r ->
                    ListItem(
                        headlineContent = { Text(r.name) },
                        supportingContent = { Text("${r.servings} pers. · ${r.cuisineEnum.label}") },
                        leadingContent = { RecipeImage(r.imagePath, Modifier.size(44.dp).clip(MaterialTheme.shapes.small), fallbackIconSize = 20) },
                        trailingContent = { if (r.isFavorite) Text("💛") },
                        modifier = Modifier.clickable { onPick(r, pers) },
                    )
                }
                if (filtered.isEmpty()) {
                    item { Text("Aucun plat trouvé.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
                }
            }
        }
    }
}

private fun norm(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
