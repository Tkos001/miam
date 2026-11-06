package com.tkos.miam.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tkos.miam.data.MiamRepository
import com.tkos.miam.data.model.Aisle
import com.tkos.miam.data.model.Cuisine
import com.tkos.miam.data.model.MeasureUnit
import com.tkos.miam.ui.components.DropdownField
import com.tkos.miam.ui.components.RecipeImage
import com.tkos.miam.ui.components.Stepper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditScreen(
    repository: MiamRepository,
    recipeId: Long?,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val vm: RecipeEditViewModel = viewModel(
        key = "edit_${recipeId ?: "new"}",
        factory = viewModelFactory { initializer { RecipeEditViewModel(repository, recipeId) } },
    )
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        uris.forEach { vm.addPhoto(it) }
    }
    val photos by (if (recipeId != null) repository.photos(recipeId) else kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val coverPath by (if (recipeId != null) repository.recipe(recipeId) else kotlinx.coroutines.flow.flowOf(null))
        .collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(vm.error) {
        if (vm.error != null) {
            kotlinx.coroutines.delay(2500)
            vm.error = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (recipeId == null) "Nouveau plat" else "Modifier le plat") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") } },
                actions = {
                    TextButton(onClick = { vm.save(onSaved) }, enabled = !vm.saving && vm.loaded) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Enregistrer")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Photos (galerie) ----
            item {
                Text("Photos", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    items(photos, key = { it.id }) { ph ->
                        val isCover = ph.path == coverPath?.recipe?.imagePath
                        Box(Modifier.size(120.dp).clip(MaterialTheme.shapes.medium)) {
                            RecipeImage(ph.path, Modifier.fillMaxSize(), fallbackIconSize = 32)
                            Text(
                                if (ph.isCatalog) "Illustration" else "Photo",
                                style = MaterialTheme.typography.labelSmall, color = Color.White,
                                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                                    .clip(MaterialTheme.shapes.extraSmall).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                            Row(Modifier.align(Alignment.BottomEnd).padding(4.dp)) {
                                IconButton(onClick = { vm.setCover(ph) }, modifier = Modifier.size(30.dp)) {
                                    Icon(if (isCover) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = "Couverture", tint = Color(0xFFFFD54F))
                                }
                                if (!ph.isCatalog) {
                                    IconButton(onClick = { vm.removePhoto(ph) }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Retirer", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                    items(vm.pendingPhotos, key = { it.toString() }) { uri ->
                        Box(Modifier.size(120.dp).clip(MaterialTheme.shapes.medium)) {
                            coil.compose.AsyncImage(model = uri, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            IconButton(onClick = { vm.removePendingPhoto(uri) }, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(30.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Retirer", tint = Color.White)
                            }
                        }
                    }
                    item {
                        Box(
                            Modifier.size(120.dp).clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null)
                                Text("Ajouter", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                Text(
                    "L'illustration du catalogue reste ; tes photos s'ajoutent à côté. ★ = image affichée dans les listes.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // ---- Infos ----
            item {
                OutlinedTextField(
                    value = vm.name, onValueChange = { vm.name = it },
                    label = { Text("Nom du plat *") }, singleLine = true,
                    isError = vm.error != null && vm.name.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            item {
                OutlinedTextField(
                    value = vm.description, onValueChange = { vm.description = it },
                    label = { Text("Description (optionnel)") }, maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = vm.prepMinutes, onValueChange = { vm.prepMinutes = it.filter { c -> c.isDigit() } },
                        label = { Text("Prépa (min)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = vm.cookMinutes, onValueChange = { vm.cookMinutes = it.filter { c -> c.isDigit() } },
                        label = { Text("Cuisson (min)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Pour combien de personnes ?", style = MaterialTheme.typography.labelLarge)
                        Text("Les quantités ci-dessous sont données pour ce nombre.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Stepper(value = vm.servings, onValueChange = { vm.servings = it })
                }
            }
            item {
                DropdownField(
                    label = "Style de cuisine",
                    value = vm.cuisine,
                    options = Cuisine.entries,
                    optionLabel = { "${it.emoji} ${it.label}" },
                    onSelected = { vm.cuisine = it },
                )
            }

            // ---- Ingrédients ----
            item {
                Text("Ingrédients", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            itemsIndexed(vm.ingredients, key = { _, it -> it.uid }) { index, ing ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = ing.name,
                                onValueChange = { v -> vm.updateIngredient(ing.uid) { it.copy(name = v) } },
                                label = { Text("Ingrédient") }, singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            ReorderButtons(
                                canUp = index > 0, canDown = index < vm.ingredients.lastIndex,
                                onUp = { vm.moveIngredient(ing.uid, -1) }, onDown = { vm.moveIngredient(ing.uid, 1) },
                                onRemove = { vm.removeIngredient(ing.uid) },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = ing.quantity,
                                onValueChange = { v -> vm.updateIngredient(ing.uid) { it.copy(quantity = v.filter { c -> c.isDigit() || c == '.' || c == ',' }) } },
                                label = { Text("Qté") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(0.8f),
                            )
                            DropdownField(
                                label = "Unité",
                                value = ing.unit,
                                options = MeasureUnit.entries,
                                optionLabel = { if (it == MeasureUnit.NONE) "—" else it.label },
                                onSelected = { u -> vm.updateIngredient(ing.uid) { it.copy(unit = u) } },
                                modifier = Modifier.weight(1.2f),
                            )
                        }
                        DropdownField(
                            label = "Rayon",
                            value = ing.aisle,
                            options = Aisle.entries,
                            optionLabel = { "${it.emoji} ${it.label}" },
                            onSelected = { a -> vm.updateIngredient(ing.uid) { it.copy(aisle = a) } },
                        )
                    }
                }
            }
            item {
                OutlinedButton(onClick = { vm.addIngredient() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Ajouter un ingrédient")
                }
            }

            // ---- Étapes ----
            item {
                Text("Préparation (étapes)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            itemsIndexed(vm.steps, key = { _, it -> it.uid }) { index, step ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = step.text,
                            onValueChange = { vm.updateStep(step.uid, it) },
                            label = { Text("Étape ${index + 1}") },
                            minLines = 2, maxLines = 5,
                            modifier = Modifier.weight(1f),
                        )
                        ReorderButtons(
                            canUp = index > 0, canDown = index < vm.steps.lastIndex,
                            onUp = { vm.moveStep(step.uid, -1) }, onDown = { vm.moveStep(step.uid, 1) },
                            onRemove = { vm.removeStep(step.uid) },
                        )
                    }
                }
            }
            item {
                OutlinedButton(onClick = { vm.addStep() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Ajouter une étape")
                }
            }
            item {
                Button(onClick = { vm.save(onSaved) }, enabled = !vm.saving && vm.loaded, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)) {
                    Text("Enregistrer")
                }
            }
        }
    }
}

@Composable
private fun ReorderButtons(canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row {
            IconButton(onClick = onUp, enabled = canUp, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Monter") }
            IconButton(onClick = onDown, enabled = canDown, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Descendre") }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
        }
    }
}
