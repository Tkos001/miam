package com.tkos.miam.data.seed

import com.tkos.miam.data.model.Aisle
import com.tkos.miam.data.model.Aisle.BOUCHERIE
import com.tkos.miam.data.model.Aisle.BOULANGERIE
import com.tkos.miam.data.model.Aisle.EPICERIE
import com.tkos.miam.data.model.Aisle.FRAIS
import com.tkos.miam.data.model.Aisle.FRUITS_LEGUMES
import com.tkos.miam.data.model.Cuisine
import com.tkos.miam.data.model.Protein
import com.tkos.miam.data.model.Ingredient
import com.tkos.miam.data.model.MeasureUnit
import com.tkos.miam.data.model.MeasureUnit.BOITE
import com.tkos.miam.data.model.MeasureUnit.CAC
import com.tkos.miam.data.model.MeasureUnit.CAS
import com.tkos.miam.data.model.MeasureUnit.CL
import com.tkos.miam.data.model.MeasureUnit.G
import com.tkos.miam.data.model.MeasureUnit.GOUSSE
import com.tkos.miam.data.model.MeasureUnit.KG
import com.tkos.miam.data.model.MeasureUnit.NONE
import com.tkos.miam.data.model.MeasureUnit.PIECE
import com.tkos.miam.data.model.MeasureUnit.SACHET
import com.tkos.miam.data.model.MeasureUnit.TRANCHE
import com.tkos.miam.data.model.Recipe
import com.tkos.miam.data.model.Step

data class SeedRecipe(
    val recipe: Recipe,
    val ingredients: List<Ingredient>,
    val steps: List<Step>,
)

/** 15 plats français du quotidien, quantités pour 4 personnes. */
object SeedRecipes {

    private fun ing(name: String, qty: Double?, unit: MeasureUnit, aisle: Aisle) =
        Ingredient(name = name, quantity = qty, unit = unit.name, aisle = aisle.name)

    private fun r(
        name: String,
        description: String,
        prep: Int,
        cook: Int,
        ingredients: List<Ingredient>,
        steps: List<String>,
        protein: Protein = Protein.AUTRE,
    ) = SeedRecipe(
        recipe = Recipe(
            name = name, description = description, prepMinutes = prep, cookMinutes = cook,
            servings = 4, cuisine = Cuisine.FRANCAISE.name, protein = protein.name, isBuiltIn = true,
        ),
        ingredients = ingredients,
        steps = steps.map { Step(text = it) },
    )

