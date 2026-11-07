package com.tkos.miam.data.ai

import com.tkos.miam.data.backup.IngredientDto
import com.tkos.miam.data.backup.RecipeDto
import com.tkos.miam.data.model.Aisle
import com.tkos.miam.data.model.Cuisine
import com.tkos.miam.data.model.MeasureUnit
import com.tkos.miam.data.model.Protein
import com.tkos.miam.data.model.RecipeKind
import com.tkos.miam.data.model.StockItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Demande une recette à l'assistant, dans le format exact de l'app (même JSON que le catalogue). */
object RecipeAssistant {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun systemPrompt(): String = """
Tu es un cuisinier qui écrit des recettes pour une application française de planification de repas et de liste de courses.
Réponds UNIQUEMENT par un tableau JSON (1 à 3 recettes), sans texte autour. Chaque recette :
{"name": "…", "description": "une phrase appétissante", "prepMinutes": int, "cookMinutes": int, "servings": 4,
 "cuisine": "FRANCAISE|ITALIENNE|INDIENNE|THAI|JAPONAISE|AMERICAINE|AUTRE",
 "protein": "BOEUF|PORC|VOLAILLE|VEAU|AGNEAU|POISSON|VEGETARIEN|AUTRE",
 "kind": "PLAT|ENTREE|ACCOMPAGNEMENT|DESSERT|PETIT_DEJEUNER|GOUTER|BOISSON",
 "ingredients": [{"name": "nom générique achetable (ex. Crème fraîche, Bœuf haché)", "quantity": nombre ou null, "unit": "G|KG|ML|CL|L|CAC|CAS|PIECE|TRANCHE|GOUSSE|PINCEE|BOTTE|BOITE|SACHET|NONE", "aisle": "FRUITS_LEGUMES|BOUCHERIE|FRAIS|EPICERIE|BOULANGERIE|SURGELES|BOISSONS|AUTRE"}],
 "steps": ["une action complète par étape, avec temps et température", "…"]}
Règles : quantités pour 4 personnes (1 pour petit-déj, goûter, boisson) ; sel/poivre « selon goût » = quantity null + unit NONE ; pas d'eau dans les ingrédients ; 4 à 8 étapes ; français ; apostrophes droites.
""".trimIndent()

    fun userPrompt(request: String, stock: List<StockItem>, useStock: Boolean, existingNames: Collection<String>): String {
        val sb = StringBuilder()
        sb.appendLine("Demande : ${request.ifBlank { "propose un plat du quotidien" }}")
        if (useStock && stock.isNotEmpty()) {
            sb.appendLine("Utilise en priorité ce que j'ai déjà (tu peux ajouter quelques ingrédients courants) :")
            stock.forEach { sb.appendLine("- ${it.name}${if (it.quantityLabel.isNotBlank()) " (${it.quantityLabel})" else ""}") }
        }
        if (existingNames.isNotEmpty()) sb.appendLine("Évite de reproposer ces recettes que j'ai déjà : ${existingNames.take(120).joinToString(", ")}.")
        return sb.toString()
    }

    /** Parse et assainit la réponse : enums inconnus → valeurs par défaut, quantités non numériques → null. */
    fun parse(text: String): List<RecipeDto> {
        val arr = LlmClient.extractJson(text)
        val list: List<RecipeDto> = if (arr.startsWith("[")) json.decodeFromString(ListSerializer(RecipeDto.serializer()), arr)
                                    else listOf(json.decodeFromString(RecipeDto.serializer(), arr))
        return list.filter { it.name.isNotBlank() }.map { r ->
            r.copy(
                remoteId = null, imageUrl = null, imageUrls = emptyList(), sourceUrl = null, isFavorite = false,
                cuisine = Cuisine.fromName(r.cuisine).name,
                protein = Protein.fromName(r.protein).name,
                kind = RecipeKind.fromName(r.kind).name,
                servings = r.servings.coerceIn(1, 30),
                ingredients = r.ingredients.filter { it.name.isNotBlank() }.map { i ->
                    IngredientDto(i.name.trim(), i.quantity, MeasureUnit.fromName(i.unit).name, Aisle.fromName(i.aisle).name)
                },
                steps = r.steps.map { it.trim() }.filter { it.isNotBlank() },
            )
        }
    }
}
