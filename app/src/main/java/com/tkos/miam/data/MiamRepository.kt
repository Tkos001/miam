package com.tkos.miam.data

import android.content.Context
import android.net.Uri
import com.tkos.miam.data.backup.BackupCodec
import com.tkos.miam.data.backup.RecipeDto
import com.tkos.miam.data.ai.RecipeAssistant
import com.tkos.miam.data.ai.ReceiptParser
import com.tkos.miam.data.ai.ReceiptItem
import com.tkos.miam.data.ai.Ocr
import com.tkos.miam.data.ai.LlmClient
import com.tkos.miam.data.backup.BackupFile
import com.tkos.miam.data.backup.MealDto
import com.tkos.miam.data.backup.StockDto
import com.tkos.miam.data.catalog.Catalog
import com.tkos.miam.data.catalog.CatalogSyncResult
import com.tkos.miam.data.db.MiamDatabase
import com.tkos.miam.data.model.UnitFamily
import com.tkos.miam.data.model.MeasureUnit
import com.tkos.miam.data.model.Aisle
import com.tkos.miam.data.model.ExtraItem
import com.tkos.miam.data.model.Ingredient
import com.tkos.miam.data.model.Meal
import com.tkos.miam.data.model.MealWithRecipe
import com.tkos.miam.data.model.PhotoSource
import com.tkos.miam.data.model.Recipe
import com.tkos.miam.data.model.RecipePhoto
import com.tkos.miam.data.model.RecipeWithDetails
import com.tkos.miam.data.model.ShoppingCheck
import com.tkos.miam.data.model.StockItem
import com.tkos.miam.data.model.Step
import com.tkos.miam.data.model.Protein
import com.tkos.miam.data.seed.SeedRecipes
import com.tkos.miam.data.settings.Settings
import com.tkos.miam.data.settings.SettingsRepository
import com.tkos.miam.data.shopping.ShoppingAggregator
import com.tkos.miam.data.shopping.ShoppingLine
import com.tkos.miam.data.shopping.StockLevel
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
    private val mealDao = db.mealDao()
    private val stockDao = db.stockDao()
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

    // ---------- Repas à faire (la « liste des repas ») ----------
    val meals: Flow<List<MealWithRecipe>> = mealDao.observeAll()
    val mealsTodo: Flow<List<MealWithRecipe>> = mealDao.observeTodo()
    val mealsTodoCount: Flow<Int> = mealDao.observeTodoCount()

    /** Ajoute un repas principal, ou (avec `parentId`) une entrée / un accompagnement / un dessert rattaché à un repas. */
    suspend fun addMeal(recipeId: Long, servings: Int, parentId: Long? = null): Long =
        mealDao.insert(Meal(recipeId = recipeId, servings = servings, position = mealDao.maxPosition() + 1, parentId = parentId))

    suspend fun updateMeal(meal: Meal) = mealDao.update(meal)
    suspend fun removeMeal(id: Long) { mealDao.deleteChildren(id); mealDao.delete(id) }
    suspend fun deleteDoneMeals() = mealDao.deleteDone()

    /** Monte (`-1`) ou descend (`+1`) un repas dans la liste des repas à faire. */
    suspend fun moveMeal(id: Long, delta: Int) {
        val todo = mealDao.getAll().filter { !it.meal.done && it.meal.parentId == null }.map { it.meal }
        val idx = todo.indexOfFirst { it.id == id }
        val other = idx + delta
        if (idx < 0 || other !in todo.indices) return
        val reordered = todo.toMutableList().also { val m = it.removeAt(idx); it.add(other, m) }
        reordered.forEachIndexed { pos, m -> if (m.position != pos) mealDao.update(m.copy(position = pos)) }
    }

    /**
     * Coche / décoche « mangé ». Coché : les ingrédients du repas (à l'échelle) sont retirés du stock ;
     * décoché : ils y sont remis (approximation : on ne sait pas ce qui a vraiment été consommé).
     */
    suspend fun setMealDone(id: Long, done: Boolean) {
        val m = mealDao.get(id) ?: return
        // Un repas principal entraîne ses accompagnements.
        for (c in mealDao.children(id)) setMealDone(c.meal.id, done)
        if (m.meal.done == done) return
        val d = recipeDao.getWithDetails(m.meal.recipeId)
        if (d != null) {
            for (n in ShoppingAggregator.needsOf(d, m.meal.servings)) {
                if (done) consumeFromStock(n.key, n.baseQuantity) else addToStock(n.key, n.name, n.baseQuantity, n.family, n.aisle)
            }
        }
        mealDao.update(m.meal.copy(done = done, doneAt = if (done) System.currentTimeMillis() else null))
    }

    // ---------- Stock (ce qu'il y a à la maison, local au téléphone) ----------
    val stock: Flow<List<StockItem>> = stockDao.observeAll()
    val stockCount: Flow<Int> = stockDao.observeCount()

    /** Ajoute `baseQuantity` à l'article (créé au besoin). Familles « présence » (huile, sel…) : sans quantité. */
    suspend fun addToStock(key: String, name: String, baseQuantity: Double?, family: UnitFamily, aisle: Aisle) {
        val presence = ShoppingAggregator.isPresenceFamily(family)
        val existing = stockDao.getByKey(key)
        val now = System.currentTimeMillis()
        if (existing == null) {
            stockDao.upsert(StockItem(key = key, name = name.replaceFirstChar { it.titlecase(java.util.Locale.FRENCH) },
                quantity = if (presence) null else baseQuantity, family = family.name, aisle = aisle.name, updatedAt = now))
        } else {
            val q = if (presence || baseQuantity == null) existing.quantity
                    else if (existing.quantity == null) baseQuantity else existing.quantity + baseQuantity
            stockDao.update(existing.copy(quantity = q, aisle = if (existing.aisleEnum == Aisle.AUTRE) aisle.name else existing.aisle, updatedAt = now))
        }
    }

    /**
     * Retire `baseQuantity` du stock. Un article `always` n'est jamais retiré ; un article sans quantité
     * reste tel quel (on ne sait pas le décompter) ; à 0 ou moins, l'article disparaît.
     */
    suspend fun consumeFromStock(key: String, baseQuantity: Double?) {
        val item = stockDao.getByKey(key) ?: return
        if (item.always || item.quantity == null || baseQuantity == null) return
        val rest = item.quantity - baseQuantity
        if (rest <= 1e-9) stockDao.delete(item.id) else stockDao.update(item.copy(quantity = rest, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateStockItem(item: StockItem) = stockDao.update(item.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteStockItem(id: Long) = stockDao.delete(id)
    suspend fun deleteStockByKey(key: String) = stockDao.deleteByKey(key)
    suspend fun clearStock() = stockDao.deleteAll()

    suspend fun addStockManually(name: String, quantity: Double?, unit: MeasureUnit, aisle: Aisle, always: Boolean) {
        val key = ShoppingAggregator.keyOf(name, unit.family)
        addToStock(key, name, quantity?.let { it * unit.factor }, unit.family, aisle)
        if (always) stockDao.getByKey(key)?.let { stockDao.update(it.copy(always = true)) }
    }

    // ---------- Assistant IA (compte de l'utilisateur) et lecture de tickets ----------
    suspend fun llm(): LlmClient = LlmClient.from(settings.settings.first())

    /** Noms d'ingrédients connus (recettes + stock), pour rapprocher les lignes d'un ticket. */
    private suspend fun knownIngredients(): Pair<List<String>, Map<String, Aisle>> {
        val ings = recipeDao.getAllWithDetails().flatMap { it.ingredients }
        val aisles = HashMap<String, Aisle>()
        val names = LinkedHashMap<String, String>()
        for (i in ings) {
            val k = ShoppingAggregator.normalizeName(i.name)
            names.putIfAbsent(k, i.name.trim())
            if (i.aisleEnum != Aisle.AUTRE) aisles.putIfAbsent(k, i.aisleEnum)
        }
        for (st in stockDao.getAll()) {
            val k = ShoppingAggregator.normalizeName(st.name)
            names.putIfAbsent(k, st.name); if (st.aisleEnum != Aisle.AUTRE) aisles.putIfAbsent(k, st.aisleEnum)
        }
        return names.values.toList() to aisles
    }

    data class ReceiptResult(val items: List<ReceiptItem>, val rawLines: List<String>, val usedAi: Boolean, val aiError: String?)

    /** OCR local, puis structuration par l'assistant si configuré (repli sur le parseur local en cas d'échec). */
    suspend fun readReceipt(uri: Uri): ReceiptResult {
        val lines = Ocr.readLines(context, uri)
        val (names, aisles) = knownIngredients()
        val client = llm()
        if (client.isConfigured && lines.isNotEmpty()) {
            try {
                val answer = client.complete(ReceiptParser.llmSystemPrompt(names), lines.joinToString("\n"))
                val items = ReceiptParser.parseLlm(answer).map { it ->
                    val k = ShoppingAggregator.normalizeName(it.name)
                    if (it.aisleEnum == Aisle.AUTRE && aisles[k] != null) it.copy(aisle = aisles[k]!!.name) else it
                }
                if (items.isNotEmpty()) return ReceiptResult(items, lines, true, null)
            } catch (e: Exception) {
                return ReceiptResult(ReceiptParser.parse(lines, names, aisles), lines, false, e.message ?: e.javaClass.simpleName)
            }
        }
        return ReceiptResult(ReceiptParser.parse(lines, names, aisles), lines, false, null)
    }

    /** Texte complet à coller dans une appli IA (abonnement, sans clé) : consignes + lignes du ticket. */
    suspend fun receiptPromptForManual(rawLines: List<String>): String {
        val (names, _) = knownIngredients()
        return ReceiptParser.llmSystemPrompt(names) + "\n\nTicket :\n" + rawLines.joinToString("\n")
    }

    /** Réponse collée depuis l'appli IA → lignes à relire (rayons complétés depuis les ingrédients connus). */
    suspend fun parseReceiptAnswer(text: String): List<ReceiptItem> {
        val (_, aisles) = knownIngredients()
        return ReceiptParser.parseLlm(text).map {
            val k = ShoppingAggregator.normalizeName(it.name)
            if (it.aisleEnum == Aisle.AUTRE && aisles[k] != null) it.copy(aisle = aisles[k]!!.name) else it
        }
    }

    /** Texte complet à coller dans une appli IA pour obtenir des recettes au format de l'app. */
    suspend fun recipePromptForManual(request: String, useStock: Boolean): String {
        val stock = if (useStock) stockDao.getAll() else emptyList()
        val existing = recipeDao.getAllWithDetails().map { it.recipe.name }
        return RecipeAssistant.systemPrompt() + "\n\n" + RecipeAssistant.userPrompt(request, stock, useStock, existing)
    }

    fun parseRecipeAnswer(text: String): List<RecipeDto> = RecipeAssistant.parse(text)

    /** Entre en stock les lignes validées par l'utilisateur. Retourne le nombre d'articles. */
    suspend fun addReceiptToStock(items: List<ReceiptItem>): Int {
        var n = 0
        for (it in items) {
            if (!it.selected || it.name.isBlank()) continue
            val unit = it.unitEnum
            addToStock(ShoppingAggregator.keyOf(it.name, unit.family), it.name, it.quantity?.let { q -> q * unit.factor }, unit.family, it.aisleEnum)
            n++
        }
        return n
    }

    /** Demande 1 à 3 recettes à l'assistant, dans le format de l'app. */
    suspend fun askRecipes(request: String, useStock: Boolean): List<RecipeDto> {
        val client = llm()
        val stock = if (useStock) stockDao.getAll() else emptyList()
        val existing = recipeDao.getAllWithDetails().map { it.recipe.name }
        val answer = client.complete(RecipeAssistant.systemPrompt(), RecipeAssistant.userPrompt(request, stock, useStock, existing), maxTokens = 6000)
        return RecipeAssistant.parse(answer)
    }

    /** Enregistre une recette proposée par l'assistant dans « Mes plats ». Retourne son id. */
    suspend fun importRecipeDto(dto: RecipeDto): Long {
        val (recipe, ings, steps) = BackupCodec.fromDto(dto)
        return recipeDao.upsertFull(recipe, ings, steps)
    }

    // ---------- Liste de courses ----------
    val extras: Flow<List<ExtraItem>> = shoppingDao.observeExtras()
    val extrasUncheckedCount: Flow<Int> = shoppingDao.observeExtrasUncheckedCount()

    /** Lignes agrégées : besoins de tous les repas à faire, moins le stock. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val shoppingLines: Flow<List<ShoppingLine>> =
        mealDao.observeTodo().flatMapLatest { meals ->
            // Recharger les détails à chaque changement de recettes ou de repas.
            recipeDao.observeAll().flatMapLatest {
                flow { emit(meals.map { it.meal.recipeId }.distinct().mapNotNull { recipeDao.getWithDetails(it) }.associateBy { it.recipe.id }) }
            }.combine(shoppingDao.observeChecks()) { details, checks -> details to checks.filter { it.checked }.map { it.key }.toSet() }
                .combine(stockDao.observeAll()) { (details, checked), stock ->
                    ShoppingAggregator.aggregate(meals, details, checked, stock.associate { it.key to StockLevel(it.quantity, it.always) })
                }
        }

    /**
     * « Courses terminées » : tout ce qui est coché entre dans le stock (la quantité restante à acheter,
     * telle qu'affichée), les coches sont effacées, les articles « Autres » cochés sont retirés de la liste.
     * Retourne le nombre d'articles entrés en stock.
     */
    suspend fun finishShopping(): Int {
        val lines = shoppingLines.first()
        var n = 0
        for (l in lines) {
            if (!l.checked || l.inStock) continue
            addToStock(l.key, l.name, l.toBuy, l.family, l.aisle)
            n++
        }
        for (e in shoppingDao.getExtras()) {
            if (!e.checked) continue
            val unit = e.unitEnum
            addToStock(ShoppingAggregator.keyOf(e.name, unit.family), e.name, e.quantity?.let { it * unit.factor }, unit.family, e.aisleEnum)
            shoppingDao.deleteExtra(e)
            n++
        }
        shoppingDao.clearChecks()
        return n
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
        val meals = mealDao.getAll()
        val extras = shoppingDao.getExtras()
        val stock = stockDao.getAll()
        return BackupCodec.encode(
            BackupFile(
                recipes = recipes.map { BackupCodec.toDto(it) },
                meals = meals.filter { it.meal.parentId == null }.map { p ->
                    MealDto(p.recipe.name, p.meal.servings, p.meal.done,
                        meals.filter { it.meal.parentId == p.meal.id }.map { MealDto(it.recipe.name, it.meal.servings, it.meal.done) })
                },
                extras = extras.map { BackupCodec.toDto(it) },
                stock = stock.map { StockDto(it.name, it.quantity, it.family, it.aisle, it.always) },
            )
        )
    }

    /** Importe un fichier JSON. Les recettes de même nom sont remplacées. Retourne le nombre de recettes importées. */
    suspend fun importBackup(text: String, replaceAll: Boolean): Int {
        val file = BackupCodec.decode(text)
        if (replaceAll) {
            mealDao.deleteAll()
            shoppingDao.deleteAllExtras()
            shoppingDao.clearChecks()
            stockDao.deleteAll()
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
        // Repas (nouveau format) + anciennes cases de planning (format d'avant), dans l'ordre du fichier.
        val legacy = file.plan.sortedWith(compareBy({ it.epochDay }, { it.slot })).map { MealDto(it.recipeName, it.servings, false) }
        for (m in file.meals + legacy) {
            val rid = byName[ShoppingAggregator.normalizeName(m.recipeName)]?.recipe?.id ?: continue
            val pid = mealDao.insert(Meal(recipeId = rid, servings = m.servings, position = mealDao.maxPosition() + 1, done = m.done,
                doneAt = if (m.done) System.currentTimeMillis() else null))
            for (sd in m.sides) {
                val sid = byName[ShoppingAggregator.normalizeName(sd.recipeName)]?.recipe?.id ?: continue
                mealDao.insert(Meal(recipeId = sid, servings = sd.servings, position = mealDao.maxPosition() + 1, done = m.done,
                    doneAt = if (m.done) System.currentTimeMillis() else null, parentId = pid))
            }
        }
        for (e in file.extras) shoppingDao.insertExtra(BackupCodec.fromDto(e))
        for (st in file.stock) {
            val fam = UnitFamily.entries.firstOrNull { it.name == st.family } ?: UnitFamily.NONE
            val key = ShoppingAggregator.keyOf(st.name, fam)
            addToStock(key, st.name, st.quantity, fam, Aisle.fromName(st.aisle))
            if (st.always) stockDao.getByKey(key)?.let { stockDao.update(it.copy(always = true)) }
        }
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
