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
import com.tkos.miam.data.model.Recipe
import com.tkos.miam.data.model.RecipeWithDetails
import com.tkos.miam.data.model.ShoppingCheck
import com.tkos.miam.data.model.Slot
import com.tkos.miam.data.model.Step
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

    suspend fun saveRecipe(recipe: Recipe, ingredients: List<Ingredient>, steps: List<Step>): Long =
        recipeDao.upsertFull(recipe, ingredients, steps)

    suspend fun setFavorite(id: Long, favorite: Boolean) = recipeDao.setFavorite(id, favorite)

    suspend fun deleteRecipe(id: Long) {
        recipeDao.getById(id)?.imagePath?.let { deleteImage(it) }
        recipeDao.deleteRecipe(id)
    }

    /** Duplique une recette (utile pour adapter une recette de base). */
    suspend fun duplicateRecipe(id: Long): Long? {
        val d = recipeDao.getWithDetails(id) ?: return null
        return recipeDao.upsertFull(
            d.recipe.copy(id = 0, name = d.recipe.name + " (copie)", isBuiltIn = false, isFavorite = false, createdAt = System.currentTimeMillis()),
            d.sortedIngredients, d.sortedSteps,
        )
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
        var added = 0
        var updated = 0
        for (dto in file.recipes) {
            val remoteId = dto.remoteId?.trim().takeUnless { it.isNullOrBlank() } ?: continue
            val (recipe, ings, steps) = BackupCodec.fromDto(dto.copy(remoteId = remoteId, sourceUrl = url))
            val existing = recipeDao.getByRemoteId(remoteId)
                ?: byName[ShoppingAggregator.normalizeName(dto.name)]?.recipe?.takeIf { it.remoteId == null }
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
            recipeDao.getWithDetails(id)?.let { byName[ShoppingAggregator.normalizeName(it.recipe.name)] = it }
        }
        settings.setCatalogSynced(file.version, System.currentTimeMillis())
        return CatalogSyncResult.Updated(file.version, added, updated)
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
