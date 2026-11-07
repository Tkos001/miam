package com.tkos.miam.data

import android.content.Context
import android.net.Uri
import com.tkos.miam.data.backup.BackupCodec
import com.tkos.miam.data.backup.BackupFile
import com.tkos.miam.data.backup.PlanDto
import com.tkos.miam.data.catalog.Catalog
import com.tkos.miam.data.catalog.CatalogSyncResult
import com.tkos.miam.data.db.MiamDatabase
import com.tkos.miam.data.model.ExtraItem
import com.tkos.miam.data.model.Ingredient
import com.tkos.miam.data.model.PlanEntry
import com.tkos.miam.data.model.PlanEntryWithRecipe
import com.tkos.miam.data.model.PhotoSource
import com.tkos.miam.data.model.Recipe
import com.tkos.miam.data.model.RecipePhoto
import com.tkos.miam.data.model.RecipeWithDetails
import com.tkos.miam.data.model.ShoppingCheck
import com.tkos.miam.data.model.Slot
import com.tkos.miam.data.model.Step
import com.tkos.miam.data.model.Protein
import com.tkos.miam.data.seed.SeedRecipes
import com.tkos.miam.data.settings.Settings
import com.tkos.miam.data.settings.SettingsRepository
import com.tkos.miam.data.shopping.ShoppingAggregator
import com.tkos.miam.data.shopping.ShoppingLine
import com.tkos.miam.data.update.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Point d'accès unique aux données (Room + DataStore + fichiers images). */
class MiamRepository(private val context: Context) {
    private val db = MiamDatabase.get(context)
    private val recipeDao = db.recipeDao()
    private val planDao = db.planDao()
    private val shoppingDao = db.shoppingDao()
    val settings = SettingsRepository(context)

    // ---------- Recettes ----------
    val recipes: Flow<List<Recipe>> = recipeDao.observeAll()
    val favorites: Flow<List<Recipe>> = recipeDao.observeFavorites()
    val recipeCount: Flow<Int> = recipeDao.observeCount()
    val favoriteCount: Flow<Int> = recipeDao.observeFavoriteCount()

    fun recipe(id: Long): Flow<RecipeWithDetails?> = recipeDao.observeWithDetails(id)
    suspend fun getRecipe(id: Long): RecipeWithDetails? = recipeDao.getWithDetails(id)

