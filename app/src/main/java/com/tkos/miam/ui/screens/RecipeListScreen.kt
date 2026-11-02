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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tkos.miam.data.MiamRepository
import com.tkos.miam.data.model.Cuisine
import com.tkos.miam.data.model.Recipe
import com.tkos.miam.ui.components.EmptyState
import com.tkos.miam.ui.components.RecipeImage
import kotlinx.coroutines.launch
import java.text.Normalizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    repository: MiamRepository,
    favoritesOnly: Boolean,
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onNewRecipe: () -> Unit,
) {
    val all by repository.recipes.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var onlyFav by rememberSaveable { mutableStateOf(favoritesOnly) }
    var cuisine by rememberSaveable { mutableStateOf<String?>(null) }

    val filtered = all.filter { r ->
        (!onlyFav || r.isFavorite) &&
            (cuisine == null || r.cuisine == cuisine) &&
            (query.isBlank() || norm(r.name).contains(norm(query)) || norm(r.description).contains(norm(query)))
    }
    val usedCuisines = all.map { it.cuisineEnum }.distinct()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (onlyFav) "Plats favoris" else "Mes plats") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewRecipe,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nouveau plat") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Rechercher un plat…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = onlyFav,
                        onClick = { onlyFav = !onlyFav },
                        label = { Text("Favoris") },
                        leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
                if (usedCuisines.size > 1) {
                    usedCuisines.forEach { c ->
                        item(key = c.name) {
                            FilterChip(
                                selected = cuisine == c.name,
                                onClick = { cuisine = if (cuisine == c.name) null else c.name },
                                label = { Text("${c.emoji} ${c.label}") },
                            )
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                EmptyState(
                    emoji = if (onlyFav) "💛" else "🍽️",
                    title = if (all.isEmpty()) "Aucun plat pour l'instant" else "Aucun résultat",
                    subtitle = if (all.isEmpty()) "Crée ton premier plat avec le bouton ci-dessous, ou restaure les plats de base dans les paramètres." else null,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.id }) { r ->
                        RecipeCard(
                            recipe = r,
                            onClick = { onOpenRecipe(r.id) },
                            onToggleFavorite = { scope.launch { repository.setFavorite(r.id, !r.isFavorite) } },
                        )
                    }
                }
            }
        }
    }
}

private fun norm(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

@Composable
fun RecipeCard(recipe: Recipe, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box {
            RecipeImage(recipe.imagePath, Modifier.fillMaxWidth().height(120.dp))
            IconButton(onClick = onToggleFavorite, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    if (recipe.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favori",
                    tint = if (recipe.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(
                recipe.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(40.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                val total = (recipe.prepMinutes ?: 0) + (recipe.cookMinutes ?: 0)
                if (total > 0) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("$total min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                }
                Text("${recipe.servings} pers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (recipe.cuisineEnum != Cuisine.FRANCAISE) {
                    Spacer(Modifier.width(6.dp))
                    Text(recipe.cuisineEnum.emoji, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
