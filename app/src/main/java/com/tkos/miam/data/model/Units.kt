package com.tkos.miam.data.model

/**
 * Unités de mesure. `family` regroupe les unités convertibles entre elles,
 * `factor` donne la conversion vers l'unité de base de la famille (g ou ml).
 */
enum class MeasureUnit(
    val label: String,
    val plural: String,
    val family: UnitFamily,
    val factor: Double,
) {
    G("g", "g", UnitFamily.WEIGHT, 1.0),
    KG("kg", "kg", UnitFamily.WEIGHT, 1000.0),
    ML("ml", "ml", UnitFamily.VOLUME, 1.0),
    CL("cl", "cl", UnitFamily.VOLUME, 10.0),
    L("L", "L", UnitFamily.VOLUME, 1000.0),
    CAC("c. à café", "c. à café", UnitFamily.SPOON_TEA, 1.0),
    CAS("c. à soupe", "c. à soupe", UnitFamily.SPOON_TABLE, 1.0),
    PIECE("pièce", "pièces", UnitFamily.PIECE, 1.0),
    TRANCHE("tranche", "tranches", UnitFamily.SLICE, 1.0),
    GOUSSE("gousse", "gousses", UnitFamily.CLOVE, 1.0),
    PINCEE("pincée", "pincées", UnitFamily.PINCH, 1.0),
    BOTTE("botte", "bottes", UnitFamily.BUNCH, 1.0),
    BOITE("boîte", "boîtes", UnitFamily.CAN, 1.0),
    SACHET("sachet", "sachets", UnitFamily.BAG, 1.0),
    NONE("", "", UnitFamily.NONE, 1.0);

    /** Unités « comptables » : on affiche « 2 × Steak haché » plutôt que « 2 pièces de steak haché ». */
    val isCount: Boolean get() = this == PIECE || this == NONE

    companion object {
        fun fromName(name: String?): MeasureUnit = entries.firstOrNull { it.name == name } ?: NONE
    }
}

enum class UnitFamily { WEIGHT, VOLUME, SPOON_TEA, SPOON_TABLE, PIECE, SLICE, CLOVE, PINCH, BUNCH, CAN, BAG, NONE }

/** Rayons du magasin, dans l'ordre d'affichage de la liste de courses. */
enum class Aisle(val label: String, val emoji: String) {
    FRUITS_LEGUMES("Fruits & légumes", "🥦"),
    BOUCHERIE("Boucherie / poisson", "🥩"),
    FRAIS("Frais & crèmerie", "🧀"),
    EPICERIE("Épicerie", "🥫"),
    BOULANGERIE("Boulangerie", "🥖"),
    SURGELES("Surgelés", "🧊"),
    BOISSONS("Boissons", "🧃"),
    AUTRE("Autre", "🛒");

    companion object {
        fun fromName(name: String?): Aisle = entries.firstOrNull { it.name == name } ?: AUTRE
    }
}

/** Style de cuisine d'un plat (extensible : indien, thaï, japonais, américain…). */
enum class Cuisine(val label: String, val emoji: String) {
    FRANCAISE("Française", "🇫🇷"),
    ITALIENNE("Italienne", "🇮🇹"),
    INDIENNE("Indienne", "🇮🇳"),
    THAI("Thaï", "🇹🇭"),
    JAPONAISE("Japonaise", "🇯🇵"),
    AMERICAINE("Américaine", "🇺🇸"),
    AUTRE("Autre", "🌍");

    companion object {
        fun fromName(name: String?): Cuisine = entries.firstOrNull { it.name == name } ?: AUTRE
    }
}

/** Protéine / base du plat, pour filtrer (« ce soir, du poisson »). */
enum class Protein(val label: String, val emoji: String) {
    BOEUF("Bœuf", "🐄"),
    PORC("Porc", "🐖"),
    VOLAILLE("Volaille", "🐔"),
    VEAU("Veau", "🐮"),
    AGNEAU("Agneau", "🐑"),
    POISSON("Poisson", "🐟"),
    VEGETARIEN("Végétarien", "🥬"),
    AUTRE("Autre", "🍽️");

    companion object {
        fun fromName(name: String?): Protein = entries.firstOrNull { it.name == name } ?: AUTRE
    }
}

