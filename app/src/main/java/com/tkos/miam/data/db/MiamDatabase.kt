package com.tkos.miam.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tkos.miam.data.model.ExtraItem
import com.tkos.miam.data.model.Ingredient
import com.tkos.miam.data.model.Meal
import com.tkos.miam.data.model.Recipe
import com.tkos.miam.data.model.RecipePhoto
import com.tkos.miam.data.model.ShoppingCheck
import com.tkos.miam.data.model.StockItem
import com.tkos.miam.data.model.Step

@Database(
    entities = [Recipe::class, Ingredient::class, Step::class, Meal::class, StockItem::class, ExtraItem::class, ShoppingCheck::class, RecipePhoto::class],
    version = 4,
    exportSchema = true,
)
abstract class MiamDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun mealDao(): MealDao
    abstract fun stockDao(): StockDao
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN protein TEXT NOT NULL DEFAULT 'AUTRE'")
            }
        }

        /** v3 → v4 : le planning daté devient une liste de repas ordonnée ; table du stock. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `meals` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recipeId` INTEGER NOT NULL, `servings` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, `done` INTEGER NOT NULL, `doneAt` INTEGER, `createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_recipeId` ON `meals` (`recipeId`)")
                val today = java.time.LocalDate.now().toEpochDay()
                // Les cases d'aujourd'hui et à venir, dans l'ordre des dates ; les passées sont abandonnées.
                db.execSQL(
                    "INSERT INTO meals (recipeId, servings, position, done, doneAt, createdAt) " +
                        "SELECT p.recipeId, p.servings, " +
                        "(SELECT COUNT(*) FROM plan_entries q WHERE q.epochDay >= $today AND (q.epochDay < p.epochDay OR (q.epochDay = p.epochDay AND q.id <= p.id))), " +
                        "0, NULL, ${System.currentTimeMillis()} FROM plan_entries p WHERE p.epochDay >= $today ORDER BY p.epochDay, p.slot, p.id"
                )
                db.execSQL("DROP TABLE IF EXISTS `plan_entries`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stock_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `key` TEXT NOT NULL, `name` TEXT NOT NULL, `quantity` REAL, " +
                        "`family` TEXT NOT NULL, `aisle` TEXT NOT NULL, `always` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_stock_items_key` ON `stock_items` (`key`)")
            }
        }

        fun get(context: Context): MiamDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, MiamDatabase::class.java, "miam.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { instance = it }
        }
    }
}