    val all: List<SeedRecipe> = listOf(
        r("Spaghetti bolognaise", "Le classique du mardi soir.", 15, 40,
            listOf(
                ing("Spaghetti", 400.0, G, EPICERIE),
                ing("Bœuf haché", 500.0, G, BOUCHERIE),
                ing("Oignon", 1.0, PIECE, FRUITS_LEGUMES),
                ing("Carotte", 1.0, PIECE, FRUITS_LEGUMES),
                ing("Ail", 2.0, GOUSSE, FRUITS_LEGUMES),
                ing("Tomates concassées", 1.0, BOITE, EPICERIE),
                ing("Concentré de tomate", 1.0, CAS, EPICERIE),
                ing("Huile d'olive", 2.0, CAS, EPICERIE),
                ing("Parmesan râpé", 60.0, G, FRAIS),
                ing("Sel", null, NONE, EPICERIE),
                ing("Poivre", null, NONE, EPICERIE),
            ),
            listOf(
                "Émincer l'oignon, l'ail et la carotte en petits dés.",
                "Faire revenir les légumes dans l'huile d'olive 5 min à feu moyen.",
                "Ajouter la viande hachée, la faire dorer en l'émiettant.",
                "Ajouter les tomates concassées et le concentré, saler, poivrer. Laisser mijoter 30 min à feu doux.",
                "Cuire les spaghetti dans un grand volume d'eau salée selon le paquet.",
                "Servir les pâtes nappées de sauce avec le parmesan.",
            ), protein = Protein.BOEUF),
        r("Gratin dauphinois", "Pommes de terre fondantes à la crème.", 20, 75,
            listOf(
                ing("Pommes de terre", 1.2, KG, FRUITS_LEGUMES),
                ing("Crème fraîche épaisse", 40.0, CL, FRAIS),
                ing("Lait", 25.0, CL, FRAIS),
                ing("Ail", 1.0, GOUSSE, FRUITS_LEGUMES),
                ing("Beurre", 20.0, G, FRAIS),
                ing("Noix de muscade", null, NONE, EPICERIE),
                ing("Sel", null, NONE, EPICERIE),
                ing("Poivre", null, NONE, EPICERIE),
            ),
            listOf(
                "Préchauffer le four à 160 °C. Éplucher et couper les pommes de terre en fines rondelles (sans les laver).",
                "Frotter le plat avec la gousse d'ail puis le beurrer.",
                "Mélanger crème, lait, sel, poivre et muscade.",
                "Disposer les pommes de terre en couches, verser le mélange crème-lait.",
                "Enfourner 1 h 15 jusqu'à ce que le dessus soit doré et les pommes de terre fondantes.",
            ), protein = Protein.VEGETARIEN),
        r("Quiche lorraine", "Lardons, œufs, crème : la vraie.", 15, 35,
            listOf(
                ing("Pâte brisée", 1.0, PIECE, FRAIS),
                ing("Lardons fumés", 200.0, G, BOUCHERIE),
                ing("Œufs", 3.0, PIECE, FRAIS),
                ing("Crème fraîche épaisse", 20.0, CL, FRAIS),
                ing("Lait", 10.0, CL, FRAIS),
                ing("Noix de muscade", null, NONE, EPICERIE),
                ing("Poivre", null, NONE, EPICERIE),
            ),
            listOf(
                "Préchauffer le four à 180 °C. Étaler la pâte dans un moule et piquer le fond.",
                "Faire revenir les lardons à sec 5 min, les égoutter.",
                "Battre les œufs avec la crème, le lait, le poivre et la muscade.",
                "Répartir les lardons sur la pâte, verser l'appareil.",
                "Enfourner 35 min jusqu'à ce que la quiche soit dorée et prise.",
            ), protein = Protein.PORC),
        r("Poulet rôti et pommes de terre", "Le dimanche midi.", 15, 80,
            listOf(
                ing("Poulet fermier", 1.0, PIECE, BOUCHERIE),
                ing("Pommes de terre", 1.0, KG, FRUITS_LEGUMES),
                ing("Ail", 4.0, GOUSSE, FRUITS_LEGUMES),
                ing("Thym", null, NONE, EPICERIE),
                ing("Beurre", 30.0, G, FRAIS),
                ing("Huile d'olive", 2.0, CAS, EPICERIE),
                ing("Sel", null, NONE, EPICERIE),
                ing("Poivre", null, NONE, EPICERIE),
            ),
            listOf(
                "Préchauffer le four à 200 °C. Saler et poivrer le poulet, glisser le thym et 2 gousses d'ail dans la cavité.",
                "Badigeonner de beurre mou et placer dans un plat.",
                "Éplucher les pommes de terre, les couper en quartiers, les mélanger avec l'huile, le reste d'ail et du sel.",
                "Disposer les pommes de terre autour du poulet.",
                "Enfourner 1 h 20 en arrosant toutes les 20 min. Laisser reposer 10 min avant de découper.",
            ), protein = Protein.VOLAILLE),
        r("Bœuf bourguignon", "Mijoté au vin rouge, encore meilleur réchauffé.", 30, 180,
            listOf(
                ing("Bœuf à braiser (paleron, gîte)", 1.2, KG, BOUCHERIE),
                ing("Lardons fumés", 150.0, G, BOUCHERIE),
                ing("Carotte", 3.0, PIECE, FRUITS_LEGUMES),
                ing("Oignon", 2.0, PIECE, FRUITS_LEGUMES),
                ing("Champignons de Paris", 250.0, G, FRUITS_LEGUMES),
                ing("Vin rouge", 75.0, CL, Aisle.BOISSONS),
                ing("Bouillon de bœuf", 1.0, PIECE, EPICERIE),
                ing("Farine", 2.0, CAS, EPICERIE),
                ing("Bouquet garni", 1.0, PIECE, EPICERIE),
                ing("Beurre", 30.0, G, FRAIS),
                ing("Sel", null, NONE, EPICERIE),
                ing("Poivre", null, NONE, EPICERIE),
            ),
            listOf(
                "Couper la viande en gros cubes, les faire dorer dans le beurre en plusieurs fois. Réserver.",
                "Faire revenir les lardons, les oignons émincés et les carottes en rondelles.",
                "Remettre la viande, saupoudrer de farine, mélanger 2 min.",
                "Verser le vin et le bouillon à hauteur, ajouter le bouquet garni, saler, poivrer.",
                "Couvrir et laisser mijoter 2 h 30 à feu très doux.",
                "Ajouter les champignons émincés et poursuivre 30 min.",
            ), protein = Protein.BOEUF),
        r("Blanquette de veau", "Douce, crémeuse, avec du riz.", 20, 90,
            listOf(
                ing("Épaule de veau", 1.0, KG, BOUCHERIE),
                ing("Carotte", 3.0, PIECE, FRUITS_LEGUMES),
                ing("Oignon", 1.0, PIECE, FRUITS_LEGUMES),
                ing("Poireau", 1.0, PIECE, FRUITS_LEGUMES),
                ing("Champignons de Paris", 250.0, G, FRUITS_LEGUMES),
                ing("Crème fraîche épaisse", 20.0, CL, FRAIS),
                ing("Jaune d'œuf", 1.0, PIECE, FRAIS),
                ing("Citron", 0.5, PIECE, FRUITS_LEGUMES),
                ing("Beurre", 40.0, G, FRAIS),
                ing("Farine", 40.0, G, EPICERIE),
                ing("Bouquet garni", 1.0, PIECE, EPICERIE),
                ing("Riz", 300.0, G, EPICERIE),
            ),
            listOf(
                "Mettre la viande en morceaux dans une cocotte, couvrir d'eau froide, porter à ébullition et écumer.",
                "Ajouter carottes, oignon, poireau et bouquet garni. Saler. Laisser frémir 1 h 15.",
                "Faire sauter les champignons au beurre.",
                "Préparer un roux avec beurre et farine, mouiller avec 50 cl de bouillon de cuisson, laisser épaissir.",
                "Hors du feu, ajouter crème, jaune d'œuf et jus de citron. Napper la viande et les légumes.",
                "Servir avec le riz.",
            ), protein = Protein.VEAU),
        r("Croque-monsieur", "Rapide et tout le monde aime.", 10, 10,
            listOf(
                ing("Pain de mie", 8.0, TRANCHE, BOULANGERIE),
                ing("Jambon blanc", 4.0, TRANCHE, BOUCHERIE),
                ing("Emmental râpé", 150.0, G, FRAIS),
                ing("Beurre", 40.0, G, FRAIS),
                ing("Crème fraîche épaisse", 2.0, CAS, FRAIS),
                ing("Noix de muscade", null, NONE, EPICERIE),
            ),
            listOf(
                "Beurrer les tranches de pain sur une face.",
                "Mélanger la crème avec une pincée de muscade, en tartiner l'intérieur.",
                "Garnir de jambon et d'emmental, refermer.",
                "Cuire à la poêle ou au four à 200 °C 10 min jusqu'à ce que le fromage fonde et que le pain dore.",
            ), protein = Protein.PORC),
        r("Hachis parmentier", "Purée maison gratinée sur viande hachée.", 25, 40,
            listOf(
                ing("Bœuf haché", 600.0, G, BOUCHERIE),
                ing("Pommes de terre", 1.2, KG, FRUITS_LEGUMES),
                ing("Oignon", 1.0, PIECE, FRUITS_LEGUMES),
                ing("Ail", 1.0, GOUSSE, FRUITS_LEGUMES),
                ing("Lait", 20.0, CL, FRAIS),
                ing("Beurre", 60.0, G, FRAIS),
                ing("Gruyère râpé", 80.0, G, FRAIS),
                ing("Persil", null, NONE, FRUITS_LEGUMES),
                ing("Sel", null, NONE, EPICERIE),
                ing("Poivre", null, NONE, EPICERIE),
            ),
            listOf(
                "Cuire les pommes de terre épluchées 25 min dans l'eau salée, les écraser avec le beurre et le lait chaud.",
                "Faire revenir l'oignon et l'ail émincés, ajouter la viande, saler, poivrer, cuire 10 min. Ajouter le persil.",
                "Étaler la viande dans un plat, couvrir de purée, parsemer de gruyère.",
                "Gratiner 20 min à 200 °C.",
            ), protein = Protein.BOEUF),
        r("Ratatouille", "Légumes d'été mijotés, chaud ou froid.", 20, 45,
            listOf(
                ing("Aubergine", 2.0, PIECE, FRUITS_LEGUMES),
                ing("Courgette", 2.0, PIECE, FRUITS_LEGUMES),
                ing("Poivron", 2.0, PIECE, FRUITS_LEGUMES),
                ing("Tomate", 4.0, PIECE, FRUITS_LEGUMES),
                ing("Oignon", 2.0, PIECE, FRUITS_LEGUMES),
                ing("Ail", 3.0, GOUSSE, FRUITS_LEGUMES),
                ing("Huile d'olive", 5.0, CAS, EPICERIE),
                ing("Herbes de Provence", 1.0, CAC, EPICERIE),
                ing("Sel", null, NONE, EPICERIE),
                ing("Poivre", null, NONE, EPICERIE),
            ),
            listOf(
                "Couper tous les légumes en dés.",
                "Faire revenir séparément aubergines, courgettes et poivrons dans l'huile d'olive, réserver.",
                "Dans la même cocotte, faire fondre oignons et ail, ajouter les tomates et les herbes.",
                "Remettre tous les légumes, saler, poivrer, laisser mijoter 30 min à couvert.",
            ), protein = Protein.VEGETARIEN),
        r("Tartiflette", "Reblochon, lardons, pommes de terre.", 20, 40,
            listOf(
                ing("Pommes de terre", 1.0, KG, FRUITS_LEGUMES),
                ing("Reblochon", 1.0, PIECE, FRAIS),
                ing("Lardons fumés", 200.0, G, BOUCHERIE),
                ing("Oignon", 2.0, PIECE, FRUITS_LEGUMES),
                ing("Crème fraîche épaisse", 15.0, CL, FRAIS),
                ing("Vin blanc sec", 10.0, CL, Aisle.BOISSONS),
                ing("Poivre", null, NONE, EPICERIE),
            ),
            listOf(
                "Cuire les pommes de terre en robe des champs 20 min, les éplucher et les couper en rondelles.",
                "Faire revenir oignons émincés et lardons, déglacer au vin blanc.",
                "Mélanger pommes de terre, lardons-oignons et crème dans un plat. Poivrer.",
                "Poser le reblochon coupé en deux dans l'épaisseur, croûte vers le haut.",
                "Enfourner 20 min à 200 °C.",
            ), protein = Protein.PORC),
        r("Soupe à l'oignon gratinée", "Réconfort d'hiver.", 15, 50,
            listOf(
                ing("Oignon", 6.0, PIECE, FRUITS_LEGUMES),
                ing("Beurre", 50.0, G, FRAIS),
                ing("Farine", 1.0, CAS, EPICERIE),
                ing("Bouillon de volaille", 1.5, MeasureUnit.L, EPICERIE),
                ing("Vin blanc sec", 10.0, CL, Aisle.BOISSONS),
                ing("Pain de campagne", 4.0, TRANCHE, BOULANGERIE),
                ing("Gruyère râpé", 150.0, G, FRAIS),
                ing("Sel", null, NONE, EPICERIE),
                ing("Poivre", null, NONE, EPICERIE),
            ),
            listOf(
                "Émincer les oignons, les faire fondre au beurre 25 min à feu doux jusqu'à coloration.",
                "Saupoudrer de farine, mouiller avec le vin puis le bouillon. Saler, poivrer, laisser mijoter 20 min.",
                "Répartir dans des bols allant au four, poser une tranche de pain, couvrir de gruyère.",
                "Gratiner sous le gril 5 min.",
            ), protein = Protein.VEGETARIEN),
        r("Steak haché, frites maison, salade", "Simple et efficace.", 15, 30,
            listOf(
                ing("Steak haché", 4.0, PIECE, BOUCHERIE),
                ing("Pommes de terre", 1.0, KG, FRUITS_LEGUMES),
                ing("Salade verte", 1.0, PIECE, FRUITS_LEGUMES),
                ing("Huile de friture", 1.0, MeasureUnit.L, EPICERIE),
                ing("Huile d'olive", 2.0, CAS, EPICERIE),
                ing("Vinaigre", 1.0, CAS, EPICERIE),
                ing("Moutarde", 1.0, CAC, EPICERIE),
                ing("Sel", null, NONE, EPICERIE),
                ing("Poivre", null, NONE, EPICERIE),
            ),
            listOf(
                "Éplucher et couper les pommes de terre en bâtonnets, les rincer et bien les sécher.",
                "Première cuisson des frites 6 min à 150 °C, égoutter. Deuxième cuisson 3 min à 180 °C, saler.",
                "Préparer la vinaigrette : moutarde, vinaigre, sel, poivre, huile d'olive.",
                "Cuire les steaks à la poêle bien chaude 2 à 3 min par face.",
            ), protein = Protein.BOEUF),
        r("Omelette aux fines herbes", "Dîner en 10 minutes.", 5, 8,
            listOf(
                ing("Œufs", 8.0, PIECE, FRAIS),
                ing("Beurre", 20.0, G, FRAIS),
                ing("Ciboulette", null, NONE, FRUITS_LEGUMES),
                ing("Persil", null, NONE, FRUITS_LEGUMES),
                ing("Lait", 2.0, CAS, FRAIS),
                ing("Sel", null, NONE, EPICERIE),
                ing("Poivre", null, NONE, EPICERIE),
            ),
            listOf(
                "Battre les œufs avec le lait, les herbes ciselées, sel et poivre.",
                "Faire fondre le beurre dans une poêle chaude, verser les œufs.",
                "Ramener les bords vers le centre avec une spatule, plier quand le dessus est encore baveux.",
            ), protein = Protein.VEGETARIEN),
        r("Pot-au-feu", "Le grand plat familial, pour plusieurs repas.", 20, 180,
            listOf(
                ing("Bœuf pour pot-au-feu (gîte, plat de côte, macreuse)", 1.5, KG, BOUCHERIE),
                ing("Os à moelle", 2.0, PIECE, BOUCHERIE),
                ing("Carotte", 6.0, PIECE, FRUITS_LEGUMES),
                ing("Poireau", 3.0, PIECE, FRUITS_LEGUMES),
                ing("Navet", 3.0, PIECE, FRUITS_LEGUMES),
                ing("Pommes de terre", 800.0, G, FRUITS_LEGUMES),
                ing("Oignon", 1.0, PIECE, FRUITS_LEGUMES),
                ing("Clou de girofle", 2.0, PIECE, EPICERIE),
                ing("Bouquet garni", 1.0, PIECE, EPICERIE),
                ing("Gros sel", null, NONE, EPICERIE),
                ing("Cornichons", 1.0, BOITE, EPICERIE),
                ing("Moutarde", 1.0, PIECE, EPICERIE),
            ),
            listOf(
                "Mettre la viande dans un grand faitout, couvrir d'eau froide, porter à ébullition et écumer.",
                "Ajouter l'oignon piqué des clous de girofle, le bouquet garni et le gros sel. Frémir 2 h.",
                "Ajouter carottes, poireaux, navets, cuire 45 min. Ajouter pommes de terre et os à moelle, cuire 30 min.",
                "Servir le bouillon à part, puis viande et légumes avec moutarde et cornichons.",
            ), protein = Protein.BOEUF),
        r("Crêpes", "Sucrées ou salées, la pâte de base.", 10, 20,
            listOf(
                ing("Farine", 250.0, G, EPICERIE),
                ing("Œufs", 3.0, PIECE, FRAIS),
                ing("Lait", 50.0, CL, FRAIS),
                ing("Beurre", 50.0, G, FRAIS),
                ing("Sucre", 1.0, CAS, EPICERIE),
                ing("Sucre vanillé", 1.0, SACHET, EPICERIE),
                ing("Sel", 1.0, MeasureUnit.PINCEE, EPICERIE),
            ),
            listOf(
                "Mélanger farine, sucre, sucre vanillé et sel. Creuser un puits, ajouter les œufs.",
                "Verser le lait petit à petit en fouettant, puis le beurre fondu. Laisser reposer 30 min.",
                "Cuire dans une poêle chaude légèrement beurrée, 1 min par face.",
            ), protein = Protein.VEGETARIEN),
    )
}
