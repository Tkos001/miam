package com.tkos.miam.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tkos.miam.R
import com.tkos.miam.data.MiamRepository
import com.tkos.miam.data.model.PlanEntryWithRecipe
import com.tkos.miam.data.model.Recipe
import com.tkos.miam.data.model.Slot
import com.tkos.miam.ui.DateUtil
import com.tkos.miam.ui.components.RecipeImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: MiamRepository,
    onOpenRecipes: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenShopping: () -> Unit,
    onOpenFind: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onNewRecipe: () -> Unit,
) {
    val recipeCount by repository.recipeCount.collectAsStateWithLifecycle(initialValue = 0)
    val favoriteCount by repository.favoriteCount.collectAsStateWithLifecycle(initialValue = 0)
    val favorites by repository.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val recipes by repository.recipes.collectAsStateWithLifecycle(initialValue = emptyList())

    val today = remember { DateUtil.today() }
    val week = remember { DateUtil.weekWindow(DateUtil.startOfWeek(today)) }
    val planThisWeek by repository.planCount(week.first, week.second).collectAsStateWithLifecycle(initialValue = 0)
    val todayEntries by repository.planRange(today.toEpochDay(), today.toEpochDay())
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val shopping = remember { DateUtil.shoppingWindow() }
    val lines by repository.shoppingLines(shopping.first, shopping.second).collectAsStateWithLifecycle(initialValue = emptyList())
    val extrasCount by repository.extrasUncheckedCount.collectAsStateWithLifecycle(initialValue = 0)
    val toBuy = lines.count { !it.checked } + extrasCount
    val availableUpdate by repository.availableUpdate.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF2B84B)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Miam", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Paramètres")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Qu'est-ce qu'on mange ?",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 4.dp),
            )

            // ---- Mise à jour disponible ----
            availableUpdate?.let { info ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Miam ${info.latest} disponible", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Installée : ${info.installed}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        com.tkos.miam.ui.components.UpdateButton(info, Modifier.padding(top = 10.dp), compact = true)
                    }
                }
            }

            // ---- Chiffres ----
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Plats", recipeCount.toString(), Icons.Default.MenuBook, Modifier.weight(1f), onOpenRecipes)
                StatCard("Favoris", favoriteCount.toString(), Icons.Default.Favorite, Modifier.weight(1f), onOpenFavorites)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Repas prévus", "$planThisWeek", Icons.Default.CalendarMonth, Modifier.weight(1f), onOpenPlanner, "cette semaine")
                StatCard("À acheter", toBuy.toString(), Icons.Default.ShoppingCart, Modifier.weight(1f), onOpenShopping, "articles")
            }

            // ---- Aujourd'hui ----
            TodayCard(todayEntries, onOpenPlanner, onOpenRecipe)

            // ---- Navigation rapide ----
            Text("Navigation", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NavTile("Mes plats", Icons.Default.MenuBook, Modifier.weight(1f), onOpenRecipes)
                NavTile("Favoris", Icons.Default.Favorite, Modifier.weight(1f), onOpenFavorites)
                NavTile("Planning", Icons.Default.CalendarMonth, Modifier.weight(1f), onOpenPlanner)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NavTile("Ma liste", Icons.Default.ShoppingCart, Modifier.weight(1f), onOpenShopping)
                NavTile("Trouver", Icons.Default.Search, Modifier.weight(1f), onOpenFind)
                NavTile("Nouveau plat", Icons.Default.Add, Modifier.weight(1f), onNewRecipe, accent = true)
            }

            // ---- Favoris ----
            val carousel = if (favorites.isNotEmpty()) favorites else recipes.take(8)
            if (carousel.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (favorites.isNotEmpty()) "Plats favoris" else "Quelques plats", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tout voir",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { if (favorites.isNotEmpty()) onOpenFavorites() else onOpenRecipes() },
                    )
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                ) {
                    items(carousel, key = { it.id }) { r -> MiniRecipeCard(r) { onOpenRecipe(r.id) } }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    sub: String? = null,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 6.dp))
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NavTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit, accent: Boolean = false) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (accent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon, contentDescription = null,
                tint = if (accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TodayCard(entries: List<PlanEntryWithRecipe>, onOpenPlanner: () -> Unit, onOpenRecipe: (Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Aujourd'hui", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (entries.isEmpty()) {
                Text(
                    "Rien de prévu. Ajoute des plats au planning pour générer ta liste de courses.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 6.dp),
                )
                FilledTonalButton(onClick = onOpenPlanner, modifier = Modifier.padding(top = 10.dp)) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Planifier la semaine")
                }
            } else {
                Slot.entries.forEach { slot ->
                    val forSlot = entries.filter { it.entry.slotEnum == slot }
                    if (forSlot.isNotEmpty()) {
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                slot.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.width(48.dp),
                            )
                            Column {
                                forSlot.forEach { e ->
                                    Text(
                                        "${e.recipe.name} · ${e.entry.servings} pers.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.clickable { onOpenRecipe(e.recipe.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniRecipeCard(recipe: Recipe, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box {
            RecipeImage(recipe.imagePath, Modifier.fillMaxWidth().height(90.dp), fallbackIconSize = 28)
            if (recipe.isFavorite) {
                Icon(
                    Icons.Default.Favorite, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp),
                )
            }
        }
        Text(
            recipe.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(10.dp).height(40.dp),
        )
    }
}
