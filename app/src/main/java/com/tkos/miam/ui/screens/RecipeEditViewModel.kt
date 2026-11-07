package com.tkos.miam.ui.screens

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkos.miam.data.MiamRepository
import com.tkos.miam.data.model.Aisle
import com.tkos.miam.data.model.Cuisine
import com.tkos.miam.data.model.Ingredient
import com.tkos.miam.data.model.MeasureUnit
import com.tkos.miam.data.model.Protein
import com.tkos.miam.data.model.RecipeKind
import com.tkos.miam.data.model.Recipe
import com.tkos.miam.data.model.Step
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/** Ligne d'ingrédient en cours d'édition (texte brut pour la quantité). */
data class IngredientDraft(
    val uid: String = UUID.randomUUID().toString(),
    val name: String = "",
    val quantity: String = "",
    val unit: MeasureUnit = MeasureUnit.NONE,
    val aisle: Aisle = Aisle.AUTRE,
) {
    fun toEntity(): Ingredient = Ingredient(
        name = name.trim(),
        quantity = quantity.replace(',', '.').trim().toDoubleOrNull(),
        unit = unit.name,
        aisle = aisle.name,
    )
}

data class StepDraft(val uid: String = UUID.randomUUID().toString(), val text: String = "")

class RecipeEditViewModel(private val repository: MiamRepository, private val recipeId: Long?) : ViewModel() {
    var loaded by mutableStateOf(recipeId == null)
        private set
    private var original: Recipe? = null

    var name by mutableStateOf("")
    var description by mutableStateOf("")
    /** Photos à ajouter après création (nouvelle recette uniquement). */
    val pendingPhotos = mutableStateListOf<Uri>()
    var prepMinutes by mutableStateOf("")
    var cookMinutes by mutableStateOf("")
    var servings by mutableStateOf(4)
    var cuisine by mutableStateOf(Cuisine.FRANCAISE)
    var protein by mutableStateOf(Protein.AUTRE)
    var kind by mutableStateOf(RecipeKind.PLAT)
    val ingredients = mutableStateListOf<IngredientDraft>()
    val steps = mutableStateListOf<StepDraft>()
    var error by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
        private set

    init {
        if (recipeId != null) {
            viewModelScope.launch {
                val d = repository.getRecipe(recipeId)
                if (d != null) {
                    original = d.recipe
                    name = d.recipe.name
                    description = d.recipe.description
                    prepMinutes = d.recipe.prepMinutes?.toString() ?: ""
                    cookMinutes = d.recipe.cookMinutes?.toString() ?: ""
                    servings = d.recipe.servings
                    cuisine = d.recipe.cuisineEnum
                    protein = d.recipe.proteinEnum
                    kind = d.recipe.kindEnum
                    ingredients.addAll(d.sortedIngredients.map {
                        IngredientDraft(
                            name = it.name,
                            quantity = it.quantity?.let { q -> formatQty(q) } ?: "",
                            unit = it.unitEnum,
                            aisle = it.aisleEnum,
                        )
                    })
                    steps.addAll(d.sortedSteps.map { StepDraft(text = it.text) })
                }
                loaded = true
            }
        } else {
            viewModelScope.launch {
                servings = repository.settings.settings.first().defaultServings
            }
            ingredients.add(IngredientDraft())
            steps.add(StepDraft())
        }
    }

    private fun formatQty(q: Double): String =
        if (q == Math.floor(q)) q.toLong().toString() else q.toString().replace('.', ',')

    fun addIngredient() = ingredients.add(IngredientDraft())
    fun updateIngredient(uid: String, transform: (IngredientDraft) -> IngredientDraft) {
        val i = ingredients.indexOfFirst { it.uid == uid }
        if (i >= 0) ingredients[i] = transform(ingredients[i])
    }
    fun removeIngredient(uid: String) { ingredients.removeAll { it.uid == uid } }
    fun moveIngredient(uid: String, delta: Int) = move(ingredients, uid, delta) { it.uid }

    fun addStep() = steps.add(StepDraft())
    fun updateStep(uid: String, text: String) {
        val i = steps.indexOfFirst { it.uid == uid }
        if (i >= 0) steps[i] = steps[i].copy(text = text)
    }
    fun removeStep(uid: String) { steps.removeAll { it.uid == uid } }
    fun moveStep(uid: String, delta: Int) = move(steps, uid, delta) { it.uid }

    private fun <T> move(list: MutableList<T>, uid: String, delta: Int, key: (T) -> String) {
        val i = list.indexOfFirst { key(it) == uid }
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        val item = list.removeAt(i)
        list.add(j, item)
    }

    /** Ajoute une photo : immédiat pour une recette existante, différé à l'enregistrement pour une nouvelle. */
    fun addPhoto(uri: Uri) {
        if (recipeId != null) viewModelScope.launch { repository.addPhoto(recipeId, uri) }
        else pendingPhotos.add(uri)
    }

    fun removePendingPhoto(uri: Uri) { pendingPhotos.remove(uri) }
    fun removePhoto(photo: com.tkos.miam.data.model.RecipePhoto) { viewModelScope.launch { repository.removePhoto(photo) } }
    fun setCover(photo: com.tkos.miam.data.model.RecipePhoto) { viewModelScope.launch { repository.setCover(photo.recipeId, photo.path) } }

    fun save(onSaved: (Long) -> Unit) {
        if (name.isBlank()) { error = "Donne un nom au plat."; return }
        val ings = ingredients.filter { it.name.isNotBlank() }.map { it.toEntity() }
        val sts = steps.filter { it.text.isNotBlank() }.map { Step(text = it.text.trim()) }
        saving = true
        viewModelScope.launch {
            val base = original ?: Recipe(name = "")
            val recipe = base.copy(
                name = name.trim(),
                description = description.trim(),
                prepMinutes = prepMinutes.trim().toIntOrNull(),
                cookMinutes = cookMinutes.trim().toIntOrNull(),
                servings = servings.coerceIn(1, 30),
                cuisine = cuisine.name,
                protein = protein.name,
                kind = kind.name,
                isBuiltIn = false,
            )
            val id = repository.saveRecipe(recipe, ings, sts)
            for (uri in pendingPhotos) repository.addPhoto(id, uri)
            pendingPhotos.clear()
            saving = false
            onSaved(id)
        }
    }
}
