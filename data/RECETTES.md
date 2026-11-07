# Ajouter une recette au catalogue Miam

Procédure complète, à suivre dans l'ordre, par un humain ou un assistant IA (Claude, ChatGPT…).
Le catalogue est `data/recipes.json`. L'application le télécharge au lancement : **une recette poussée
ici apparaît sur les téléphones sans rebuild**, à condition que `version` ait été incrémentée.

Comptage : l'app affiche **15 plats de base compilés dans l'APK** (`app/src/main/java/com/tkos/miam/data/seed/SeedRecipes.kt`,
absents de ce fichier) **+ les recettes du catalogue**. 32 recettes ici = 47 dans l'app. C'est normal.

## Checklist (résumé)

1. Écrire la recette dans `data/recipes.json` (4 personnes, `remoteId` slug unique).
2. Dessiner l'illustration : une fonction dans `data/images/src/build.py` + entrée dans `DISHES`.
3. `python3 data/images/src/build.py <remoteId>` → produit `data/images/<remoteId>.jpg`.
4. Renseigner `imageUrl` dans la recette.
5. Incrémenter `version`, mettre `updatedAt` à la date du jour.
6. `python3 data/check.py` → doit afficher `OK`.
7. Commit (`recipes.json`, `build.py`, le `.svg` et le `.jpg`) et push sur `main`.

Une recette sans étape 2 s'affiche **sans image** dans l'app. Ne pas la sauter.

### Sans environnement local (édition via l'API GitHub, ChatGPT, éditeur web)

Les étapes 3, 4 et 6 sont faites par le workflow **Catalogue** (`.github/workflows/catalogue.yml`) à chaque
push sur `main` touchant `data/recipes.json` ou `data/images/src/` :

1. il lance `build.py --missing` : génère le JPG de tout `remoteId` présent dans `DISHES` sans image ;
2. il lance `check.py --fix` : renseigne `imageUrl`, incrémente `version` si nécessaire, valide le tout ;
3. il commite le résultat sur `main` (« Catalogue vN : illustrations générées (CI) »).

Donc, sans pouvoir exécuter de script, il suffit de : écrire la recette dans `recipes.json` (sans `imageUrl`
ou avec, au choix), ajouter la fonction de dessin dans `build.py` et l'enregistrer dans `DISHES`, incrémenter
`version`, pousser. Puis regarder l'onglet *Actions* → run « Catalogue » : vert = publié ; rouge = le résumé
du run liste les erreurs de `check.py` à corriger (enum inconnu, `remoteId` en double, fonction absente…).
Le workflow ne corrige jamais le contenu d'une recette, seulement l'image et la version.

## 1. La recette

Ajouter un objet à la fin du tableau `recipes` :

```json
{
  "remoteId": "paupiettes-porc-ail-creme",
  "name": "Paupiettes de porc à l’ail et à la crème",
  "description": "Une phrase appétissante qui décrit le plat.",
  "prepMinutes": 10,
  "cookMinutes": 40,
  "servings": 4,
  "cuisine": "FRANCAISE",
  "protein": "PORC",
  "imageUrl": "https://raw.githubusercontent.com/Tkos001/miam/main/data/images/paupiettes-porc-ail-creme.jpg",
  "ingredients": [
    { "name": "Paupiettes de porc", "quantity": 4, "unit": "PIECE", "aisle": "BOUCHERIE" },
    { "name": "Ail", "quantity": 6, "unit": "GOUSSE", "aisle": "FRUITS_LEGUMES" },
    { "name": "Crème fraîche", "quantity": 20, "unit": "CL", "aisle": "FRAIS" },
    { "name": "Sel", "quantity": null, "unit": "NONE", "aisle": "EPICERIE" }
  ],
  "steps": [
    "Une étape = une action complète, avec temps et température quand il y en a.",
    "Quatre à six étapes suffisent."
  ]
}
```

Règles :

- `remoteId` : slug en minuscules sans accent (`a-z0-9-`), **unique**, jamais modifié ensuite (c'est la clé
  de fusion côté app). Vérifier qu'un plat proche n'existe pas déjà (`grep -i paupiettes data/recipes.json`).
- `servings` : **4**. Les quantités sont mises à l'échelle par l'app selon le planning.
- Quantités en nombres (pas de chaînes), `null` + `"NONE"` pour sel/poivre/« selon goût ».
- Les ingrédients alimentent la **liste de courses** : noms génériques et achetables (« Crème fraîche »,
  pas « crème de la ferme du coin »), rayon exact, une ligne par produit à acheter. Pas d'eau.
- Enums acceptés (toute autre valeur retombe sur `AUTRE`/`NONE` sans erreur, donc attention aux fautes) :
  - `unit` : `G KG ML CL L CAC CAS PIECE TRANCHE GOUSSE PINCEE BOTTE BOITE SACHET NONE`
  - `aisle` : `FRUITS_LEGUMES BOUCHERIE FRAIS EPICERIE BOULANGERIE SURGELES BOISSONS AUTRE`
  - `cuisine` : `FRANCAISE ITALIENNE INDIENNE THAI JAPONAISE AMERICAINE AUTRE`
  - `protein` : `BOEUF PORC VOLAILLE VEAU AGNEAU POISSON VEGETARIEN AUTRE`
