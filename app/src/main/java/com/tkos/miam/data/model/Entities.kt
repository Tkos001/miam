package com.tkos.miam.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    /** Chemin du fichier image dans le stockage interne (ou null). */
    val imagePath: String? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    /** Nombre de personnes pour lequel les quantités sont données. */
    val servings: Int = 4,
    val isFavorite: Boolean = false,
    val cuisine: String = Cuisine.FRANCAISE.name,
    /** Vrai pour les recettes de base fournies avec l'app. */
    val isBuiltIn: Boolean = false,
    /** Prévu pour l'import depuis le futur site de recettes. */
    val sourceUrl: String? = null,
    val remoteId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val cuisineEnum: Cuisine get() = Cuisine.fromName(cuisine)
}

@Entity(
    tableName = "ingredients",
    foreignKeys = [ForeignKey(
        entity = Recipe::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recipeId")],
)
data class Ingredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long = 0,
    val position: Int = 0,
    val name: String,
    /** null = « au goût » / sans quantité. */
    val quantity: Double? = null,
    val unit: String = MeasureUnit.NONE.name,
    val aisle: String = Aisle.AUTRE.name,
) {
    val unitEnum: MeasureUnit get() = MeasureUnit.fromName(unit)
    val aisleEnum: Aisle get() = Aisle.fromName(aisle)
}

@Entity(
    tableName = "steps",
    foreignKeys = [ForeignKey(
        entity = Recipe::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recipeId")],
)
data class Step(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long = 0,
    val position: Int = 0,
    val text: String,
    /** Illustration optionnelle de l'étape (chemin interne) ; sinon on affiche une icône. */
    val imagePath: String? = null,
)

/** Recette complète avec ses ingrédients et ses étapes. */
data class RecipeWithDetails(
    @Embedded val recipe: Recipe,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<Ingredient>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val steps: List<Step>,
) {
    val sortedIngredients: List<Ingredient> get() = ingredients.sortedBy { it.position }
    val sortedSteps: List<Step> get() = steps.sortedBy { it.position }
}

/** Une case du planning : un plat prévu tel jour, midi ou soir, pour N personnes. */
@Entity(
    tableName = "plan_entries",
    foreignKeys = [ForeignKey(
        entity = Recipe::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recipeId"), Index("epochDay")],
)
data class PlanEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Jour en nombre de jours depuis 1970-01-01 (LocalDate.toEpochDay). */
    val epochDay: Long,
    val slot: String = Slot.SOIR.name,
    val recipeId: Long,
    val servings: Int = 4,
) {
    val slotEnum: Slot get() = Slot.fromName(slot)
}

data class PlanEntryWithRecipe(
    @Embedded val entry: PlanEntry,
    @Relation(parentColumn = "recipeId", entityColumn = "id")
    val recipe: Recipe,
)

/** Article ajouté à la main dans la liste de courses, hors plats (rubrique « Autres »). */
@Entity(tableName = "extra_items")
data class ExtraItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Double? = null,
    val unit: String = MeasureUnit.NONE.name,
    val aisle: String = Aisle.AUTRE.name,
    val checked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val unitEnum: MeasureUnit get() = MeasureUnit.fromName(unit)
    val aisleEnum: Aisle get() = Aisle.fromName(aisle)
}

/** État « coché » d'une ligne agrégée de la liste de courses (clé = nom normalisé + famille d'unité). */
@Entity(tableName = "shopping_checks")
data class ShoppingCheck(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    val checked: Boolean = true,
)