    suspend fun saveRecipe(recipe: Recipe, ingredients: List<Ingredient>, steps: List<Step>): Long {
        val cover = if (recipe.id != 0L) recipeDao.getById(recipe.id)?.imagePath else null
        val id = recipeDao.upsertFull(recipe.copy(imagePath = cover), ingredients, steps)
        // Modifiée dans l'app : la sync du catalogue ne doit plus l'écraser tant qu'elle n'est pas publiée.
        if (recipe.id != 0L) settings.setRecipeDirty(id, true)
        return id
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = recipeDao.setFavorite(id, favorite)

    suspend fun deleteRecipe(id: Long) {
        recipeDao.getPhotos(id).forEach { deleteImage(it.path) }
        recipeDao.getById(id)?.imagePath?.let { deleteImage(it) }
        recipeDao.deleteRecipe(id)
        settings.setRecipeDirty(id, false)
    }

    /** Duplique une recette (utile pour adapter une recette de base). */
    suspend fun duplicateRecipe(id: Long): Long? {
        val d = recipeDao.getWithDetails(id) ?: return null
        val newId = recipeDao.upsertFull(
            d.recipe.copy(id = 0, name = d.recipe.name + " (copie)", imagePath = null, remoteId = null, sourceUrl = null,
                isBuiltIn = false, isFavorite = false, createdAt = System.currentTimeMillis()),
            d.sortedIngredients, d.sortedSteps,
        )
        withContext(Dispatchers.IO) {
            for (ph in d.gallery) {
                val src = File(ph.path); if (!src.exists()) continue
                val dst = File(File(context.filesDir, "images").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
                runCatching { src.copyTo(dst) }.onSuccess {
                    recipeDao.insertPhoto(RecipePhoto(recipeId = newId, path = dst.absolutePath, source = PhotoSource.USER.name, position = ph.position))
                    if (ph.path == d.recipe.imagePath) recipeDao.setCover(newId, dst.absolutePath)
                }
            }
        }
        return newId
    }

    // ---------- Planning (le « panier ») ----------
    fun planRange(from: Long, to: Long): Flow<List<PlanEntryWithRecipe>> = planDao.observeRange(from, to)
    val planAll: Flow<List<PlanEntryWithRecipe>> = planDao.observeAll()
    fun planCount(from: Long, to: Long): Flow<Int> = planDao.observeCountInRange(from, to)

    suspend fun addToPlan(epochDay: Long, slot: Slot, recipeId: Long, servings: Int) =
        planDao.insert(PlanEntry(epochDay = epochDay, slot = slot.name, recipeId = recipeId, servings = servings))

    suspend fun updatePlanEntry(entry: PlanEntry) = planDao.update(entry)
    suspend fun removeFromPlan(id: Long) = planDao.delete(id)
    suspend fun clearPlan(from: Long, to: Long) = planDao.deleteRange(from, to)

    // ---------- Liste de courses ----------
    val extras: Flow<List<ExtraItem>> = shoppingDao.observeExtras()
    val extrasUncheckedCount: Flow<Int> = shoppingDao.observeExtrasUncheckedCount()

    /** Lignes agrégées à partir de TOUT le planning (toutes les cases non passées depuis aujourd'hui incluses). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun shoppingLines(from: Long, to: Long): Flow<List<ShoppingLine>> =
        planDao.observeRange(from, to).flatMapLatest { entries ->
            // Recharger les détails à chaque changement de recettes ou de planning.
            recipeDao.observeAll().flatMapLatest {
                flow { emit(entries.map { it.entry.recipeId }.distinct().mapNotNull { recipeDao.getWithDetails(it) }.associateBy { it.recipe.id }) }
            }.combine(shoppingDao.observeChecks()) { details, checks ->
                ShoppingAggregator.aggregate(entries, details, checks.filter { it.checked }.map { it.key }.toSet())
            }
        }

    suspend fun setLineChecked(key: String, checked: Boolean) {
        if (checked) shoppingDao.upsertCheck(ShoppingCheck(key, true)) else shoppingDao.deleteCheck(key)
    }
    suspend fun clearChecks() = shoppingDao.clearChecks()

    suspend fun addExtra(item: ExtraItem) = shoppingDao.insertExtra(item)
    suspend fun updateExtra(item: ExtraItem) = shoppingDao.updateExtra(item)
    suspend fun deleteExtra(item: ExtraItem) = shoppingDao.deleteExtra(item)
    suspend fun deleteCheckedExtras() = shoppingDao.deleteCheckedExtras()

    // ---------- Photos (galerie) ----------
    fun photos(recipeId: Long): Flow<List<RecipePhoto>> = recipeDao.observePhotos(recipeId)

    /** Ajoute une photo choisie par l'utilisateur. Ne marque PAS la recette comme modifiée (les photos ne bloquent pas la sync). */
    suspend fun addPhoto(recipeId: Long, uri: Uri): RecipePhoto? {
        val path = importImage(uri) ?: return null
        val pos = (recipeDao.getPhotos(recipeId).maxOfOrNull { it.position } ?: -1) + 1
        val photo = RecipePhoto(recipeId = recipeId, path = path, source = PhotoSource.USER.name, position = pos)
        val id = recipeDao.insertPhoto(photo)
        if (recipeDao.getById(recipeId)?.imagePath == null) recipeDao.setCover(recipeId, path)
        return photo.copy(id = id)
    }

    suspend fun removePhoto(photo: RecipePhoto) {
        recipeDao.deletePhoto(photo.id)
        deleteImage(photo.path)
        val recipe = recipeDao.getById(photo.recipeId) ?: return
        if (recipe.imagePath == photo.path) recipeDao.setCover(photo.recipeId, recipeDao.getPhotos(photo.recipeId).firstOrNull()?.path)
    }

    suspend fun setCover(recipeId: Long, path: String) = recipeDao.setCover(recipeId, path)

    // ---------- Images ----------
    /** Copie une image choisie par l'utilisateur dans le stockage interne, retourne le chemin. */
    suspend fun importImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            file.absolutePath
        }.getOrNull()
    }

    fun deleteImage(path: String) {
        runCatching { File(path).delete() }
    }

