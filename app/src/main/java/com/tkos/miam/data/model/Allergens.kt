package com.tkos.miam.data.model

import java.text.Normalizer
import java.util.Locale

/**
 * Les 14 allergènes à déclaration obligatoire (règlement UE 1169/2011), détectés automatiquement
 * d'après le nom des ingrédients. Indicatif : un ingrédient transformé peut en contenir d'autres
 * (bouillon cube, charcuterie, sauces industrielles…) ; la fiche le rappelle.
 */
enum class Allergen(val label: String, val emoji: String, private val keywords: List<String>) {
    GLUTEN("Gluten", "🌾", listOf(
        "farine", "ble", "pate", "pates", "spaghetti", "tagliatelle", "penne", "lasagne", "macaroni", "coquillette", "nouille",
        "pain", "chapelure", "biscotte", "semoule", "boulgour", "couscous", "orge", "seigle", "epeautre", "avoine",
        "brioche", "croissant", "feuilletee", "brisee", "sablee", "gnocchi", "biscuit", "speculoos", "seitan", "biere", "sauce soja",
        "tortilla de ble", "wrap", "pita", "naan", "galette de ble", "crepe", "gaufre", "pancake", "blini",
    )),
    CRUSTACEANS("Crustacés", "🦐", listOf("crevette", "gambas", "langoustine", "crabe", "homard", "langouste", "ecrevisse", "tourteau")),
    EGGS("Œufs", "🥚", listOf("oeuf", "jaune d'oeuf", "blanc d'oeuf", "mayonnaise", "meringue")),
    FISH("Poisson", "🐟", listOf(
        "poisson", "saumon", "cabillaud", "colin", "lieu", "merlu", "thon", "sardine", "maquereau", "truite", "dorade", "bar ",
        "sole", "anchois", "hareng", "haddock", "morue", "julienne", "lotte", "surimi", "nuoc", "sauce poisson",
    )),
    PEANUTS("Arachides", "🥜", listOf("arachide", "cacahuete", "cacahouete", "beurre de cacahuete")),
    SOY("Soja", "🫘", listOf("soja", "tofu", "tempeh", "edamame", "miso")),
    MILK("Lait", "🥛", listOf(
        "lait", "creme", "beurre", "fromage", "yaourt", "yogourt", "comte", "gruyere", "emmental", "parmesan", "mozzarella",
        "mascarpone", "ricotta", "chevre", "reblochon", "raclette", "cheddar", "feta", "roquefort", "bleu", "camembert",
        "brie", "munster", "cantal", "tomme", "pecorino", "gorgonzola", "boursin", "kiri", "babybel", "saint-nectaire",
        "petit-suisse", "faisselle", "lactose", "bechamel",
    )),
    NUTS("Fruits à coque", "🌰", listOf(
        "noix", "noisette", "amande", "pistache", "cajou", "pecan", "macadamia", "pignon", "praline", "nutella", "frangipane",
    )),
    CELERY("Céleri", "🥬", listOf("celeri", "celeri-rave", "celeri branche")),
    MUSTARD("Moutarde", "🟡", listOf("moutarde")),
    SESAME("Sésame", "⚪", listOf("sesame", "tahini", "tahin", "houmous")),
    SULPHITES("Sulfites", "🍷", listOf("vin ", "vin blanc", "vin rouge", "vinaigre", "cidre", "champagne", "porto", "cognac", "madere", "raisin sec")),
    LUPIN("Lupin", "🌱", listOf("lupin")),
    MOLLUSCS("Mollusques", "🐚", listOf("moule", "huitre", "saint-jacques", "coquille", "calamar", "calmar", "encornet", "poulpe", "seiche", "escargot", "bulot", "palourde", "coque")),
    ;

    companion object {
        private val EXCLUSIONS = mapOf(
            // faux positifs fréquents : (mot-clé) → ingrédients à ignorer
            "noix" to listOf("noix de muscade", "noix de coco", "noix de saint-jacques", "noix de veau", "noix de joue"),
            "lait" to listOf("lait de coco", "lait d'amande", "lait d'avoine", "lait de soja", "lait de riz"),
            "creme" to listOf("creme de coco", "creme de soja", "creme d'amande", "creme de riz"),
            "beurre" to listOf("beurre de cacahuete", "beurre d'amande"),
            "pate" to listOf("pate de curry", "pate de piment", "pate d'amande", "pate de sesame", "pate de tomate"),
            "coque" to listOf("fruits a coque"),
            "farine" to listOf("farine de riz", "farine de mais", "farine de sarrasin", "farine de pois chiche", "farine de chataigne"),
        )

        private fun norm(s: String): String =
            Normalizer.normalize(s.trim().lowercase(Locale.FRENCH).replace("œ", "oe").replace("æ", "ae"), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace('’', '\'')
                .replace(Regex("\\s+"), " ")

        /** Allergènes détectés → ingrédients qui les déclenchent (noms tels qu'écrits dans la recette). */
        fun detect(ingredientNames: List<String>): Map<Allergen, List<String>> {
            val out = linkedMapOf<Allergen, MutableList<String>>()
            for (raw in ingredientNames) {
                val n = " " + norm(raw) + " "
                for (a in entries) {
                    val hit = a.keywords.any { kw ->
                        val k = norm(kw)
                        val present = if (k.endsWith(" ")) n.contains(" $k") else Regex("(^|[^a-z])" + Regex.escape(k) + "(s?)($|[^a-z])").containsMatchIn(n)
                        present && EXCLUSIONS[k].orEmpty().none { ex -> n.contains(ex) }
                    }
                    if (hit) out.getOrPut(a) { mutableListOf() }.add(raw)
                }
            }
            return out
        }
    }
}
