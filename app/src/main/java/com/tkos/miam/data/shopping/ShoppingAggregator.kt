package com.tkos.miam.data.shopping

import com.tkos.miam.data.model.Aisle
import com.tkos.miam.data.model.MeasureUnit
import com.tkos.miam.data.model.PlanEntryWithRecipe
import com.tkos.miam.data.model.RecipeWithDetails
import com.tkos.miam.data.model.UnitFamily
import java.text.Normalizer
import java.util.Locale

/** Une ligne agrégée de la liste de courses. */
data class ShoppingLine(
    /** Clé stable : nom normalisé + famille d'unité. Sert à persister l'état coché. */
    val key: String,
    val name: String,
    /** Quantité exprimée dans l'unité de base de la famille (g, ml, pièce…) ; null si « au goût ». */
    val baseQuantity: Double?,
    val family: UnitFamily,
    val aisle: Aisle,
    /** Plats qui ont contribué à cette ligne. */
    val fromRecipes: List<String>,
    val checked: Boolean = false,
) {
    /** « 500 g », « 1,2 kg », « 3 », « 2 c. à soupe »… (sans le nom). */
    val quantityLabel: String get() = formatQuantity(baseQuantity, family)

    /** Texte complet pour partage : « Tomates — 500 g ». */
    val shareLabel: String get() = if (quantityLabel.isBlank()) name else "$name — $quantityLabel"
}

object ShoppingAggregator {

    fun normalizeName(name: String): String =
        Normalizer.normalize(name.trim().lowercase(Locale.FRENCH), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\s+"), " ")
            .removeSuffix("s") // tomates / tomate → même clé

    fun keyOf(name: String, family: UnitFamily): String = "${normalizeName(name)}|${family.name}"

    /**
     * Agrège les ingrédients de toutes les cases du planning, en mettant à l'échelle
     * les quantités selon le nombre de personnes prévu vs celui de la recette.
     */
    fun aggregate(
        entries: List<PlanEntryWithRecipe>,
        details: Map<Long, RecipeWithDetails>,
        checkedKeys: Set<String>,
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
        for (e in entries) {
            val d = details[e.entry.recipeId] ?: continue
            val baseServings = d.recipe.servings.coerceAtLeast(1)
            val ratio = e.entry.servings.toDouble() / baseServings
            for (ing in d.ingredients) {
                val unit = ing.unitEnum
                val key = keyOf(ing.name, unit.family)
                val scaled = ing.quantity?.let { it * ratio * unit.factor }
                val a = acc.getOrPut(key) {
                    Acc(ing.name.trim(), null, false, unit.family, ing.aisleEnum, LinkedHashSet())
                }
                if (scaled != null) {
                    a.qty = (a.qty ?: 0.0) + scaled
                    a.hasQty = true
                }
                if (a.aisle == Aisle.AUTRE && ing.aisleEnum != Aisle.AUTRE) a.aisle = ing.aisleEnum
                a.recipes += d.recipe.name
            }
        }
        return acc.map { (key, a) ->
            ShoppingLine(
                key = key,
                name = a.name.replaceFirstChar { it.titlecase(Locale.FRENCH) },
                baseQuantity = if (a.hasQty) a.qty else null,
                family = a.family,
                aisle = a.aisle,
                fromRecipes = a.recipes.toList(),
                checked = key in checkedKeys,
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