    // ---------- Données de base ----------
    suspend fun seedIfNeeded() {
        val s = settings.settings.first()
        if (!s.seeded && recipeDao.count() == 0) {
            restoreBuiltIn()
            settings.setSeeded(true)
        } else {
            backfillBuiltInProtein()
        }
    }

    /**
     * Les plats de base insérés avant l'ajout de la colonne `protein` ont hérité de AUTRE (migration 2→3) ;
     * on leur applique le type de viande défini dans [SeedRecipes], par nom. Idempotent, rien à faire ensuite.
     */
    private suspend fun backfillBuiltInProtein() {
        val seeds = SeedRecipes.all.associateBy { ShoppingAggregator.normalizeName(it.recipe.name) }
        for (d in recipeDao.getAllWithDetails()) {
            val r = d.recipe
            if (r.proteinEnum != Protein.AUTRE) continue
            val seed = seeds[ShoppingAggregator.normalizeName(r.name)]?.recipe ?: continue
            if (seed.proteinEnum == Protein.AUTRE) continue
            recipeDao.updateRecipe(r.copy(protein = seed.protein))
        }
    }

    /** Réinsère les recettes de base manquantes (par nom). */
    suspend fun restoreBuiltIn(): Int {
        val existing = recipeDao.getAllWithDetails().map { ShoppingAggregator.normalizeName(it.recipe.name) }.toSet()
        var added = 0
        for (seed in SeedRecipes.all) {
            if (ShoppingAggregator.normalizeName(seed.recipe.name) in existing) continue
            recipeDao.upsertFull(seed.recipe, seed.ingredients, seed.steps)
            added++
        }
        return added
    }

    // ---------- Catalogue distant (recettes dans le dépôt git) ----------

    /** Dernière info de mise à jour de l'app trouvée (null = pas vérifié ou à jour). */
    private val _availableUpdate = MutableStateFlow<AppUpdate.Info?>(null)
    val availableUpdate: StateFlow<AppUpdate.Info?> = _availableUpdate

    fun catalogUrl(s: Settings): String = s.catalogUrl.trim().ifBlank { Catalog.DEFAULT_URL }

    /**
     * Télécharge le catalogue et intègre les recettes.
     * - `force = false` : ne fait rien si la version distante n'est pas plus récente que la version locale.
     * - `force = true` : réapplique tout le catalogue (et contourne le cache CDN).
     * Les recettes sont identifiées par `remoteId` ; à défaut, une recette locale de même nom est rattachée.
     * Favoris, photo et id locaux sont conservés ; ingrédients et étapes sont remplacés.
     */
    suspend fun syncCatalog(force: Boolean): CatalogSyncResult {
        val s = settings.settings.first()
        val url = catalogUrl(s)
        val file = try {
            Catalog.fetch(url, bustCache = force)
        } catch (e: Exception) {
            return CatalogSyncResult.Failed(e.message ?: e.javaClass.simpleName)
        }
        if (!force && file.version <= s.catalogVersion) return CatalogSyncResult.UpToDate(file.version)

        val byName = recipeDao.getAllWithDetails().associateBy { ShoppingAggregator.normalizeName(it.recipe.name) }.toMutableMap()
        val dirty = s.dirtyRecipeIds
        var added = 0
        var updated = 0
        for (dto in file.recipes) {
            val remoteId = dto.remoteId?.trim().takeUnless { it.isNullOrBlank() } ?: continue
            val (recipe, ings, steps) = BackupCodec.fromDto(dto.copy(remoteId = remoteId, sourceUrl = url))
            val existing = recipeDao.getByRemoteId(remoteId)
                ?: byName[ShoppingAggregator.normalizeName(dto.name)]?.recipe?.takeIf { it.remoteId == null }
            if (existing != null && existing.id in dirty) {
                // Modifiée localement, non publiée : on rattache l'id distant, on garde le contenu local, on met à jour les images.
                if (existing.remoteId != remoteId) recipeDao.updateRecipe(existing.copy(remoteId = remoteId, sourceUrl = url))
                applyCatalogImages(existing.id, remoteId, dto.imageUrl, dto.imageUrls, force)
                continue
            }
            val toSave = if (existing != null) {
                updated++
                recipe.copy(
                    id = existing.id, imagePath = existing.imagePath, isFavorite = existing.isFavorite,
                    isBuiltIn = existing.isBuiltIn, createdAt = existing.createdAt,
                )
            } else {
                added++
                recipe
            }
            val id = recipeDao.upsertFull(toSave, ings, steps)
            applyCatalogImages(id, remoteId, dto.imageUrl, dto.imageUrls, force)
            recipeDao.getWithDetails(id)?.let { byName[ShoppingAggregator.normalizeName(it.recipe.name)] = it }
        }
        // Illustrations des recettes locales (plats de base) désignées par leur nom.
        for ((name, imageUrl) in file.images) {
            val local = byName[ShoppingAggregator.normalizeName(name)]?.recipe ?: continue
            applyCatalogImages(local.id, "img-" + ShoppingAggregator.normalizeName(name), imageUrl, emptyList(), force)
        }
        settings.setCatalogSynced(file.version, System.currentTimeMillis())
        return CatalogSyncResult.Updated(file.version, added, updated)
    }

