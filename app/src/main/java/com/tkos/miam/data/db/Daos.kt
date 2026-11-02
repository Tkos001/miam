package com.tkos.miam.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.tkos.miam.data.model.ExtraItem
import com.tkos.miam.data.model.Ingredient
import com.tkos.miam.data.model.PlanEntry
import com.tkos.miam.data.model.PlanEntryWithRecipe
import com.tkos.miam.data.model.Recipe
import com.tkos.miam.data.model.RecipeWithDetails
import com.tkos.miam.data.model.ShoppingCheck
import com.tkos.miam.data.model.Step
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY isFavorite DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE isFavorite = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeFavorites(): Flow<List<Recipe>>

    @Query("SELECT COUNT(*) FROM recipes")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM recipes WHERE isFavorite = 1")
    fun observeFavoriteCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun count(): Int

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeWithDetails(id: Long): Flow<RecipeWithDetails?>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getWithDetails(id: Long): RecipeWithDetails?

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllWithDetails(): List<RecipeWithDetails>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: Long): Recipe?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: Recipe): Long

    @Update
    suspend fun updateRecipe(recipe: Recipe)

    @Query("UPDATE recipes SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipe(id: Long)

    @Query("DELETE FROM recipes")
    suspend fun deleteAllRecipes()

    @Insert
    suspend fun insertIngredients(items: List<Ingredient>)

    @Insert
    suspend fun insertSteps(items: List<Step>)

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredientsOf(recipeId: Long)

    @Query("DELETE FROM steps WHERE recipeId = :recipeId")
    suspend fun deleteStepsOf(recipeId: Long)

    /** Insère ou remplace une recette complète (ingrédients + étapes). Retourne l'id. */
    @Transaction
    suspend fun upsertFull(recipe: Recipe, ingredients: List<Ingredient>, steps: List<Step>): Long {
        val id = insertRecipe(recipe)
        deleteIngredientsOf(id)
        deleteStepsOf(id)
        insertIngredients(ingredients.mapIndexed { i, it -> it.copy(id = 0, recipeId = id, position = i) })
        insertSteps(steps.mapIndexed { i, it -> it.copy(id = 0, recipeId = id, position = i) })
        return id
    }
}

@Dao
interface PlanDao {
    @Transaction
    @Query("SELECT * FROM plan_entries WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, slot")
    fun observeRange(from: Long, to: Long): Flow<List<PlanEntryWithRecipe>>

    @Transaction
    @Query("SELECT * FROM plan_entries ORDER BY epochDay, slot")
    fun observeAll(): Flow<List<PlanEntryWithRecipe>>

    @Transaction
    @Query("SELECT * FROM plan_entries ORDER BY epochDay, slot")
    suspend fun getAll(): List<PlanEntryWithRecipe>

    @Query("SELECT COUNT(*) FROM plan_entries WHERE epochDay BETWEEN :from AND :to")
    fun observeCountInRange(from: Long, to: Long): Flow<Int>

    @Insert
    suspend fun insert(entry: PlanEntry): Long

    @Update
    suspend fun update(entry: PlanEntry)

    @Query("DELETE FROM plan_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM plan_entries WHERE epochDay BETWEEN :from AND :to")
    suspend fun deleteRange(from: Long, to: Long)

    @Query("DELETE FROM plan_entries")
    suspend fun deleteAll()
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM extra_items ORDER BY checked ASC, createdAt DESC")
    fun observeExtras(): Flow<List<ExtraItem>>

    @Query("SELECT * FROM extra_items")
    suspend fun getExtras(): List<ExtraItem>

    @Query("SELECT COUNT(*) FROM extra_items WHERE checked = 0")
    fun observeExtrasUncheckedCount(): Flow<Int>

    @Insert
    suspend fun insertExtra(item: ExtraItem): Long

    @Update
    suspend fun updateExtra(item: ExtraItem)

    @Delete
    suspend fun deleteExtra(item: ExtraItem)

    @Query("DELETE FROM extra_items WHERE checked = 1")
    suspend fun deleteCheckedExtras()

    @Query("DELETE FROM extra_items")
    suspend fun deleteAllExtras()

    @Query("SELECT * FROM shopping_checks")
    fun observeChecks(): Flow<List<ShoppingCheck>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheck(check: ShoppingCheck)

    @Query("DELETE FROM shopping_checks WHERE `key` = :key")
    suspend fun deleteCheck(key: String)

    @Query("DELETE FROM shopping_checks")
    suspend fun clearChecks()
}
