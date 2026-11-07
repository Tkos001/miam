package com.tkos.miam.data.shopping

import com.tkos.miam.data.model.Aisle
import com.tkos.miam.data.model.MeasureUnit
import com.tkos.miam.data.model.MealWithRecipe
import com.tkos.miam.data.model.RecipeWithDetails
import com.tkos.miam.data.model.UnitFamily
import java.text.Normalizer
import java.util.Locale

/** Une ligne agrégée de la liste de courses. */
data class ShoppingLine(
    /** Clé stable : nom normalisé + famille d'unité. Sert à persister l'état coché et à retrouver le stock. */
    val key: String,
    val name: String,
    /** Besoin total des repas, en unité de base de la famille (g, ml, pièce…) ; null si « au goût ». */
    val baseQuantity: Double?,
    val family: UnitFamily,
    val aisle: Aisle,
    /** Plats qui ont contribué à cette ligne. */
    val fromRecipes: List<String>,
    /** Mis dans le caddie (coché à la main). */
    val checked: Boolean = false,
    /** Quantité déjà en stock (unité de base), null si rien ou stock sans quantité. */
    val stockQuantity: Double? = null,
    /** Entièrement couvert par le stock : rien à acheter, affiché coché et grisé. */
    val inStock: Boolean = false,
) {
    /** Ce qu'il reste à acheter une fois le stock déduit (null si « au goût »). */
    val toBuy: Double? get() = baseQuantity?.let { b -> (b - (stockQuantity ?: 0.0)).coerceAtLeast(0.0) }

    /** « 500 g », « 1,2 kg », « 3 », « 2 c. à soupe »… : la quantité à acheter (sans le nom). */
    val quantityLabel: String get() = ShoppingAggregator.formatQuantity(if (inStock) baseQuantity else toBuy, family)

    /** « dont 300 g en stock » quand le stock couvre une partie seulement. */
    val stockLabel: String? get() = stockQuantity?.takeIf { it > 0 && !inStock }?.let { "${ShoppingAggregator.formatQuantity(it, family)} en stock" }

    /** Coché à l'écran : acheté ou déjà en stock. */
    val isDone: Boolean get() = checked || inStock

    /** Texte complet pour partage : « Tomates — 500 g ». */
    val shareLabel: String get() = if (quantityLabel.isBlank()) name else "$name — $quantityLabel"
}

/** Un article du stock tel que l'agrégateur en a besoin. */
data class StockLevel(val quantity: Double?, val always: Boolean)

object ShoppingAggregator {

    fun normalizeName(name: String): String =
        Normalizer.normalize(name.trim().lowercase(Locale.FRENCH), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\s+"), " ")
            .removeSuffix("s") // tomates / tomate → même clé

    fun keyOf(name: String, family: UnitFamily): String = "${normalizeName(name)}|${family.name}"

    /** Familles « sans quantité utile » une fois achetées : une bouteille, un pot — on note la présence, pas la dose. */
    fun isPresenceFamily(family: UnitFamily): Boolean =
        family == UnitFamily.SPOON_TEA || family == UnitFamily.SPOON_TABLE || family == UnitFamily.PINCH || family == UnitFamily.NONE

    /** Besoin brut d'un repas (nom, quantité en unité de base, famille, rayon), à l'échelle du nombre de personnes. */
    fun needsOf(d: RecipeWithDetails, servings: Int): List<Need> {
        val ratio = servings.toDouble() / d.recipe.servings.coerceAtLeast(1)
        return d.ingredients.map { ing ->
            val unit = ing.unitEnum
            Need(keyOf(ing.name, unit.family), ing.name.trim(), ing.quantity?.let { it * ratio * unit.factor }, unit.family, ing.aisleEnum)
        }
    }

    data class Need(val key: String, val name: String, val baseQuantity: Double?, val family: UnitFamily, val aisle: Aisle)

    /**
     * Agrège les ingrédients des repas à faire, puis déduit le stock.
     * `stock` : clé → niveau. Un article `always`, ou présent sans quantité, couvre toute demande ;
     * sinon la ligne est couverte si le stock ≥ le besoin, partielle au-delà.
     */
    fun aggregate(
        meals: List<MealWithRecipe>,
        details: Map<Long, RecipeWithDetails>,
        checkedKeys: Set<String>,
        stock: Map<String, StockLevel> = emptyMap(),
    ): List<ShoppingLine> {
        data class Acc(
            var name: String,
            var qty: Double?,
            var hasQty: Boolean,
            val family: UnitFamily,
            var aisle: Aisle,
            val recipes: LinkedHashSet<String>,
        )

        val acc = LinkedHashMap<String, Acc>()
        for (m in meals) {
            if (m.meal.done) continue
            val d = details[m.meal.recipeId] ?: continue
            for (n in needsOf(d, m.meal.servings)) {
                val a = acc.getOrPut(n.key) { Acc(n.name, null, false, n.family, n.aisle, LinkedHashSet()) }
                if (n.baseQuantity != null) {
                    a.qty = (a.qty ?: 0.0) + n.baseQuantity
                    a.hasQty = true
                }
                if (a.aisle == Aisle.AUTRE && n.aisle != Aisle.AUTRE) a.aisle = n.aisle
                a.recipes += d.recipe.name
            }
        }
        return acc.map { (key, a) ->
            val need = if (a.hasQty) a.qty else null
            val level = stock[key]
            val covered = level != null && (level.always || level.quantity == null || need == null || level.quantity + 1e-9 >= need)
            ShoppingLine(
                key = key,
                name = a.name.replaceFirstChar { it.titlecase(Locale.FRENCH) },
                baseQuantity = need,
                family = a.family,
                aisle = a.aisle,
                fromRecipes = a.recipes.toList(),
                checked = !covered && key in checkedKeys,
                stockQuantity = level?.quantity,
                inStock = covered,
            )
        }.sortedWith(compareBy({ it.aisle.ordinal }, { it.name.lowercase(Locale.FRENCH) }))
    }

    /** Formate une quantité en unité de base vers l'unité la plus lisible. */
    fun formatQuantity(base: Double?, family: UnitFamily): String {
        if (base == null) return ""
        return when (family) {
            UnitFamily.WEIGHT -> if (base >= 1000) "${fmt(base / 1000)} kg" else "${fmt(base)} g"
            UnitFamily.VOLUME -> when {
                base >= 1000 -> "${fmt(base / 1000)} L"
                base >= 100 && base % 10 == 0.0 -> "${fmt(base / 10)} cl"
                else -> "${fmt(base)} ml"
            }
            UnitFamily.PIECE, UnitFamily.NONE -> fmt(base)
            else -> {
                val unit = MeasureUnit.entries.first { it.family == family }
                "${fmt(base)} ${if (base > 1) unit.plural else unit.label}"
            }
        }
    }

    /** Formate une quantité d'ingrédient dans son unité d'origine (fiche recette). */
    fun formatIngredient(quantity: Double?, unit: MeasureUnit): String {
        if (quantity == null) return ""
        return when (unit) {
            MeasureUnit.NONE, MeasureUnit.PIECE -> fmt(quantity)
            else -> "${fmt(quantity)} ${if (quantity > 1) unit.plural else unit.label}"
        }
    }

    fun fmt(v: Double): String {
        val rounded = Math.round(v * 100) / 100.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString()
        else String.format(Locale.FRENCH, "%.2f", rounded).trimEnd('0').trimEnd(',')
    }
}
