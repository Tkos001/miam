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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tkos.miam.data.MiamRepository
import com.tkos.miam.data.ai.AiProvider
import com.tkos.miam.data.backup.RecipeDto
import com.tkos.miam.data.model.Cuisine
import com.tkos.miam.data.model.MeasureUnit
import com.tkos.miam.data.model.Protein
import com.tkos.miam.data.model.RecipeKind
import com.tkos.miam.data.settings.Settings
import com.tkos.miam.data.shopping.ShoppingAggregator
import kotlinx.coroutines.launch

/** Demander une recette à l'assistant (compte de l'utilisateur), la relire, l'ajouter à « Mes plats ». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    repository: MiamRepository,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings by repository.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val stockCount by repository.stockCount.collectAsStateWithLifecycle(initialValue = 0)
    val configured = AiProvider.fromName(settings.aiProvider) != AiProvider.NONE
    var request by rememberSaveable { mutableStateOf("") }
    var useStock by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<RecipeDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var added by remember { mutableStateOf<Set<String>>(emptySet()) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Demander une recette") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") } },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Paramètres") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!configured) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Assistant non configuré", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Renseigne ton fournisseur et ta clé API dans Paramètres → Assistant IA. L'app utilise ton compte, rien ne passe ailleurs.",
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp),
                            )
                            FilledTonalButton(onClick = onOpenSettings, modifier = Modifier.padding(top = 10.dp)) { Text("Ouvrir les paramètres") }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = request, onValueChange = { request = it },
                    label = { Text("Qu'est-ce que tu veux manger ?") },
                    placeholder = { Text("Ex. : un plat rapide avec du poulet, un dessert sans œufs, une soupe pour 2…") },
                    minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth().clickable { useStock = !useStock }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useStock, onCheckedChange = { useStock = it })
                    Text("Avec ce que j'ai en stock ($stockCount articles)", style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    enabled = configured && !busy,
                    onClick = {
                        busy = true; error = null
                        scope.launch {
                            runCatching { repository.askRecipes(request, useStock) }
                                .onSuccess { results = it; if (it.isEmpty()) error = "Aucune recette exploitable dans la réponse. Réessaie en précisant la demande." }
                                .onFailure { error = it.message ?: it.javaClass.simpleName }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) { CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Je réfléchis…") }
                    else { Icon(Icons.Default.AutoAwesome, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Proposer des recettes") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            }
            items(results, key = { it.name }) { r ->
                RecipeProposal(
                    r = r,
                    added = r.name in added,
                    onAdd = {
                        scope.launch {
                            val id = repository.importRecipeDto(r)
                            added = added + r.name
                            val res = snackbar.showSnackbar("« ${r.name} » ajoutée à mes plats", actionLabel = "Voir")
                            if (res == androidx.compose.material3.SnackbarResult.ActionPerformed) onOpenRecipe(id)
                        }
                    },
                )
            }
            if (results.isNotEmpty()) {
                item {
                    Text(
                        "Les recettes ajoutées sont modifiables comme les autres, et publiables dans le catalogue depuis leur menu ⋮ si tu veux les partager.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipeProposal(r: RecipeDto, added: Boolean, onAdd: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(r.name, style = MaterialTheme.typography.titleMedium)
            Text(r.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            Text(
                listOfNotNull(
                    "${Cuisine.fromName(r.cuisine).emoji} ${Cuisine.fromName(r.cuisine).label}",
                    Protein.fromName(r.protein).takeIf { it != Protein.AUTRE }?.let { "${it.emoji} ${it.label}" },
                    RecipeKind.fromName(r.kind).takeIf { it != RecipeKind.PLAT }?.let { "${it.emoji} ${it.label}" },
                    "👥 ${r.servings}",
                    r.prepMinutes?.let { "Prépa $it min" },
                    r.cookMinutes?.let { "Cuisson $it min" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
            )
            Text("Ingrédients", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
            r.ingredients.forEach { i ->
                val q = ShoppingAggregator.formatIngredient(i.quantity, MeasureUnit.fromName(i.unit))
                Text("• ${i.name}${if (q.isBlank()) "" else " — $q"}", style = MaterialTheme.typography.bodyMedium)
            }
            if (expanded) {
                Text("Étapes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                r.steps.forEachIndexed { k, s -> Text("${k + 1}. $s", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Masquer les étapes" else "Voir les étapes (${r.steps.size})") }
                Button(onClick = onAdd, enabled = !added) { Text(if (added) "Ajoutée ✓" else "Ajouter à mes plats") }
            }
        }
    }
}
