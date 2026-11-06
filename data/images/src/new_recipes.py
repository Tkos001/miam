#!/usr/bin/env python3
"""Ajoute un lot de recettes au catalogue (pour 4 personnes). Usage : python3 data/images/src/new_recipes.py"""
import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
CAT = os.path.join(ROOT, "data", "recipes.json")
IMG = "https://raw.githubusercontent.com/Tkos001/miam/main/data/images/"

FL, BO, FR, EP, BL, SU = "FRUITS_LEGUMES", "BOUCHERIE", "FRAIS", "EPICERIE", "BOULANGERIE", "SURGELES"


def i(name, qty, unit, aisle):
    return {"name": name, "quantity": qty, "unit": unit, "aisle": aisle}


def r(slug, name, desc, prep, cook, ings, steps, cuisine="FRANCAISE", protein="AUTRE"):
    return {
        "remoteId": slug, "name": name, "description": desc,
        "prepMinutes": prep, "cookMinutes": cook, "servings": 4, "cuisine": cuisine, "protein": protein,
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

NEW += [
    r("curry-poulet-coco", "Curry de poulet au lait de coco", "Poulet mijoté dans une sauce coco-curry douce, avec du riz basmati.", 15, 30, [
        i("Blanc de poulet", 600, "G", BO), i("Lait de coco", 40, "CL", EP), i("Oignon", 1, "PIECE", FL), i("Ail", 2, "GOUSSE", FL),
        i("Gingembre frais", 20, "G", FL), i("Curry en poudre", 2, "CAS", EP), i("Tomates concassées", 200, "G", EP),
        i("Riz basmati", 300, "G", EP), i("Huile", 2, "CAS", EP), i("Coriandre", 6, "PIECE", FL), i("Sel", None, "NONE", EP),
    ], [
        "Faire revenir l'oignon émincé, l'ail et le gingembre râpé 3 min dans l'huile. Ajouter le curry, 30 s.",
        "Ajouter le poulet en morceaux, le faire dorer 5 min. Verser tomates et lait de coco, saler, mijoter 20 min à feu doux.",
        "Servir sur le riz cuit, parsemé de coriandre.",
    ], "INDIENNE"),

    r("gratin-pates-jambon", "Gratin de pâtes au jambon", "Coquillettes, jambon, béchamel et gruyère : le plat des soirs pressés.", 10, 35, [
        i("Coquillettes", 400, "G", EP), i("Jambon blanc", 200, "G", FR), i("Beurre", 50, "G", FR), i("Farine", 50, "G", EP),
        i("Lait", 70, "CL", FR), i("Gruyère râpé", 150, "G", FR), i("Noix de muscade", 1, "PINCEE", EP), i("Sel", None, "NONE", EP), i("Poivre", None, "NONE", EP),
    ], [
        "Cuire les coquillettes 2 min de moins que le paquet. Four à 200 °C.",
        "Béchamel : beurre fondu, farine, 1 min ; lait petit à petit en fouettant jusqu'à épaississement ; muscade, sel, poivre, la moitié du gruyère.",
        "Mélanger pâtes, jambon en dés et béchamel, verser dans un plat, couvrir du reste de gruyère, enfourner 20 min.",
    ]),

    r("escalopes-milanaise", "Escalopes de dinde à la milanaise", "Escalopes panées croustillantes, citron et spaghetti à la tomate.", 20, 15, [
        i("Escalope de dinde", 4, "PIECE", BO), i("Œuf", 2, "PIECE", FR), i("Chapelure", 150, "G", EP), i("Farine", 50, "G", EP),
        i("Parmesan râpé", 40, "G", FR), i("Citron", 1, "PIECE", FL), i("Spaghetti", 320, "G", EP), i("Coulis de tomate", 40, "CL", EP),
        i("Huile", 6, "CAS", EP), i("Sel", None, "NONE", EP), i("Poivre", None, "NONE", EP),
    ], [
        "Aplatir les escalopes entre deux feuilles de film. Trois assiettes : farine ; œufs battus ; chapelure mélangée au parmesan.",
        "Passer chaque escalope farine, œuf, chapelure en appuyant bien.",
        "Cuire les spaghetti, réchauffer le coulis salé. Frire les escalopes 3 à 4 min par face dans l'huile chaude. Servir avec un quartier de citron.",
    ], "ITALIENNE"),

    r("wok-boeuf-legumes", "Wok de bœuf aux légumes", "Lamelles de bœuf sautées, poivrons, brocoli, sauce soja-miel, nouilles.", 20, 15, [
        i("Bœuf à fondue ou rumsteck", 500, "G", BO), i("Nouilles chinoises", 250, "G", EP), i("Poivron rouge", 1, "PIECE", FL),
        i("Brocoli", 1, "PIECE", FL), i("Carotte", 2, "PIECE", FL), i("Oignon nouveau", 3, "PIECE", FL), i("Ail", 2, "GOUSSE", FL),
        i("Sauce soja", 4, "CAS", EP), i("Miel", 1, "CAS", EP), i("Maïzena", 1, "CAC", EP), i("Huile", 2, "CAS", EP), i("Graines de sésame", 1, "CAS", EP),
    ], [
        "Mélanger soja, miel, maïzena et 5 cl d'eau. Cuire les nouilles, égoutter.",
        "Wok très chaud : saisir le bœuf en lamelles 2 min, réserver. Sauter carottes en bâtonnets, brocoli en petits bouquets et poivron 5 min avec l'ail.",
        "Remettre le bœuf, verser la sauce, ajouter les nouilles, mélanger 2 min. Oignons nouveaux et sésame au moment de servir.",
    ], "AUTRE"),

    r("flammekueche", "Flammekueche", "Tarte flambée alsacienne : pâte fine, crème-fromage blanc, oignons et lardons.", 20, 15, [
        i("Pâte à pizza", 2, "PIECE", FR), i("Fromage blanc", 200, "G", FR), i("Crème fraîche épaisse", 150, "G", FR),
        i("Oignon", 2, "PIECE", FL), i("Lardons fumés", 200, "G", FR), i("Noix de muscade", 1, "PINCEE", EP), i("Sel", None, "NONE", EP), i("Poivre", None, "NONE", EP),
    ], [
        "Four au maximum (250 °C ou plus), plaque dedans pour préchauffer. Mélanger fromage blanc, crème, muscade, sel, poivre.",
        "Étaler les pâtes très finement, tartiner du mélange, répartir oignons émincés fins et lardons.",
        "Enfourner sur la plaque chaude 10 à 12 min jusqu'à bords dorés. Servir aussitôt.",
    ]),

    r("galettes-sarrasin-complete", "Galettes de sarrasin complètes", "Galettes bretonnes jambon-œuf-fromage, pâte maison.", 15, 25, [
        i("Farine de sarrasin", 250, "G", EP), i("Œuf", 5, "PIECE", FR), i("Eau", 50, "CL", "BOISSONS"), i("Jambon blanc", 4, "TRANCHE", FR),
        i("Emmental râpé", 150, "G", FR), i("Beurre demi-sel", 40, "G", FR), i("Sel", 1, "CAC", EP),
    ], [
        "Pâte : mélanger farine, sel, 1 œuf et l'eau petit à petit jusqu'à une pâte lisse. Laisser reposer 1 h si possible.",
        "Cuire une louche de pâte dans une poêle beurrée très chaude, 1 min par face. Faire 4 galettes.",
        "Galette dans la poêle : emmental, tranche de jambon, casser un œuf au centre, couvrir 3 min, replier les bords en carré.",
    ]),

    r("cordon-bleu-maison-puree", "Cordons bleus maison et purée", "Escalopes de poulet roulées jambon-fromage, panées, purée maison.", 25, 30, [
        i("Escalope de poulet", 4, "PIECE", BO), i("Jambon blanc", 4, "TRANCHE", FR), i("Emmental", 4, "TRANCHE", FR),
        i("Œuf", 2, "PIECE", FR), i("Chapelure", 120, "G", EP), i("Farine", 50, "G", EP), i("Pommes de terre", 1, "KG", FL),
        i("Beurre", 60, "G", FR), i("Lait", 15, "CL", FR), i("Huile", 4, "CAS", EP), i("Sel", None, "NONE", EP), i("Poivre", None, "NONE", EP),
    ], [
        "Purée : pommes de terre pelées cuites 20 min à l'eau salée, écrasées avec beurre et lait chaud, sel, poivre.",
        "Aplatir les escalopes, poser jambon et emmental, rouler ou replier, fermer avec un cure-dent.",
        "Paner farine, œuf, chapelure. Cuire 5 min par face à feu moyen dans l'huile, le fromage doit couler.",
    ]),

    r("poisson-pane-maison-haricots", "Poisson pané maison et haricots verts", "Cabillaud pané au four, haricots verts persillés, citron.", 15, 20, [
        i("Dos de cabillaud", 600, "G", BO), i("Chapelure", 120, "G", EP), i("Œuf", 2, "PIECE", FR), i("Farine", 50, "G", EP),
        i("Haricots verts", 600, "G", FL), i("Ail", 1, "GOUSSE", FL), i("Persil", 6, "PIECE", FL), i("Citron", 1, "PIECE", FL),
        i("Beurre", 30, "G", FR), i("Huile d'olive", 3, "CAS", EP), i("Sel", None, "NONE", EP), i("Poivre", None, "NONE", EP),
    ], [
        "Four à 220 °C. Couper le cabillaud en bâtonnets épais, saler. Paner farine, œuf, chapelure. Poser sur une plaque huilée, arroser d'un filet d'huile.",
        "Enfourner 15 min en retournant à mi-cuisson.",
        "Haricots : 8 min à l'eau bouillante salée, égoutter, sauter 2 min au beurre avec ail et persil hachés. Servir avec le citron.",
    ]),

    r("risotto-champignons", "Risotto aux champignons", "Riz arborio crémeux, champignons de Paris, parmesan.", 10, 30, [
        i("Riz arborio", 320, "G", EP), i("Champignons de Paris", 400, "G", FL), i("Oignon", 1, "PIECE", FL), i("Ail", 1, "GOUSSE", FL),
        i("Vin blanc sec", 10, "CL", "BOISSONS"), i("Bouillon de volaille", 1, "L", EP), i("Parmesan râpé", 80, "G", FR),
        i("Beurre", 50, "G", FR), i("Huile d'olive", 2, "CAS", EP), i("Persil", 4, "PIECE", FL), i("Sel", None, "NONE", EP), i("Poivre", None, "NONE", EP),
    ], [
        "Sauter les champignons émincés dans l'huile à feu vif 6 min, saler, réserver. Garder le bouillon chaud.",
        "Fondre l'oignon et l'ail hachés dans la moitié du beurre, ajouter le riz, nacrer 2 min, déglacer au vin.",
        "Ajouter le bouillon louche par louche en remuant, 18 min. Hors du feu : champignons, parmesan, reste de beurre, poivre, persil. Couvrir 2 min, servir.",
    ], "ITALIENNE"),

    r("tomates-farcies", "Tomates farcies", "Grosses tomates garnies de chair à saucisse et bœuf, cuites sur un lit de riz.", 25, 60, [
        i("Grosse tomate", 8, "PIECE", FL), i("Chair à saucisse", 400, "G", BO), i("Bœuf haché", 200, "G", BO), i("Oignon", 1, "PIECE", FL),
        i("Ail", 2, "GOUSSE", FL), i("Pain de mie", 2, "TRANCHE", BL), i("Lait", 10, "CL", FR), i("Œuf", 1, "PIECE", FR),
        i("Persil", 8, "PIECE", FL), i("Riz", 200, "G", EP), i("Huile d'olive", 2, "CAS", EP), i("Sel", None, "NONE", EP), i("Poivre", None, "NONE", EP),
    ], [
        "Four à 180 °C. Décalotter les tomates, les vider à la cuillère (garder la pulpe), saler l'intérieur, retourner 10 min.",
        "Farce : pain trempé dans le lait essoré, chair à saucisse, bœuf, oignon et ail hachés, persil, œuf, sel, poivre.",
        "Riz cru et pulpe hachée au fond d'un plat huilé avec 20 cl d'eau. Poser les tomates garnies, remettre les chapeaux. Enfourner 50 à 60 min.",
    ]),
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
