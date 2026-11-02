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
- **Trouver des recettes** : affiche le site configuré dans les paramètres (site dédié à venir,
  champs `sourceUrl` / `remoteId` déjà prévus dans le modèle pour l'import).
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
