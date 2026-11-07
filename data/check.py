#!/usr/bin/env python3
"""Vérifie le catalogue avant commit. Usage : python3 data/check.py [--base REF] [--fix]

Contrôle : JSON valide, format, version incrémentée par rapport à REF (défaut : HEAD, ou HEAD~1 si le
fichier est identique à HEAD), remoteId uniques et en slug, champs obligatoires, enums connus de l'app,
image présente dans data/images/ et imageUrl cohérente, noms de la carte `images` existants dans
data/images/. Code retour 1 si une erreur bloque.

--fix : renseigne imageUrl pour les recettes dont data/images/<remoteId>.jpg existe (si quelque chose a
changé, incrémente version et met updatedAt à aujourd'hui), puis normalise le fichier : ordre des clés,
indentation 2, quantités entières sans « .0 », champs par défaut retirés (isFavorite false, imageUrls vide,
sourceUrl). La normalisation seule ne change pas la version. Utilisé par le workflow CI.
"""
import datetime
import json
import os
import re
import subprocess
import sys

FIX = "--fix" in sys.argv
BASE = sys.argv[sys.argv.index("--base") + 1] if "--base" in sys.argv else None

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CAT = os.path.join(ROOT, "data", "recipes.json")
IMG_DIR = os.path.join(ROOT, "data", "images")
IMG_URL = "https://raw.githubusercontent.com/Tkos001/miam/main/data/images/"

UNITS = {"G", "KG", "ML", "CL", "L", "CAC", "CAS", "PIECE", "TRANCHE", "GOUSSE", "PINCEE", "BOTTE", "BOITE", "SACHET", "NONE"}
AISLES = {"FRUITS_LEGUMES", "BOUCHERIE", "FRAIS", "EPICERIE", "BOULANGERIE", "SURGELES", "BOISSONS", "AUTRE"}
CUISINES = {"FRANCAISE", "ITALIENNE", "INDIENNE", "THAI", "JAPONAISE", "AMERICAINE", "AUTRE"}
PROTEINS = {"BOEUF", "PORC", "VOLAILLE", "VEAU", "AGNEAU", "POISSON", "VEGETARIEN", "AUTRE"}
SLUG = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")

errors, warnings = [], []
err = errors.append
warn = warnings.append

try:
    with open(CAT, encoding="utf-8") as f:
        cat = json.load(f)
except Exception as e:  # noqa: BLE001
    print(f"ERREUR : recipes.json illisible : {e}")
    sys.exit(1)

if cat.get("format") != "miam-catalog":
    err("format doit valoir \"miam-catalog\"")
version = cat.get("version")
if not isinstance(version, int):
    err("version doit être un entier")

RECIPE_KEYS = ["remoteId", "name", "description", "prepMinutes", "cookMinutes", "servings", "cuisine", "protein",
               "imageUrl", "imageUrls", "ingredients", "steps"]
ING_KEYS = ["name", "quantity", "unit", "aisle"]


def _num(v):
    return int(v) if isinstance(v, float) and v.is_integer() and abs(v) < 1e15 else v


def _ordered(d, keys):
    out = {k: d[k] for k in keys if k in d}
    out.update({k: v for k, v in d.items() if k not in out})
    return out


def canonical(cat):
    """Forme de référence du catalogue (celle que tout contributeur doit produire)."""
    recipes = []
    for r in cat.get("recipes", []):
        r = dict(r)
        r.pop("isFavorite", None)
        r.pop("sourceUrl", None)
        if not r.get("imageUrls"):
            r.pop("imageUrls", None)
        if r.get("imageUrl") is None:
            r.pop("imageUrl", None)
        ings = []
        for ing in r.get("ingredients", []):
            ing = dict(ing)
            ing["quantity"] = _num(ing.get("quantity"))
            ings.append(_ordered(ing, ING_KEYS))
        r["ingredients"] = ings
        recipes.append(_ordered(r, RECIPE_KEYS))
    out = {"format": cat.get("format"), "version": cat.get("version"), "updatedAt": cat.get("updatedAt"), "recipes": recipes}
    out["images"] = cat.get("images", {})
    return out


def dumps(cat):
    return json.dumps(cat, ensure_ascii=False, indent=2) + "\n"


