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
    /** Protéine principale (bœuf, porc, volaille, poisson, végétarien…). */
    val protein: String = Protein.AUTRE.name,
    /** Plat, entrée, accompagnement, dessert, petit-déj… */
    val kind: String = RecipeKind.PLAT.name,
    /** Vrai pour les recettes de base fournies avec l'app. */
    val isBuiltIn: Boolean = false,
    /** Prévu pour l'import depuis le futur site de recettes. */
    val sourceUrl: String? = null,
    val remoteId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val cuisineEnum: Cuisine get() = Cuisine.fromName(cuisine)
    val proteinEnum: Protein get() = Protein.fromName(protein)
    val kindEnum: RecipeKind get() = RecipeKind.fromName(kind)
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

/**
 * Photo d'une recette (galerie). `source` = CATALOG (illustration descendue du catalogue, régénérée à la sync)
 * ou USER (photo prise/choisie sur ce téléphone). Les deux coexistent : ajouter une photo n'enlève pas l'illustration.
 */
@Entity(
    tableName = "recipe_photos",
    foreignKeys = [ForeignKey(
        entity = Recipe::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recipeId"), Index(value = ["recipeId", "path"], unique = true)],
)
data class RecipePhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val path: String,
    val source: String = PhotoSource.USER.name,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isCatalog: Boolean get() = source == PhotoSource.CATALOG.name
}

enum class PhotoSource { CATALOG, USER }

/** Recette complète avec ses ingrédients, ses étapes et ses photos. */
data class RecipeWithDetails(
    @Embedded val recipe: Recipe,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<Ingredient>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val steps: List<Step>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val photos: List<RecipePhoto> = emptyList(),
) {
    val sortedIngredients: List<Ingredient> get() = ingredients.sortedBy { it.position }
    val sortedSteps: List<Step> get() = steps.sortedBy { it.position }
    /** Galerie : couverture d'abord, puis illustrations du catalogue, puis photos perso. */
    val gallery: List<RecipePhoto> get() = photos.sortedWith(
        compareBy<RecipePhoto> { it.path != recipe.imagePath }.thenBy { !it.isCatalog }.thenBy { it.position }.thenBy { it.createdAt }
    )
}

/** Un repas à faire : un plat de la « liste des repas », pour N personnes, coché quand il est mangé / fini. */
@Entity(
    tableName = "meals",
    foreignKeys = [ForeignKey(
        entity = Recipe::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recipeId")],
)
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val servings: Int = 4,
    /** Ordre dans la liste (croissant). */
    val position: Int = 0,
    /** Mangé / fini de préparer : ses ingrédients ont été retirés du stock. */
    val done: Boolean = false,
    val doneAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Repas principal auquel celui-ci est rattaché (entrée, accompagnement, dessert) ; null = repas principal. */
    val parentId: Long? = null,
)

data class MealWithRecipe(
    @Embedded val meal: Meal,
    @Relation(parentColumn = "recipeId", entityColumn = "id")
    val recipe: Recipe,
)

/**
 * Ce qu'il y a à la maison. Clé = nom normalisé + famille d'unité (comme les lignes de courses).
 * `quantity` est en unité de base de la famille (g, ml, pièce…) ; null = « présent, sans quantité »
 * (une bouteille d'huile, un pot de sel) : couvre toute demande tant que l'article est là.
 * `always` = basique de placard : jamais remis sur la liste tant qu'il est en stock, même à 0.
 */
@Entity(tableName = "stock_items", indices = [Index(value = ["key"], unique = true)])
data class StockItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "key") val key: String,
    val name: String,
    val quantity: Double? = null,
    val family: String = UnitFamily.NONE.name,
    val aisle: String = Aisle.AUTRE.name,
    val always: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val familyEnum: UnitFamily get() = UnitFamily.entries.firstOrNull { it.name == family } ?: UnitFamily.NONE
    val aisleEnum: Aisle get() = Aisle.fromName(aisle)
    /** « 5 L », « 800 g », « 6 »… ; vide si sans quantité. */
    val quantityLabel: String get() = com.tkos.miam.data.shopping.ShoppingAggregator.formatQuantity(quantity, familyEnum)
}

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
