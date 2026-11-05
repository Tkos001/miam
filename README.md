# Miam 🧺

Application Android (Kotlin + Jetpack Compose, Material 3) pour planifier ses repas et générer
automatiquement la liste de courses.

## Fonctionnalités

- **Mes plats** : fiches « carte » (photo, infos, ingrédients avec quantité/unité/rayon, étapes).
  La fiche affiche les étapes en cartes à faire défiler vers la gauche. Favoris, duplication,
  partage en texte.
- **Planning** : semaine par semaine, midi / soir, plusieurs plats par case, nombre de personnes
  par case (les quantités sont mises à l'échelle).
- **Liste de courses** : agrégation automatique de tous les ingrédients du planning (4 semaines
  glissantes), fusion des doublons par unité compatible (g/kg, ml/cl/L, pièces…), groupement par
  rayon ou liste plate, cases à cocher, rubrique « Autres articles » hors plats, partage / copie.
- **15 plats français de base** insérés au premier lancement, restaurables depuis les paramètres.
- **Thèmes** : suivre le système, clair, sombre, mauve sombre, rouge sombre.
- **Catalogue de recettes versionné dans ce dépôt** (`data/recipes.json`) : l'app le télécharge au
  lancement et intègre les nouvelles recettes sans rebuild. Mise à jour forcée depuis Paramètres ou
  « Trouver des recettes ».
- **Mise à jour de l'app** : vérification de la dernière release GitHub au lancement, bannière sur
  l'accueil + bouton « Télécharger » (APK `miam-latest.apk`).
- **Trouver des recettes** : peut aussi afficher un site configuré dans les paramètres.
- **Export / import JSON** (plats, planning, articles « Autres »). Tout est stocké en local
  (Room + DataStore), pas de compte, pas de réseau requis.

## Build

### Sans Android Studio (GitHub Actions)

Chaque push sur `main` lance le workflow **Build APK** (`.github/workflows/android.yml`).
Onglet *Actions* → dernier run → artefact `miam-debug-apk` → `app-debug.apk` à installer sur le
téléphone (autoriser les sources inconnues).

### En local

Android Studio Ladybug ou plus récent, JDK 17 :

```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Structure

```
app/src/main/java/com/tkos/miam/
├── MiamApp.kt / MainActivity.kt
├── data/
│   ├── model/      Entités Room + enums (unités, rayons, cuisines, midi/soir)
│   ├── db/         Base Room, DAO
│   ├── shopping/   Agrégation / fusion des ingrédients → lignes de courses
│   ├── seed/       15 recettes de base
│   ├── backup/     Format JSON export/import
│   ├── settings/   DataStore (thème, options)
│   └── MiamRepository.kt
└── ui/
    ├── theme/      4 thèmes Material 3
    ├── components/ Composants réutilisables
    └── screens/    Accueil, plats, fiche, édition, planning, courses, paramètres, recherche
```

## Ajouter une recette au catalogue

1. Éditer `data/recipes.json` : ajouter un objet dans `recipes` avec un `remoteId` unique et stable
   (slug, ex. `butternut-farcie-boeuf`). Même structure qu'une recette de l'export ci-dessous.
2. Illustration : déposer `data/images/<remoteId>.jpg` (1200×800, JPG) et renseigner
   `imageUrl` avec son URL raw GitHub. Sources vectorielles dans `data/images/src/`.
   L'app télécharge l'image à la sync ; une photo choisie à la main dans l'app n'est jamais écrasée.
3. Incrémenter `version` (sinon l'app considère le catalogue inchangé au lancement).
4. Commit + push sur `main`. L'app récupère le fichier via
   `https://raw.githubusercontent.com/Tkos001/miam/main/data/recipes.json` (cache CDN ~5 min ;
   « Mettre à jour maintenant » le contourne).

Règles de fusion côté app : une recette est reconnue par son `remoteId` (à défaut par son nom si la
recette locale n'a pas de `remoteId`). Favori, photo et id local sont conservés ; ingrédients et
étapes sont remplacés. Une recette du catalogue supprimée localement revient à la prochaine montée
de version.

## Format d'export JSON

```json
{
  "format": "miam-backup",
  "version": 1,
  "recipes": [
    {
      "name": "Crêpes", "servings": 4, "cuisine": "FRANCAISE",
      "ingredients": [{ "name": "Farine", "quantity": 250, "unit": "G", "aisle": "EPICERIE" }],
      "steps": ["Mélanger…"]
    }
  ],
  "plan": [{ "epochDay": 20760, "slot": "SOIR", "recipeName": "Crêpes", "servings": 4 }],
  "extras": [{ "name": "Papier toilette", "aisle": "AUTRE" }]
}
```

Unités : `G KG ML CL L CAC CAS PIECE TRANCHE GOUSSE PINCEE BOTTE BOITE SACHET NONE`.
Rayons : `FRUITS_LEGUMES BOUCHERIE FRAIS EPICERIE BOULANGERIE SURGELES BOISSONS AUTRE`.
Ce format servira aussi à l'import depuis le futur site de recettes.