# --fix : imageUrl manquantes dont le JPG existe, puis normalisation
changed = False
if FIX:
    for r in cat.get("recipes", []):
        rid = r.get("remoteId")
        if rid and not r.get("imageUrl") and os.path.exists(os.path.join(IMG_DIR, rid + ".jpg")):
            r["imageUrl"] = IMG_URL + rid + ".jpg"
            print("fix    : imageUrl renseignée pour", rid)
            changed = True
    if changed and isinstance(version, int):
        version = cat["version"] = version + 1
        cat["updatedAt"] = datetime.date.today().isoformat()
        print("fix    : catalogue passé en v", version)
    cat = canonical(cat)
    text = dumps(cat)
    if text != open(CAT, encoding="utf-8").read():
        with open(CAT, "w", encoding="utf-8") as f:
            f.write(text)
        print("fix    : fichier normalisé (ordre des clés, indentation, quantités)")

# version par rapport au commit de référence
def git_show(ref):
    return subprocess.run(["git", "show", f"{ref}:data/recipes.json"], cwd=ROOT, capture_output=True, text=True, check=True).stdout

try:
    current = open(CAT, encoding="utf-8").read()
    ref = BASE or "HEAD"
    base = git_show(ref)
    if BASE is None and base == current:
        ref, base = "HEAD~1", git_show("HEAD~1")
    base_version = json.loads(base).get("version")
    head_ids = {r.get("remoteId") for r in json.loads(base).get("recipes", [])}
    if isinstance(version, int) and version <= base_version and base != current:
        err(f"version non incrémentée ({ref} = {base_version}, fichier = {version}) : l'app ne verra pas le changement")
except Exception:  # noqa: BLE001
    head_ids = set()
    warn(f"impossible de comparer avec {BASE or 'HEAD'} (pas de git ?)")

recipes = cat.get("recipes", [])
seen = set()
for i, r in enumerate(recipes):
    rid = r.get("remoteId")
    tag = f"recette #{i} ({rid or r.get('name')})"
    if not rid or not SLUG.match(rid):
        err(f"{tag} : remoteId manquant ou pas un slug (minuscules, chiffres, tirets)")
    if rid in seen:
        err(f"{tag} : remoteId en double")
    seen.add(rid)
    for k in ("name", "description", "ingredients", "steps"):
        if not r.get(k):
            err(f"{tag} : champ {k} manquant ou vide")
    for k in ("prepMinutes", "cookMinutes", "servings"):
        if not isinstance(r.get(k), int):
            err(f"{tag} : {k} doit être un entier")
    if r.get("servings") != 4:
        warn(f"{tag} : servings = {r.get('servings')} (le catalogue est écrit pour 4)")
    if r.get("cuisine", "FRANCAISE") not in CUISINES:
        err(f"{tag} : cuisine inconnue {r.get('cuisine')!r}")
    if r.get("protein", "AUTRE") not in PROTEINS:
        err(f"{tag} : protein inconnue {r.get('protein')!r}")
    for j, ing in enumerate(r.get("ingredients", [])):
        it = f"{tag}, ingrédient #{j} ({ing.get('name')})"
        if not ing.get("name"):
            err(f"{it} : name manquant")
        if ing.get("unit", "NONE") not in UNITS:
            err(f"{it} : unit inconnue {ing.get('unit')!r}")
        if ing.get("aisle", "AUTRE") not in AISLES:
            err(f"{it} : aisle inconnu {ing.get('aisle')!r}")
        q = ing.get("quantity")
        if q is not None and not isinstance(q, (int, float)):
            err(f"{it} : quantity doit être un nombre ou null")
        if q is None and ing.get("unit", "NONE") != "NONE":
            warn(f"{it} : quantity null avec une unité")
    for j, st in enumerate(r.get("steps", [])):
        if not isinstance(st, str) or not st.strip():
            err(f"{tag} : étape #{j} vide ou pas une chaîne")
    url = r.get("imageUrl")
    if not url:
        warn(f"{tag} : pas d'imageUrl → fiche sans illustration (voir RECETTES.md § Illustration)")
    else:
        if url != IMG_URL + rid + ".jpg":
            err(f"{tag} : imageUrl attendue {IMG_URL}{rid}.jpg")
        if not os.path.exists(os.path.join(IMG_DIR, rid + ".jpg")):
            err(f"{tag} : data/images/{rid}.jpg absent (lancer build.py)")

for name, url in cat.get("images", {}).items():
    fn = url.rsplit("/", 1)[-1]
    if not url.startswith(IMG_URL) or not os.path.exists(os.path.join(IMG_DIR, fn)):
        err(f"images[{name!r}] : {url} ne pointe pas vers un fichier de data/images/")

for w in warnings:
    print("avert. :", w)
for e in errors:
    print("ERREUR :", e)
new = seen - head_ids
print(f"{len(recipes)} recettes, catalogue v{version}, {len(new)} nouvelle(s) : {', '.join(sorted(x for x in new if x)) or '-'}")
print("OK" if not errors else f"{len(errors)} erreur(s)")
sys.exit(1 if errors else 0)
