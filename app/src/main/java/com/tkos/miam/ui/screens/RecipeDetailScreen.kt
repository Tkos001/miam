package com.tkos.miam.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tkos.miam.data.MiamRepository
import com.tkos.miam.data.model.RecipeWithDetails
import com.tkos.miam.data.model.Slot
import com.tkos.miam.data.shopping.ShoppingAggregator
import com.tkos.miam.ui.DateUtil
import com.tkos.miam.ui.components.ConfirmDialog
import com.tkos.miam.ui.components.RecipeImage
import com.tkos.miam.ui.components.Stepper
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    repository: MiamRepository,
    recipeId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenPlanner: () -> Unit,
) {
    val details by repository.recipe(recipeId).collectAsStateWithLifecycle(initialValue = null)
    val appSettings by repository.settings.settings.collectAsStateWithLifecycle(initialValue = com.tkos.miam.data.settings.Settings())
    var publishing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showPlanDialog by remember { mutableStateOf(false) }

    val d = details
    if (d == null) {
        Scaffold(topBar = {
            TopAppBar(title = {}, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") } })
        }) { Box(Modifier.padding(it)) }
        return
    }

    var servings by rememberSaveable(d.recipe.id) { mutableIntStateOf(d.recipe.servings) }
    val ratio = servings.toDouble() / d.recipe.servings.coerceAtLeast(1)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") } },
                actions = {
                    IconButton(onClick = { scope.launch { repository.setFavorite(d.recipe.id, !d.recipe.isFavorite) } }) {
                        Icon(
                            if (d.recipe.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favori",
                            tint = if (d.recipe.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Modifier") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Plus") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Partager / copier") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    shareText(context, "Recette : ${d.recipe.name}", recipeShareText(d, servings))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Dupliquer") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        repository.duplicateRecipe(d.recipe.id)
                                        snackbar.showSnackbar("Copie créée dans « Mes plats »")
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (publishing) "Publication…" else "Publier dans le catalogue") },
                                leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                                enabled = !publishing,
                                onClick = {
                                    menuOpen = false
                                    if (appSettings.githubToken.isBlank()) {
                                        scope.launch { snackbar.showSnackbar("Renseigne un token GitHub dans Paramètres → Contribuer au catalogue") }
                                    } else {
                                        publishing = true
                                        scope.launch {
                                            val r = repository.publishRecipe(d.recipe.id)
                                            publishing = false
                                            snackbar.showSnackbar(r.getOrElse { "Échec : ${it.message}" })
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPlanDialog = true },
                icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                text = { Text("Planifier") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 96.dp),
        ) {
            item {
                // ---- Carte « header » : image + titre ----
                RecipeImage(d.recipe.imagePath, Modifier.fillMaxWidth().height(260.dp), fallbackIconSize = 64)
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(d.recipe.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
                    if (d.recipe.description.isNotBlank()) {
                        Text(d.recipe.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (d.recipe.id in appSettings.dirtyRecipeIds && (d.recipe.remoteId != null || d.recipe.isBuiltIn)) {
                        Text(
                            "Modifiée ici, pas encore publiée dans le catalogue",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoChip("${d.recipe.cuisineEnum.emoji} ${d.recipe.cuisineEnum.label}")
                        d.recipe.prepMinutes?.let { InfoChip("Prépa ${it} min") }
                        d.recipe.cookMinutes?.let { InfoChip("Cuisson ${it} min") }
                    }
                }
            }
            item {
                // ---- Ingrédients ----
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Ingrédients", style = MaterialTheme.typography.titleMedium)
                            Stepper(value = servings, onValueChange = { servings = it }, suffix = " pers.")
                        }
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        d.sortedIngredients.forEach { ing ->
                            val qty = ShoppingAggregator.formatIngredient(ing.quantity?.let { it * ratio }, ing.unitEnum)
                            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    qty,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(96.dp),
                                )
                                Text(ing.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            item {
                // ---- Étapes : swipe horizontal ----
                Text("Préparation", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
                val steps = d.sortedSteps
                if (steps.isEmpty()) {
                    Text("Aucune étape renseignée.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                } else {
                    val pager = rememberPagerState(pageCount = { steps.size })
                    HorizontalPager(
                        state = pager,
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        pageSpacing = 12.dp,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    ) { page ->
                        val step = steps[page]
                        Card(
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                                    if (step.imagePath != null) {
                                        RecipeImage(step.imagePath, Modifier.fillMaxSize())
                                    } else {
                                        StepIllustration(page + 1, steps.size)
                                    }
                                }
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        "Étape ${page + 1} / ${steps.size}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                    )
                                    Text(
                                        step.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        repeat(steps.size) { i ->
                            Box(
                                Modifier
                                    .padding(3.dp)
                                    .size(if (i == pager.currentPage) 10.dp else 7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == pager.currentPage) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                    )
                            )
                        }
                    }
                    Text(
                        "Glisse vers la gauche pour l'étape suivante",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Supprimer ce plat ?",
            text = "« ${d.recipe.name} » sera retiré de tes plats et du planning.",
            confirmLabel = "Supprimer",
            destructive = true,
            onConfirm = { scope.launch { repository.deleteRecipe(d.recipe.id); onBack() } },
            onDismiss = { confirmDelete = false },
        )
    }

    if (showPlanDialog) {
        PlanRecipeDialog(
            recipeName = d.recipe.name,
            defaultServings = servings,
            onDismiss = { showPlanDialog = false },
            onConfirm = { date, slot, pers ->
                showPlanDialog = false
                scope.launch {
                    repository.addToPlan(date.toEpochDay(), slot, d.recipe.id, pers)
                    val res = snackbar.showSnackbar("Ajouté : ${DateUtil.relativeDay(date)} ${slot.label.lowercase()}", actionLabel = "Voir")
                    if (res == androidx.compose.material3.SnackbarResult.ActionPerformed) onOpenPlanner()
                }
            },
        )
    }
}

@Composable
private fun InfoChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Petite illustration d'étape quand il n'y a pas d'image : grand numéro sur un fond coloré + emoji. */
@Composable
private fun StepIllustration(index: Int, total: Int) {
    val emojis = listOf("🔪", "🍳", "🥣", "🔥", "🧂", "⏱️", "🍽️", "🥄", "🧑‍🍳", "✨")
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emojis[(index - 1) % emojis.size], style = MaterialTheme.typography.headlineLarge)
            Text(
                "$index",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

/** Dialogue « Planifier » : jour (7 prochains jours), midi/soir, nombre de personnes. */
@Composable
fun PlanRecipeDialog(
    recipeName: String,
    defaultServings: Int,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, Slot, Int) -> Unit,
) {
    val today = remember { DateUtil.today() }
    val days = remember { (0..13).map { today.plusDays(it.toLong()) } }
    var date by remember { mutableStateOf(today) }
    var slot by remember { mutableStateOf(Slot.SOIR) }
    var pers by remember { mutableIntStateOf(defaultServings) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Planifier « $recipeName »") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Jour", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(days.size) { i ->
                        val dd = days[i]
                        FilterChip(
                            selected = dd == date,
                            onClick = { date = dd },
                            label = { Text(if (i == 0) "Auj." else if (i == 1) "Demain" else "${DateUtil.dayShort(dd)} ${dd.dayOfMonth}") },
                        )
                    }
                }
                Text("Moment", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Slot.entries.forEach { s ->
                        FilterChip(selected = slot == s, onClick = { slot = s }, label = { Text(s.label) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Personnes", style = MaterialTheme.typography.labelLarge)
                    Stepper(value = pers, onValueChange = { pers = it })
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(date, slot, pers) }) { Text("Ajouter") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

fun recipeShareText(d: RecipeWithDetails, servings: Int): String {
    val ratio = servings.toDouble() / d.recipe.servings.coerceAtLeast(1)
    val sb = StringBuilder()
    sb.appendLine(d.recipe.name.uppercase())
    sb.appendLine("Pour $servings personnes")
    sb.appendLine()
    sb.appendLine("Ingrédients :")
    d.sortedIngredients.forEach { ing ->
        val q = ShoppingAggregator.formatIngredient(ing.quantity?.let { it * ratio }, ing.unitEnum)
        sb.appendLine(if (q.isBlank()) "• ${ing.name}" else "• $q ${ing.name}")
    }
    if (d.steps.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("Préparation :")
        d.sortedSteps.forEachIndexed { i, s -> sb.appendLine("${i + 1}. ${s.text}") }
    }
    return sb.toString().trimEnd()
}

fun shareText(context: android.content.Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

fun copyText(context: android.content.Context, label: String, text: String) {
    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
    cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
}
