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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tkos.miam.data.MiamRepository
import com.tkos.miam.data.model.MealWithRecipe
import com.tkos.miam.data.model.Recipe
import com.tkos.miam.ui.components.ConfirmDialog
import com.tkos.miam.ui.components.RecipeImage
import com.tkos.miam.ui.components.Stepper
import kotlinx.coroutines.launch
import java.text.Normalizer

/**
 * La liste des repas : les plats qu'on compte faire, dans l'ordre qu'on veut, sans date.
 * Cocher = mangé / fini : les ingrédients sortent du stock et le repas sort du calcul de la liste de courses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealsScreen(
    repository: MiamRepository,
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onOpenShopping: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val meals by repository.meals.collectAsStateWithLifecycle(initialValue = emptyList())
    val recipes by repository.recipes.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by repository.settings.settings.collectAsStateWithLifecycle(initialValue = com.tkos.miam.data.settings.Settings())

    var showPicker by remember { mutableStateOf(false) }
    var editMeal by remember { mutableStateOf<MealWithRecipe?>(null) }
    var confirmClearDone by remember { mutableStateOf(false) }

    val todo = meals.filter { !it.meal.done }
    val done = meals.filter { it.meal.done }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Mes repas") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") } },
                actions = {
                    IconButton(onClick = { confirmClearDone = true }, enabled = done.isNotEmpty()) { Icon(Icons.Default.DeleteSweep, contentDescription = "Effacer les repas mangés") }
                    IconButton(onClick = onOpenShopping) { Icon(Icons.Default.ShoppingCart, contentDescription = "Liste de courses") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPicker = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Ajouter un repas") },
            )
        },
    ) { padding ->
        if (meals.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🍽️", style = MaterialTheme.typography.headlineLarge)
                Text("Aucun repas prévu", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "Ajoute les plats que tu comptes faire, pour la semaine ou deux. Leurs ingrédients forment la liste de courses ; " +
                        "quand un plat est mangé, coche-le : ses ingrédients sortent du stock.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                FilledTonalButton(onClick = { showPicker = true }, modifier = Modifier.padding(top = 16.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Ajouter un repas")
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                item(key = "h_todo") {
                    Header("À faire", "${todo.size} repas")
                }
                if (todo.isEmpty()) {
                    item(key = "e_todo") {
                        Text(
                            "Tout est mangé. Ajoute les prochains repas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(todo, key = { it.meal.id }) { m ->
                    val idx = todo.indexOf(m)
                    MealRow(
                        m = m,
                        canUp = idx > 0,
                        canDown = idx < todo.lastIndex,
                        onCheck = { scope.launch { repository.setMealDone(m.meal.id, true); snackbar.showSnackbar("« ${m.recipe.name} » mangé : ingrédients retirés du stock") } },
                        onClick = { editMeal = m },
                        onUp = { scope.launch { repository.moveMeal(m.meal.id, -1) } },
                        onDown = { scope.launch { repository.moveMeal(m.meal.id, +1) } },
                    )
                }
                if (done.isNotEmpty()) {
                    item(key = "h_done") { Header("Mangés", "${done.size}") }
                    items(done, key = { it.meal.id }) { m ->
                        MealRow(
                            m = m,
                            canUp = false, canDown = false,
                            onCheck = { scope.launch { repository.setMealDone(m.meal.id, false) } },
                            onClick = { editMeal = m },
                            onUp = {}, onDown = {},
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        RecipePickerSheet(
            title = "Ajouter un repas",
            recipes = recipes,
            defaultServings = settings.defaultServings,
            onDismiss = { showPicker = false },
            onPick = { recipe, pers ->
                showPicker = false
                scope.launch { repository.addMeal(recipe.id, pers) }
            },
        )
    }

    editMeal?.let { e ->
        var pers by remember(e.meal.id) { mutableIntStateOf(e.meal.servings) }
        AlertDialog(
            onDismissRequest = { editMeal = null },
            title = { Text(e.recipe.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Personnes")
                        Stepper(value = pers, onValueChange = { pers = it })
                    }
                    TextButton(onClick = { editMeal = null; onOpenRecipe(e.recipe.id) }) { Text("Voir la recette") }
                }
            },
            confirmButton = {
                Button(onClick = {
                    editMeal = null
                    scope.launch { repository.updateMeal(e.meal.copy(servings = pers)) }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    editMeal = null
                    scope.launch { repository.removeMeal(e.meal.id) }
                }) { Text("Retirer", color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    if (confirmClearDone) {
        ConfirmDialog(
            title = "Effacer les repas mangés ?",
            text = "${done.size} repas cochés seront retirés de la liste. Le stock n'est pas modifié.",
            confirmLabel = "Effacer",
            destructive = true,
            onConfirm = { scope.launch { repository.deleteDoneMeals() } },
            onDismiss = { confirmClearDone = false },
        )
    }
}

@Composable
private fun Header(title: String, trailing: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MealRow(
    m: MealWithRecipe,
    canUp: Boolean,
    canDown: Boolean,
    onCheck: () -> Unit,
    onClick: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    val done = m.meal.done
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = done, onCheckedChange = { onCheck() })
        RecipeImage(m.recipe.imagePath, Modifier.size(44.dp).clip(MaterialTheme.shapes.small), fallbackIconSize = 20)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                m.recipe.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${m.meal.servings} pers." + (if (m.recipe.proteinEnum != com.tkos.miam.data.model.Protein.AUTRE) "  ${m.recipe.proteinEnum.emoji}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!done) {
            IconButton(onClick = onUp, enabled = canUp) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Monter") }
            IconButton(onClick = onDown, enabled = canDown) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Descendre") }
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
