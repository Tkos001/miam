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
  l'accueil + bouton « Installer » : l'APK est téléchargé dans l'app et installé via PackageInstaller.
  Première fois : autoriser Miam à installer des applis + confirmation ; ensuite, sur Android 12+,
  Miam est l'installateur de lui-même et la mise à jour passe sans confirmation
  (`UPDATE_PACKAGES_WITHOUT_USER_ACTION`). L'app se ferme à la fin, à rouvrir.
- **Trouver des recettes** : peut aussi afficher un site configuré dans les paramètres.
- **Export / import JSON** (plats, planning, articles « Autres »). Tout est stocké en local
  (Room + DataStore), pas de compte, pas de réseau requis.

## Build

### Sans Android Studio (GitHub Actions)

Chaque push sur `main` lance le workflow **Build APK** (`.github/workflows/android.yml`).
Onglet *Actions* → dernier run → artefact `miam-debug-apk` → `app-debug.apk` à installer sur le
téléphone (autoriser les sources inconnues).

Signature : `app/miam-debug.keystore` (clé de dev committée) signe tous les builds, debug et release, pour
que chaque APK s'installe par-dessus le précédent. Ne pas la régénérer : il faudrait désinstaller l'app.

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

**Procédure détaillée (recette + illustration + vérification) : [`data/RECETTES.md`](data/RECETTES.md).**
`python3 data/check.py` valide le catalogue avant de pousser ; le workflow **Catalogue** le relance à chaque
push, génère les illustrations manquantes et les commite (contribution possible sans environnement local).
Les commits qui ne touchent que `data/` ou des `.md` ne déclenchent pas de build APK (le catalogue est lu à
l'exécution).

1. Éditer `data/recipes.json` : ajouter un objet dans `recipes` avec un `remoteId` unique et stable
   (slug, ex. `butternut-farcie-boeuf`). Même structure qu'une recette de l'export ci-dessous.
2. Illustration : déposer `data/images/<remoteId>.jpg` (1200×800, JPG) et renseigner
   `imageUrl` avec son URL raw GitHub. Sources vectorielles dans `data/images/src/`.
   L'app télécharge l'image à la sync ; une photo choisie à la main dans l'app n'est jamais écrasée.
3. Incrémenter `version` (sinon l'app considère le catalogue inchangé au lancement).
4. Commit + push sur `main`. L'app récupère le fichier via
   `https://raw.githubusercontent.com/Tkos001/miam/main/data/recipes.json` (cache CDN ~5 min ;
   « Mettre à jour maintenant » le contourne).

Pour illustrer un plat de base (déjà dans l'app, non décrit dans le catalogue), ajouter son nom exact
dans la carte `images` du catalogue : `"Crêpes": "https://…/crepes.jpg"`. Les illustrations sont
générées par `python3 data/images/src/build.py` (une fonction par plat, style flat homogène).

### Publier depuis l'app

Paramètres → « Contribuer au catalogue » → token GitHub fine-grained (dépôt `Tkos001/miam`,
permission *Contents : Read and write*). Ensuite, menu « ⋮ » d'une recette → « Publier dans le
catalogue » : l'app relit `data/recipes.json`, remplace/ajoute l'entrée (clé `remoteId`, sinon nom),
incrémente `version`, envoie la photo locale dans `data/images/<remoteId>.jpg` et commite.
Plusieurs personnes peuvent partager un token (un token par téléphone, révocable séparément) : le
champ « prénom » des paramètres devient l'auteur des commits.
**Photos** : chaque recette a une galerie (table `recipe_photos`) : l'illustration du catalogue
(`imageUrl`) et les photos publiées (`imageUrls`) sont descendues à la sync (source CATALOG), les photos
prises sur le téléphone s'ajoutent à côté (source USER) sans rien écraser. ★ dans l'édition choisit la
couverture affichée dans les listes. Ajouter ou retirer une photo ne marque pas la recette comme
modifiée ; la publication envoie les photos perso dans `data/images/<remoteId>-N.jpg`.
Une recette modifiée dans l'app n'est plus écrasée par la sync tant qu'elle n'est pas publiée
(badge « Modifiée ici, pas encore publiée ») ; le menu « ⋮ » propose alors « Reprendre la version du
catalogue » pour abandonner les modifications locales.

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
Protéine (`protein`) : `BOEUF PORC VOLAILLE VEAU AGNEAU POISSON VEGETARIEN AUTRE` (filtre dans « Mes plats »).
Ce format servira aussi à l'import depuis le futur site de recettes.