    /**
     * Télécharge les images du catalogue (illustration + photos publiées) dans `images/catalog-<remoteId>[-N].jpg`
     * et les ajoute à la galerie de la recette (source CATALOG). Les photos perso ne sont jamais touchées.
     * La couverture n'est définie que si la recette n'en a pas. Échec réseau = silencieux, réessayé à la prochaine sync.
     */
    private suspend fun applyCatalogImages(recipeId: Long, remoteId: String, imageUrl: String?, imageUrls: List<String>, force: Boolean) {
        val urls = (listOfNotNull(imageUrl) + imageUrls).filter { it.isNotBlank() }.distinct()
        if (urls.isEmpty()) return
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val safe = remoteId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        urls.forEachIndexed { n, u ->
            val dest = File(dir, if (n == 0) "catalog-$safe.jpg" else "catalog-$safe-$n.jpg")
            val known = recipeDao.getPhoto(recipeId, dest.absolutePath)
            if (known != null && dest.exists() && !force) return@forEachIndexed
            val ok = dest.exists() && !force || withContext(Dispatchers.IO) { runCatching { com.tkos.miam.data.catalog.Http.download(u, dest) }.isSuccess }
            if (!ok) return@forEachIndexed
            if (known == null) recipeDao.insertPhoto(RecipePhoto(recipeId = recipeId, path = dest.absolutePath, source = PhotoSource.CATALOG.name, position = n))
            val recipe = recipeDao.getById(recipeId) ?: return
            if (recipe.imagePath == null || !File(recipe.imagePath).exists()) recipeDao.setCover(recipeId, dest.absolutePath)
        }
    }

    /**
     * Abandonne les modifications locales d'une recette et reprend la version du catalogue
     * (ingrédients, étapes, infos). Photos perso et favori conservés.
     */
    suspend fun revertToCatalog(recipeId: Long): kotlin.Result<String> = runCatching {
        val s = settings.settings.first()
        val local = recipeDao.getById(recipeId) ?: error("Recette introuvable")
        val remoteId = local.remoteId ?: error("Cette recette ne vient pas du catalogue")
        val file = Catalog.fetch(catalogUrl(s), bustCache = true)
        val dto = file.recipes.firstOrNull { it.remoteId == remoteId } ?: error("Recette absente du catalogue")
        val (recipe, ings, steps) = BackupCodec.fromDto(dto.copy(sourceUrl = catalogUrl(s)))
        recipeDao.upsertFull(
            recipe.copy(id = local.id, imagePath = local.imagePath, isFavorite = local.isFavorite, isBuiltIn = local.isBuiltIn, createdAt = local.createdAt),
            ings, steps,
        )
        settings.setRecipeDirty(recipeId, false)
        applyCatalogImages(recipeId, remoteId, dto.imageUrl, dto.imageUrls, false)
        "Version du catalogue restaurée"
    }

