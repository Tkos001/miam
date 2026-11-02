package com.tkos.miam.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tkos.miam.data.model.ExtraItem
import com.tkos.miam.data.model.Ingredient
import com.tkos.miam.data.model.PlanEntry
import com.tkos.miam.data.model.Recipe
import com.tkos.miam.data.model.ShoppingCheck
import com.tkos.miam.data.model.Step

@Database(
    entities = [Recipe::class, Ingredient::class, Step::class, PlanEntry::class, ExtraItem::class, ShoppingCheck::class],
    version = 1,
    exportSchema = true,
)
abstract class MiamDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun planDao(): PlanDao
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile private var instance: MiamDatabase? = null

        fun get(context: Context): MiamDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, MiamDatabase::class.java, "miam.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
