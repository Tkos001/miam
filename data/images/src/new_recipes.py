#!/usr/bin/env python3
"""Ajoute un lot de recettes au catalogue (pour 4 personnes). Usage : python3 data/images/src/new_recipes.py"""
import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
CAT = os.path.join(ROOT, "data", "recipes.json")
IMG = "https://raw.githubusercontent.com/Tkos001/miam/main/data/images/"

FL, BO, FR, EP, BL, SU = "FRUITS_LEGUMES", "BOUCHERIE", "FRAIS", "EPICERIE", "BOULANGERIE", "SURGELES"


def i(name, qty, unit, aisle):
    return {"name": name, "quantity": qty, "unit": unit, "aisle": aisle}


def r(slug, name, desc, prep, cook, ings, steps, cuisine="FRANCAISE"):
    return {
        "remoteId": slug, "name": name, "description": desc,
        "prepMinutes": prep, "cookMinutes": cook, "servings": 4, "cuisine": cuisine,
        "imageUrl": IMG + slug + ".jpg", "ingredients": ings, "steps": steps,
    }


NEW = [
    r("lasagnes-boeuf", "Lasagnes au bœuf", "Bolognaise maison, béchamel et plaques de lasagnes, gratinées au four.", 30, 60, [
        i("Plaques de lasagnes", 250, "G", EP), i("Bœuf haché", 600, "G", BO), i("Oignon", 1, "PIECE", FL),
        i("Ail", 2, "GOUSSE", FL), i("Carotte", 1, "PIECE", FL), i("Tomates concassées", 800, "G", EP),
        i("Concentré de tomate", 2, "CAS", EP), i("Beurre", 60, "G", FR), i("Farine", 60, "G", EP),
        i("Lait", 80, "CL", FR), i("Gruyère râpé", 150, "G", FR), i("Parmesan râpé", 50, "G", FR),
        i("Huile d'olive", 2, "CAS", EP), i("Noix de muscade", 1, "PINCEE", EP), i("Sel", None, "NONE", EP), i("Poivre", None, "NONE", EP),
    ], [
        "Bolognaise : faire revenir oignon, ail et carotte émincés dans l'huile 5 min. Ajouter le bœuf, cuire 8 min en émiettant. Ajouter tomates et concentré, saler, poivrer, laisser mijoter 20 min.",
        "Béchamel : faire fondre le beurre, ajouter la farine, cuire 1 min. Verser le lait petit à petit en fouettant jusqu'à épaississement. Muscade, sel, poivre.",
        "Four à 180 °C. Dans un plat beurré : une louche de béchamel, plaques, bolognaise, béchamel, gruyère. Répéter jusqu'en haut, finir par béchamel, gruyère et parmesan.",
        "Enfourner 40 min jusqu'à ce que le dessus soit bien doré. Laisser reposer 10 min avant de couper.",
    ], "ITALIENNE"),

    r("tourte-pomme-de-terre-saucisse-comte", "Tourte pommes de terre, saucisse fumée et comté", "Deux pâtes feuilletées, pommes de terre fondantes, saucisse de Morteau et comté.", 25, 55, [
        i("Pâte feuilletée", 2, "PIECE", FR), i("Pommes de terre", 800, "G", FL), i("Saucisse de Morteau", 1, "PIECE", BO),
        i("Oignon", 1, "PIECE", FL), i("Comté râpé", 150, "G", FR), i("Crème fraîche épaisse", 20, "CL", FR),
        i("Œuf", 1, "PIECE", FR), i("Beurre", 20, "G", FR), i("Noix de muscade", 1, "PINCEE", EP), i("Sel", None, "NONE", EP), i("Poivre", None, "NONE", EP),
    ], [
        "Cuire la saucisse 25 min dans l'eau frémissante, la couper en rondelles. Cuire les pommes de terre pelées 15 min à l'eau salée, les couper en rondelles épaisses.",
        "Faire fondre l'oignon émincé dans le beurre. Mélanger la crème avec muscade, sel, poivre.",
        "Four à 200 °C. Foncer un moule à tarte avec la première pâte. Alterner pommes de terre, oignons, saucisse, comté, un peu de crème. Terminer par crème et comté.",
        "Couvrir avec la seconde pâte, souder les bords, faire une cheminée au centre, dorer au jaune d'œuf. Enfourner 35 à 40 min jusqu'à belle coloration.",
    ]),

    r("pates-carbonara", "Pâtes à la carbonara", "Guanciale ou lardons, jaunes d'œufs et pecorino : crémeux sans crème.", 10, 15, [
        i("Spaghetti", 400, "G", EP), i("Lardons fumés", 200, "G", FR), i("Jaune d'œuf", 4, "PIECE", FR),
        i("Œuf", 1, "PIECE", FR), i("Pecorino râpé", 80, "G", FR), i("Parmesan râpé", 40, "G", FR), i("Poivre", None, "NONE", EP), i("Sel", None, "NONE", EP),
    ], [
        "Cuire les pâtes al dente dans une grande casserole d'eau salée. Garder une louche d'eau de cuisson.",
        "Pendant ce temps, faire dorer les lardons à sec dans une poêle, feu moyen, 5 min. Couper le feu.",
        "Fouetter jaunes, œuf entier, pecorino, parmesan et beaucoup de poivre dans un saladier.",
        "Égoutter les pâtes, les verser dans la poêle hors du feu avec les lardons, ajouter le mélange aux œufs et un peu d'eau de cuisson, mélanger vite jusqu'à une sauce nappante. Servir aussitôt.",
    ], "ITALIENNE"),

    r("paupiettes-porc-veau", "Paupiettes de porc et de veau à la crème", "Paupiettes dorées puis mijotées avec champignons, échalotes et crème.", 15, 40, [
        i("Paupiettes de veau", 2, "PIECE", BO), i("Paupiettes de porc", 2, "PIECE", BO), i("Champignons de Paris", 250, "G", FL),
        i("Échalote", 2, "PIECE", FL), i("Vin blanc sec", 10, "CL", "BOISSONS"), i("Crème fraîche épaisse", 20, "CL", FR),
        i("Moutarde", 1, "CAS", EP), i("Beurre", 30, "G", FR), i("Huile", 1, "CAS", EP), i("Sel", None, "NONE", EP), i("Poivre", None, "NONE", EP),
    ], [
        "Faire dorer les paupiettes sur toutes les faces dans beurre et huile, 8 min. Réserver.",
        "Dans la même cocotte, faire revenir échalotes émincées et champignons en lamelles 5 min. Déglacer au vin blanc, laisser réduire de moitié.",
        "Remettre les paupiettes, ajouter 10 cl d'eau, couvrir et laisser mijoter 25 min à feu doux en les retournant à mi-cuisson.",
        "Sortir les paupiettes, ajouter crème et moutarde dans la sauce, faire épaissir 3 min, rectifier l'assaisonnement. Servir avec riz, purée ou tagliatelles.",
    ]),

    r("poulet-basquaise", "Poulet basquaise", "Cuisses de poulet mijotées aux poivrons, tomates et piment d'Espelette.", 15, 50, [
        i("Cuisses de poulet", 4, "PIECE", BO), i("Poivron rouge", 2, "PIECE", FL), i("Poivron vert", 1, "PIECE", FL),
        i("Oignon", 2, "PIECE", FL), i("Ail", 3, "GOUSSE", FL), i("Tomates concassées", 400, "G", EP),
        i("Piment d'Espelette", 1, "CAC", EP), i("Thym", 2, "PINCEE", EP), i("Huile d'olive", 3, "CAS", EP), i("Sel", None, "NONE", EP), i("Poivre", None, "NONE", EP),
    ], [
        "Faire dorer les cuisses salées et poivrées dans l'huile, 10 min. Réserver.",
        "Faire revenir oignons et poivrons en lanières 10 min, ajouter l'ail, les tomates, le thym et le piment.",
        "Remettre le poulet, couvrir, laisser mijoter 30 min à feu doux. Découvrir les 10 dernières minutes pour épaissir la sauce. Servir avec du riz.",
    ]),

    r("saumon-four-legumes", "Pavés de saumon au four et légumes rôtis", "Saumon, courgettes, tomates cerises et pommes de terre, tout sur une plaque.", 15, 35, [
        i("Pavés de saumon", 4, "PIECE", BO), i("Pommes de terre", 600, "G", FL), i("Courgette", 2, "PIECE", FL),
        i("Tomates cerises", 250, "G", FL), i("Citron", 1, "PIECE", FL), i("Ail", 2, "GOUSSE", FL),
        i("Huile d'olive", 4, "CAS", EP), i("Herbes de Provence", 1, "CAC", EP), i("Sel", None, "NONE", EP), i("Poivre", None, "NONE", EP),
    ], [
        "Four à 200 °C. Pommes de terre en quartiers avec 2 c. à s. d'huile, sel, herbes : 20 min sur la plaque.",
        "Ajouter courgettes en rondelles, tomates cerises, ail écrasé, puis poser les pavés de saumon salés, poivrés, arrosés du reste d'huile et de jus de citron.",
        "Remettre 15 min : le saumon doit rester nacré au centre. Servir avec des quartiers de citron.",
    ]),

    r("chili-con-carne", "Chili con carne", "Bœuf haché, haricots rouges, tomate et épices, à servir avec du riz.", 15, 45, [
        i("Bœuf haché", 600, "G", BO), i("Haricots rouges en boîte", 2, "BOITE", EP), i("Tomates concassées", 800, "G", EP),
        i("Oignon", 2, "PIECE", FL), i("Ail", 3, "GOUSSE", FL), i("Poivron rouge", 1, "PIECE", FL),
        i("Cumin", 2, "CAC", EP), i("Paprika", 1, "CAS", EP), i("Piment en poudre", 1, "CAC", EP), i("Concentré de tomate", 1, "CAS", EP),
        i("Riz", 300, "G", EP), i("Huile", 2, "CAS", EP), i("Sel", None, "NONE", EP),
    ], [
        "Faire revenir oignons, ail et poivron en dés 5 min dans l'huile. Ajouter le bœuf, cuire 8 min en émiettant.",
        "Ajouter épices et concentré, mélanger 1 min, puis les tomates. Saler, mijoter 25 min à découvert.",
        "Ajouter les haricots égouttés et rincés, cuire encore 10 min. Servir sur du riz, avec crème ou cheddar râpé selon l'envie.",
    ], "AMERICAINE"),

    r("gratin-courgettes-chevre", "Gratin de courgettes au chèvre", "Courgettes, crème, chèvre frais et menthe, léger et rapide.", 15, 35, [
        i("Courgette", 4, "PIECE", FL), i("Chèvre frais", 200, "G", FR), i("Crème fraîche épaisse", 20, "CL", FR),
        i("Œuf", 3, "PIECE", FR), i("Gruyère râpé", 80, "G", FR), i("Ail", 1, "GOUSSE", FL), i("Menthe", 6, "PIECE", FL),
        i("Huile d'olive", 1, "CAS", EP), i("Sel", None, "NONE", EP), i("Poivre", None, "NONE", EP),
    ], [
        "Four à 180 °C. Courgettes en rondelles fines, sautées 8 min dans l'huile avec l'ail pour les dessécher.",
        "Fouetter œufs, crème, chèvre écrasé, menthe ciselée, sel, poivre. Mélanger aux courgettes.",
        "Verser dans un plat, couvrir de gruyère, enfourner 25 à 30 min jusqu'à ce que ce soit pris et doré.",
    ]),

    r("riz-cantonais", "Riz cantonais", "Riz sauté aux petits pois, jambon, œufs et sauce soja. Parfait pour un reste de riz.", 10, 15, [
        i("Riz", 300, "G", EP), i("Jambon blanc", 150, "G", FR), i("Petits pois surgelés", 200, "G", SU),
        i("Œuf", 3, "PIECE", FR), i("Oignon nouveau", 3, "PIECE", FL), i("Sauce soja", 3, "CAS", EP),
        i("Huile", 2, "CAS", EP), i("Huile de sésame", 1, "CAC", EP), i("Poivre", None, "NONE", EP),
    ], [
        "Cuire le riz la veille ou le laisser bien refroidir. Cuire les petits pois 3 min à l'eau bouillante.",
        "Dans un wok très chaud, brouiller les œufs battus dans 1 c. à s. d'huile, réserver.",
        "Faire sauter le riz dans le reste d'huile 3 min en remuant, ajouter jambon en dés, petits pois, œufs, oignons nouveaux émincés, soja et sésame. Sauter 2 min. Poivrer.",
    ], "AUTRE"),

    r("boulettes-boeuf-sauce-tomate", "Boulettes de bœuf à la sauce tomate", "Boulettes moelleuses mijotées dans une sauce tomate à l'ail et au basilic.", 20, 35, [
        i("Bœuf haché", 600, "G", BO), i("Pain de mie", 2, "TRANCHE", BL), i("Lait", 10, "CL", FR), i("Œuf", 1, "PIECE", FR),
        i("Oignon", 1, "PIECE", FL), i("Ail", 2, "GOUSSE", FL), i("Parmesan râpé", 40, "G", FR),
        i("Tomates concassées", 800, "G", EP), i("Basilic", 8, "PIECE", FL), i("Huile d'olive", 3, "CAS", EP),
        i("Spaghetti", 400, "G", EP), i("Sel", None, "NONE", EP), i("Poivre", None, "NONE", EP),
    ], [
        "Tremper le pain dans le lait, l'essorer. Mélanger avec le bœuf, l'œuf, la moitié de l'oignon haché, 1 gousse d'ail, le parmesan, sel, poivre. Former 16 boulettes.",
        "Les dorer dans 2 c. à s. d'huile 6 min en les roulant. Réserver.",
        "Dans la même sauteuse, faire fondre le reste d'oignon et d'ail, ajouter les tomates, saler. Remettre les boulettes, couvrir, mijoter 20 min. Ajouter le basilic.",
        "Servir avec les spaghetti cuits al dente.",
    ], "ITALIENNE"),
]

d = json.load(open(CAT))
have = {x["remoteId"] for x in d["recipes"]}
added = [x for x in NEW if x["remoteId"] not in have]
d["recipes"].extend(added)
d["version"] += 1
d["updatedAt"] = "2026-11-06"
json.dump(d, open(CAT, "w"), ensure_ascii=False, indent=2)
open(CAT, "a").write("\n")
print("version", d["version"], "ajoutées", [x["remoteId"] for x in added])
