package com.tkos.miam.data.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tkos.miam.data.model.Aisle
import com.tkos.miam.data.model.MeasureUnit
import com.tkos.miam.data.shopping.ShoppingAggregator
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** OCR sur le téléphone (ML Kit, hors ligne). Retourne les lignes de texte dans l'ordre de lecture. */
object Ocr {
    suspend fun readLines(context: Context, uri: Uri): List<String> {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val text = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        // ML Kit regroupe en blocs ; on remet les lignes dans l'ordre vertical pour suivre le ticket.
        return text.textBlocks.flatMap { b -> b.lines.map { it } }
            .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
    }
}

/** Une ligne de ticket interprétée, à relire avant d'entrer en stock. */
@Serializable
data class ReceiptItem(
    /** Texte brut du ticket. */
    val raw: String,
    /** Nom proposé pour le stock (ingrédient connu si reconnu, sinon libellé nettoyé). */
    val name: String,
    val quantity: Double? = null,
    val unit: String = "NONE",
    val aisle: String = "AUTRE",
    /** Vrai si le nom correspond à un ingrédient déjà connu de l'app. */
    val matched: Boolean = false,
    /** Coché par défaut dans l'écran de relecture. */
    val selected: Boolean = true,
) {
    val unitEnum: MeasureUnit get() = MeasureUnit.fromName(unit)
    val aisleEnum: Aisle get() = Aisle.fromName(aisle)
}

/**
 * Parseur sans IA : quantités (6X1L, 500G, 1KG, 20CL, x2), lignes à ignorer (total, TVA, CB…),
 * rapprochement avec les noms d'ingrédients connus. Volontairement prudent : mieux vaut une ligne
 * décochée qu'un faux article en stock.
 */
object ReceiptParser {
    private val SKIP = listOf(
        "total", "sous-total", "sous total", "tva", "t.v.a", "ht", "ttc", "carte", "cb ", "especes", "espèces", "rendu", "monnaie",
        "remise", "reduction", "réduction", "fidelite", "fidélité", "points", "merci", "bienvenue", "caisse", "ticket", "client",
        "tel", "tél", "siret", "www", "http", "montant", "paiement", "payé", "paye", "avoir", "bon d'achat", "cumul", "economie", "économie",
        "article", "nombre", "date", "heure", "magasin", "ouvert", "horaires", "code", "ean", "n°", "no ", "facture", "ref",
    )
    private val QTY = Regex("(\\d+(?:[.,]\\d+)?)\\s*(KG|G|GR|L|CL|ML)\\b", RegexOption.IGNORE_CASE)
    private val PACK = Regex("(\\d+)\\s*[xX×]\\s*(\\d+(?:[.,]\\d+)?)\\s*(KG|G|GR|L|CL|ML)?\\b")
    private val COUNT = Regex("^(\\d+)\\s*[xX×]\\s+|\\s(\\d+)\\s*[xX×]\\s*$")
    private val PRICE = Regex("\\d+[.,]\\d{2}\\s*(€|EUR)?\\s*[A-Z]?\\s*$")
    private val PRICE_ANY = Regex("\\d+[.,]\\d{2}")
    private val WEIGHED = Regex("(\\d+[.,]\\d{2,3})\\s*kg\\s*[xX×]\\s*\\d+[.,]\\d{2}", RegexOption.IGNORE_CASE)

    fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.FRENCH).replace("œ", "oe"), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    /**
     * @param knownNames noms d'ingrédients connus (recettes + stock) ; le nom retenu sera celui-là quand il matche.
     * @param knownAisles rayon connu par nom normalisé d'ingrédient.
     */
    fun parse(lines: List<String>, knownNames: Collection<String>, knownAisles: Map<String, Aisle> = emptyMap()): List<ReceiptItem> {
        val known = knownNames.map { it to norm(it) }.filter { it.second.length >= 3 }
        val out = mutableListOf<ReceiptItem>()
        var i = 0
        while (i < lines.size) {
            var line = lines[i]
            // Ligne de pesée « 0,842 kg x 2,99 €/kg » : s'applique au libellé précédent.
            val weighed = WEIGHED.find(line)
            if (weighed != null && out.isNotEmpty()) {
                val kg = weighed.groupValues[1].replace(',', '.').toDoubleOrNull()
                val last = out.removeAt(out.lastIndex)
                out += last.copy(quantity = kg, unit = "KG")
                i++; continue
            }
            val n = norm(line)
            val hasPrice = PRICE.containsMatchIn(line)
            val skip = SKIP.any { n.startsWith(norm(it)) || n == norm(it) } || n.length < 3 || n.all { it.isDigit() || it == ' ' }
            if (skip) { i++; continue }
            // Un libellé seul suivi d'une ligne prix : on les fusionne.
            if (!hasPrice && i + 1 < lines.size && PRICE.matches(lines[i + 1].trim()) ) { line = "$line ${lines[i + 1]}"; i++ }
            if (!hasPrice && !PRICE_ANY.containsMatchIn(line) && !QTY.containsMatchIn(line) && !PACK.containsMatchIn(line)) {
                // Pas de prix, pas de quantité : en-tête, adresse… sauf si le libellé matche un ingrédient connu.
                if (known.none { (_, k) -> n.contains(k) }) { i++; continue }
            }
            var label = line.replace(PRICE, "").trim()
            var qty: Double? = null
            var unit = MeasureUnit.NONE
            PACK.find(label)?.let { m ->
                val count = m.groupValues[1].toDouble()
                val each = m.groupValues[2].replace(',', '.').toDouble()
                val u = unitOf(m.groupValues[3])
                if (u != null) { qty = count * each; unit = u } else qty = count
                label = label.replace(m.value, " ")
            } ?: QTY.find(label)?.let { m ->
                qty = m.groupValues[1].replace(',', '.').toDoubleOrNull(); unit = unitOf(m.groupValues[2]) ?: MeasureUnit.NONE
                label = label.replace(m.value, " ")
            } ?: COUNT.find(label)?.let { m ->
                qty = (m.groupValues[1].ifBlank { m.groupValues[2] }).toDoubleOrNull(); unit = MeasureUnit.PIECE
                label = label.replace(m.value, " ")
            }
            label = label.replace(PRICE_ANY, " ").replace(Regex("[*€]"), " ").replace(Regex("\\s+"), " ").trim(' ', '-', '.')
            if (label.length < 3) { i++; continue }
            val ln = norm(label)
            // Rapprochement : ingrédient connu dont tous les mots (≥ 3 lettres) apparaissent dans le libellé, le plus long d'abord.
            val match = known.filter { (_, k) -> k.split(' ').filter { it.length >= 3 }.let { ws -> ws.isNotEmpty() && ws.all { w -> ln.contains(w) } } }
                .maxByOrNull { it.second.length }
            val name = match?.first ?: label.lowercase(Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) }
            val aisle = match?.let { knownAisles[ShoppingAggregator.normalizeName(it.first)] } ?: Aisle.AUTRE
            out += ReceiptItem(
                raw = line, name = name, quantity = qty, unit = unit.name, aisle = aisle.name,
                matched = match != null, selected = match != null,
            )
            i++
        }
        return out
    }

    private fun unitOf(s: String?): MeasureUnit? = when (s?.uppercase(Locale.ROOT)) {
        "KG" -> MeasureUnit.KG
        "G", "GR" -> MeasureUnit.G
        "L" -> MeasureUnit.L
        "CL" -> MeasureUnit.CL
        "ML" -> MeasureUnit.ML
        else -> null
    }

    // ---- Variante IA : le modèle structure le texte, l'app ne fait que valider les enums. ----

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun llmSystemPrompt(knownNames: Collection<String>): String = """
Tu lis des tickets de caisse de supermarchés français (texte OCR, abréviations d'enseigne) et tu en extrais les produits alimentaires achetés.
Réponds UNIQUEMENT par un tableau JSON, sans commentaire, d'objets : {"raw": "ligne du ticket", "name": "nom lisible du produit", "quantity": nombre ou null, "unit": "G|KG|ML|CL|L|PIECE|NONE", "aisle": "FRUITS_LEGUMES|BOUCHERIE|FRAIS|EPICERIE|BOULANGERIE|SURGELES|BOISSONS|AUTRE"}.
Règles : ignore totaux, TVA, remises, paiements, fidélité, en-têtes. "quantity" = quantité totale achetée (ex. "6X1L" → 6, "L" ; "2 x BOEUF HACHE 500G" → 1000, "G" ; une pesée "0,842 kg" → 0.842, "KG" ; un article unitaire → 1, "PIECE").
Quand le produit correspond à l'un de ces noms connus, utilise EXACTEMENT ce nom : ${knownNames.take(300).joinToString(", ")}.
Sinon, un nom court en français courant (« Lait demi-écrémé », « Bœuf haché 15 % »).
""".trimIndent()

    fun parseLlm(text: String): List<ReceiptItem> {
        val arr = LlmClient.extractJson(text)
        val items = json.decodeFromString(ListSerializer(ReceiptItem.serializer()), arr)
        return items.map { it.copy(unit = MeasureUnit.fromName(it.unit).name, aisle = Aisle.fromName(it.aisle).name, matched = true, selected = true) }
            .filter { it.name.isNotBlank() }
    }
}
