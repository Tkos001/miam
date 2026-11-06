package com.tkos.miam.data.backup

import com.tkos.miam.data.model.ExtraItem
import com.tkos.miam.data.model.Ingredient
import com.tkos.miam.data.model.PlanEntry
import com.tkos.miam.data.model.Recipe
import com.tkos.miam.data.model.RecipeWithDetails
import com.tkos.miam.data.model.Step
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Format d'export/import JSON. Les images ne sont pas incluses (chemins locaux).
 * Même format prévu pour l'import depuis le futur site de recettes.
 */
@Serializable
data class BackupFile(
    val format: String = "miam-backup",
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val recipes: List<RecipeDto> = emptyList(),
    val plan: List<PlanDto> = emptyList(),
    val extras: List<ExtraDto> = emptyList(),
)

@Serializable
data class RecipeDto(
    val name: String,
    val description: String = "",
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val servings: Int = 4,
    val isFavorite: Boolean = false,
    val cuisine: String = "FRANCAISE",
    val sourceUrl: String? = null,
    val remoteId: String? = null,
    /** Image distante (catalogue) : téléchargée à la sync, jamais utilisée pour l'export local. */
    val imageUrl: String? = null,
    /** Photos supplémentaires (galerie), publiées depuis l'app. */
    val imageUrls: List<String> = emptyList(),
    val ingredients: List<IngredientDto> = emptyList(),
    val steps: List<String> = emptyList(),
)

@Serializable
data class IngredientDto(
    val name: String,
    val quantity: Double? = null,
    val unit: String = "NONE",
    val aisle: String = "AUTRE",
)

@Serializable
data class PlanDto(val epochDay: Long, val slot: String, val recipeName: String, val servings: Int)

@Serializable
data class ExtraDto(val name: String, val quantity: Double? = null, val unit: String = "NONE", val aisle: String = "AUTRE")

object BackupCodec {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)
    fun decode(text: String): BackupFile = json.decodeFromString(BackupFile.serializer(), text)

    fun toDto(d: RecipeWithDetails) = RecipeDto(
        name = d.recipe.name,
        description = d.recipe.description,
        prepMinutes = d.recipe.prepMinutes,
        cookMinutes = d.recipe.cookMinutes,
        servings = d.recipe.servings,
        isFavorite = d.recipe.isFavorite,
        cuisine = d.recipe.cuisine,
        sourceUrl = d.recipe.sourceUrl,
        remoteId = d.recipe.remoteId,
        ingredients = d.sortedIngredients.map { IngredientDto(it.name, it.quantity, it.unit, it.aisle) },
        steps = d.sortedSteps.map { it.text },
    )

    fun fromDto(dto: RecipeDto): Triple<Recipe, List<Ingredient>, List<Step>> = Triple(
        Recipe(
            name = dto.name, description = dto.description, prepMinutes = dto.prepMinutes,
            cookMinutes = dto.cookMinutes, servings = dto.servings, isFavorite = dto.isFavorite,
            cuisine = dto.cuisine, sourceUrl = dto.sourceUrl, remoteId = dto.remoteId,
        ),
        dto.ingredients.map { Ingredient(name = it.name, quantity = it.quantity, unit = it.unit, aisle = it.aisle) },
        dto.steps.map { Step(text = it) },
    )

    fun toDto(e: ExtraItem) = ExtraDto(e.name, e.quantity, e.unit, e.aisle)
    fun fromDto(dto: ExtraDto) = ExtraItem(name = dto.name, quantity = dto.quantity, unit = dto.unit, aisle = dto.aisle)

    fun fromDto(dto: PlanDto, recipeId: Long) = PlanEntry(epochDay = dto.epochDay, slot = dto.slot, recipeId = recipeId, servings = dto.servings)
}