- Français, apostrophes typographiques ou droites indifférentes, unités de cuisson en « °C ».
- Forme du fichier : indentation 2 espaces, clés dans l'ordre de l'exemple ci-dessus, quantités entières sans
  `.0`, pas de `isFavorite` / `sourceUrl` / `imageUrls` vide. `check.py --fix` (et donc la CI) remet le fichier
  dans cette forme ; l'app publie déjà comme ça. Ne pas reformater le fichier autrement : un diff de 5 000
  lignes pour une recette rend l'historique illisible et provoque des conflits entre contributeurs.

Pour un lot de plusieurs recettes, `data/images/src/new_recipes.py` montre un générateur en Python
(fonctions `r()` / `i()`) qui écrit directement dans le catalogue.

## 2. L'illustration

Style imposé : **illustration flat vectorielle**, fond crème dégradé, plat vu de trois-quarts, couvert
posé à côté, vapeur. Pas de photo, pas d'image générée par IA, pas d'image prise sur le web (droits).
Tout est dessiné en SVG par `data/images/src/build.py`, qui produit un JPG 1200×800.

Ajouter une fonction **avant** le dictionnaire `DISHES`, puis l'y enregistrer sous le `remoteId` :

```python
def paupiettes_ail_creme():
    rng = random.Random(71)                       # graine fixe : rendu reproductible
    s = pot(600, 400, 340, 125, 230, "url(#potblue)", "url(#cream)")   # contenant
    # ... formes du plat : ellipses, paths, dots(), herbs() ...
    s += steam(600, 230)                          # vapeur
    s += spoon(1040, 330, 30)                     # couvert
    return s

DISHES = {
    ...
    "paupiettes-porc-ail-creme": paupiettes_ail_creme,
}
```

Briques disponibles (toutes renvoient une chaîne SVG à concaténer, canevas 1200×800, centre ≈ (600, 440)) :

| Fonction | Usage |
|---|---|
| `plate(cx, cy, rx, ry)` | assiette ronde blanche |
| `dish(cx, cy, w, h, color, rim)` | plat à gratin rectangulaire |
| `pot(cx, cy, rx, ry, depth, color, inner)` | cocotte (`url(#pot)` rouge, `url(#potblue)` bleue), `inner` = contenu |
| `bowl(cx, cy, rx, ry, depth, color, inner)` | bol / saladier |
| `fork(x, y, rot)`, `knife(x, y, rot)`, `spoon(x, y, rot)` | couverts |
| `steam(cx, y, n, gap)` | volutes de vapeur |
| `herbs([(x, y, rot), …], color)` | feuilles de persil / basilic |
| `dots(rng, cx, cy, rx, ry, n, rmin, rmax, colors)` | semis de points (graines, épices, oignons) |

Dégradés définis dans `DEFS` : `#cheese #gold #brown #meat #cream #ceramic #pot #potblue #broth #wine`
(utiliser `fill="url(#meat)"`). Pour un nouvel élément (légume, viande…), regarder une fonction existante
proche et copier sa manière de faire : ombre portée `filter="url(#soft)"` à opacité 0.25-0.3 sous les
morceaux, reflet blanc à opacité 0.4 sur le dessus. Le résultat doit être lisible en vignette 200 px :
3 à 6 gros éléments valent mieux que 30 petits.

Générer et regarder le résultat (en local ; sinon le workflow Catalogue le fait au push) :

```
python3 data/images/src/build.py paupiettes-porc-ail-creme
python3 data/images/src/build.py --missing      # tout ce qui n'a pas encore de JPG
```

Prérequis : `rsvg-convert` (paquet `librsvg2-bin`) et Pillow (`pip install pillow`). Le script écrit
`data/images/src/<remoteId>.svg` (source, à committer) et `data/images/<remoteId>.jpg` (servi à l'app).
Sans argument il régénère **toutes** les images : inutile et à éviter dans un commit de recette.

## 3. Version et vérification

- `version` : entier, **+1 à chaque modification** du fichier. Sans ça l'app répond « Catalogue déjà à
  jour » et ignore le changement. `updatedAt` : date ISO du jour.
- `python3 data/check.py` contrôle tout ce qui précède (JSON, enums, doublons, image présente, version
  incrémentée par rapport au dernier commit). Ne pas pousser tant qu'il n'affiche pas `OK`.
  Options : `--base REF` (commit de comparaison), `--fix` (renseigne `imageUrl` quand le JPG existe et
  incrémente `version` si quelque chose a changé). Le workflow Catalogue l'exécute aussi après chaque push.

## 4. Commit

Un commit par recette ou par lot, sur `main`, par exemple :

```
git add data/recipes.json data/images/src/build.py data/images/src/<remoteId>.svg data/images/<remoteId>.jpg
git commit -m "Catalogue v12 : paupiettes de porc à l'ail et à la crème, illustrée"
git push
```

Côté téléphone : la sync automatique au lancement lit `raw.githubusercontent.com`, dont le cache CDN dure
environ 5 minutes. « Mettre à jour maintenant » (Paramètres ou « Trouver des recettes ») passe par l'API
GitHub et voit le commit immédiatement. Le message attendu : `Catalogue vN : 1 ajouté(s), 0 mis à jour`.

## Modifier une recette existante

Même procédure : éditer l'objet (garder son `remoteId`), régénérer l'image seulement si le dessin change,
incrémenter `version`. À la sync, l'app remplace ingrédients et étapes mais conserve favori, photos perso
et modifications locales non publiées.

## Illustrer un plat de base

Les 15 plats de base ne sont pas dans `recipes` ; leur image est référencée par **nom exact** dans la carte
`images` du catalogue (`"Crêpes": "https://…/crepes.jpg"`). Même générateur, même procédure.