    /**
     * Publie une recette dans le catalogue git (commit via l'API GitHub).
     * Nécessite le token configuré dans les paramètres. Retourne le message de résultat.
     */
    suspend fun publishRecipe(recipeId: Long): kotlin.Result<String> = runCatching {
        val s = settings.settings.first()
        require(s.githubToken.isNotBlank()) { "Aucun token GitHub : renseigne-le dans Paramètres → Contribuer au catalogue" }
        val d = recipeDao.getWithDetails(recipeId) ?: error("Recette introuvable")
        val url = catalogUrl(s)
        val r = com.tkos.miam.data.catalog.CatalogPublisher.publish(d, url, s.githubToken, s.contributorName)
        if (d.recipe.remoteId != r.remoteId || d.recipe.sourceUrl != url) {
            recipeDao.updateRecipe(d.recipe.copy(remoteId = r.remoteId, sourceUrl = url))
        }
        // Les photos perso publiées deviennent des images du catalogue (même nom de fichier que la sync), pour ne pas les dupliquer.
        val dir = File(context.filesDir, "images")
        val safe = r.remoteId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        for ((localPath, index) in r.publishedPhotos) {
            val photo = recipeDao.getPhoto(recipeId, localPath) ?: continue
            val dest = File(dir, if (index == 0) "catalog-$safe.jpg" else "catalog-$safe-$index.jpg")
            if (File(localPath).renameTo(dest) || runCatching { File(localPath).copyTo(dest, overwrite = true); File(localPath).delete() }.isSuccess) {
                recipeDao.deletePhoto(photo.id)
                recipeDao.insertPhoto(photo.copy(id = 0, path = dest.absolutePath, source = PhotoSource.CATALOG.name, position = index))
                if (recipeDao.getById(recipeId)?.imagePath == localPath) recipeDao.setCover(recipeId, dest.absolutePath)
            }
        }
        settings.setRecipeDirty(recipeId, false)
        settings.setCatalogSynced(r.version, System.currentTimeMillis())
        "« ${d.recipe.name} » publiée (catalogue v${r.version})"
    }

    /** Vérifie la dernière release GitHub ; mémorise le résultat pour l'afficher sur l'accueil. */
    suspend fun checkAppUpdate(): Result<AppUpdate.Info> = runCatching { AppUpdate.check() }
        .onSuccess { _availableUpdate.value = it.takeIf { info -> info.isNewer } }

    // ---------- Export / import ----------
    suspend fun exportBackup(): String {
        val recipes = recipeDao.getAllWithDetails()
        val plan = planDao.getAll()
        val extras = shoppingDao.getExtras()
        return BackupCodec.encode(
            BackupFile(
                recipes = recipes.map { BackupCodec.toDto(it) },
                plan = plan.map { PlanDto(it.entry.epochDay, it.entry.slot, it.recipe.name, it.entry.servings) },
                extras = extras.map { BackupCodec.toDto(it) },
            )
        )
    }

    /** Importe un fichier JSON. Les recettes de même nom sont remplacées. Retourne le nombre de recettes importées. */
    suspend fun importBackup(text: String, replaceAll: Boolean): Int {
        val file = BackupCodec.decode(text)
        if (replaceAll) {
            planDao.deleteAll()
            shoppingDao.deleteAllExtras()
            shoppingDao.clearChecks()
            recipeDao.deleteAllRecipes()
        }
        val byName = recipeDao.getAllWithDetails().associateBy { ShoppingAggregator.normalizeName(it.recipe.name) }.toMutableMap()
        var count = 0
        for (dto in file.recipes) {
            val (recipe, ings, steps) = BackupCodec.fromDto(dto)
            val existing = byName[ShoppingAggregator.normalizeName(dto.name)]
            val toSave = if (existing != null) recipe.copy(id = existing.recipe.id, imagePath = existing.recipe.imagePath, isBuiltIn = existing.recipe.isBuiltIn) else recipe
            val id = recipeDao.upsertFull(toSave, ings, steps)
            recipeDao.getWithDetails(id)?.let { byName[ShoppingAggregator.normalizeName(it.recipe.name)] = it }
            count++
        }
        for (p in file.plan) {
            val rid = byName[ShoppingAggregator.normalizeName(p.recipeName)]?.recipe?.id ?: continue
            planDao.insert(BackupCodec.fromDto(p, rid))
        }
        for (e in file.extras) shoppingDao.insertExtra(BackupCodec.fromDto(e))
        return count
    }

    suspend fun exportToUri(uri: Uri) = withContext(Dispatchers.IO) {
        val text = exportBackup()
        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
    }

    suspend fun importFromUri(uri: Uri, replaceAll: Boolean): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Impossible de lire le fichier")
        importBackup(text, replaceAll)
    }

    companion object {
        @Volatile private var instance: MiamRepository? = null
        fun get(context: Context): MiamRepository = instance ?: synchronized(this) {
            instance ?: MiamRepository(context.applicationContext).also { instance = it }
        }
    }
}
