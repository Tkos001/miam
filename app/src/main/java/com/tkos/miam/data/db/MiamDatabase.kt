package com.tkos.miam.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tkos.miam.data.model.ExtraItem
import com.tkos.miam.data.model.Ingredient
import com.tkos.miam.data.model.PlanEntry
import com.tkos.miam.data.model.Recipe
import com.tkos.miam.data.model.RecipePhoto
import com.tkos.miam.data.model.ShoppingCheck
import com.tkos.miam.data.model.Step

@Database(
    entities = [Recipe::class, Ingredient::class, Step::class, PlanEntry::class, ExtraItem::class, ShoppingCheck::class, RecipePhoto::class],
    version = 2,
    exportSchema = true,
)
abstract class MiamDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun planDao(): PlanDao
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile private var instance: MiamDatabase? = null

        /** v1 → v2 : table des photos ; l'image existante de chaque recette devient sa première photo. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recipe_photos` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recipeId` INTEGER NOT NULL, `path` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_photos_recipeId` ON `recipe_photos` (`recipeId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recipe_photos_recipeId_path` ON `recipe_photos` (`recipeId`, `path`)")
                db.execSQL(
                    "INSERT INTO recipe_photos (recipeId, path, source, position, createdAt) " +
                        "SELECT id, imagePath, CASE WHEN imagePath LIKE '%/catalog-%' THEN 'CATALOG' ELSE 'USER' END, 0, createdAt " +
                        "FROM recipes WHERE imagePath IS NOT NULL"
                )
            }
        }

        fun get(context: Context): MiamDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, MiamDatabase::class.java, "miam.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { instance = it }
        }
    }
}
