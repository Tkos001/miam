package com.tkos.miam.data.backup

import com.tkos.miam.data.model.ExtraItem
import com.tkos.miam.data.model.Ingredient
import com.tkos.miam.data.model.PlanEntry
import com.tkos.miam.data.model.Recipe
import com.tkos.miam.data.model.RecipeWithDetails
import com.tkos.miam.data.model.Step
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral

/** Quantité : écrite `4` et non `4.0` quand elle est entière (format du catalogue git), lue comme un Double. */
@OptIn(ExperimentalSerializationApi::class)
object QuantitySerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Quantity", PrimitiveKind.DOUBLE)
    override fun serialize(encoder: Encoder, value: Double) {
        val je = encoder as? JsonEncoder
        if (je != null && value == Math.rint(value) && Math.abs(value) < 1e15) je.encodeJsonElement(JsonUnquotedLiteral(value.toLong().toString()))
        else encoder.encodeDouble(value)
    }
    override fun deserialize(decoder: Decoder): Double = decoder.decodeDouble()
}

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
@OptIn(ExperimentalSerializationApi::class)
data class RecipeDto(
    // Ordre des champs = ordre d'écriture dans data/recipes.json (voir data/check.py, forme canonique).
    val remoteId: String? = null,
    val name: String,
    val description: String = "",
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val servings: Int = 4,
    val cuisine: String = "FRANCAISE",
    val protein: String = "AUTRE",
    /** Image distante (catalogue) : téléchargée à la sync, jamais utilisée pour l'export local. */
    val imageUrl: String? = null,
    /** Photos supplémentaires (galerie), publiées depuis l'app. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val imageUrls: List<String> = emptyList(),
    val ingredients: List<IngredientDto> = emptyList(),
    val steps: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val isFavorite: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val sourceUrl: String? = null,
)

@Serializable
data class IngredientDto(
    val name: String,
    @Serializable(with = QuantitySerializer::class) val quantity: Double? = null,
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
        protein = d.recipe.protein,
        sourceUrl = d.recipe.sourceUrl,
        remoteId = d.recipe.remoteId,
        ingredients = d.sortedIngredients.map { IngredientDto(it.name, it.quantity, it.unit, it.aisle) },
        steps = d.sortedSteps.map { it.text },
    )

    fun fromDto(dto: RecipeDto): Triple<Recipe, List<Ingredient>, List<Step>> = Triple(
        Recipe(
            name = dto.name, description = dto.description, prepMinutes = dto.prepMinutes,
            cookMinutes = dto.cookMinutes, servings = dto.servings, isFavorite = dto.isFavorite,
            cuisine = dto.cuisine, protein = dto.protein, sourceUrl = dto.sourceUrl, remoteId = dto.remoteId,
        ),
        dto.ingredients.map { Ingredient(name = it.name, quantity = it.quantity, unit = it.unit, aisle = it.aisle) },
        dto.steps.map { Step(text = it) },
    )

    fun toDto(e: ExtraItem) = ExtraDto(e.name, e.quantity, e.unit, e.aisle)
    fun fromDto(dto: ExtraDto) = ExtraItem(name = dto.name, quantity = dto.quantity, unit = dto.unit, aisle = dto.aisle)

    fun fromDto(dto: PlanDto, recipeId: Long) = PlanEntry(epochDay = dto.epochDay, slot = dto.slot, recipeId = recipeId, servings = dto.servings)
}
