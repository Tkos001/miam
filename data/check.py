#!/usr/bin/env python3
"""Vérifie le catalogue avant commit. Usage : python3 data/check.py

Contrôle : JSON valide, format, version incrémentée par rapport à HEAD, remoteId uniques et en slug,
champs obligatoires, enums connus de l'app, image présente dans data/images/ et imageUrl cohérente,
noms de la carte `images` existants dans data/images/. Code retour 1 si une erreur bloque.
"""
import json
import os
import re
import subprocess
import sys

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

# version par rapport au dernier commit
try:
    head = subprocess.run(["git", "show", "HEAD:data/recipes.json"], cwd=ROOT, capture_output=True, text=True, check=True).stdout
    head_version = json.loads(head).get("version")
    head_ids = {r.get("remoteId") for r in json.loads(head).get("recipes", [])}
    if isinstance(version, int) and version <= head_version and head != open(CAT, encoding="utf-8").read():
        err(f"version non incrémentée (HEAD = {head_version}, fichier = {version}) : l'app ne verra pas le changement")
except Exception:  # noqa: BLE001
    head_ids = set()
    warn("impossible de comparer avec HEAD (pas de git ?)")

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
