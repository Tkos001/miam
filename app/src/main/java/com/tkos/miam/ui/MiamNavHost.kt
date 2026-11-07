package com.tkos.miam.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tkos.miam.data.MiamRepository
import com.tkos.miam.ui.screens.FindRecipesScreen
import com.tkos.miam.ui.screens.HomeScreen
import com.tkos.miam.ui.screens.MealsScreen
import com.tkos.miam.ui.screens.RecipeDetailScreen
import com.tkos.miam.ui.screens.RecipeEditScreen
import com.tkos.miam.ui.screens.RecipeListScreen
import com.tkos.miam.ui.screens.SettingsScreen
import com.tkos.miam.ui.screens.ShoppingScreen
import com.tkos.miam.ui.screens.StockScreen

object Routes {
    const val HOME = "home"
    const val RECIPES = "recipes?favorites={favorites}"
    const val RECIPE = "recipe/{id}"
    const val EDIT = "edit/{id}"
    const val MEALS = "meals"
    const val SHOPPING = "shopping"
    const val STOCK = "stock"
    const val SETTINGS = "settings"
    const val FIND = "find"

    fun recipes(favoritesOnly: Boolean = false) = "recipes?favorites=$favoritesOnly"
    fun recipe(id: Long) = "recipe/$id"
    fun edit(id: Long = -1L) = "edit/$id"
}

@Composable
fun MiamNavHost(repository: MiamRepository, navController: NavHostController = rememberNavController()) {
    val dur = 260
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { slideInHorizontally(tween(dur)) { it / 4 } + fadeIn(tween(dur)) },
        exitTransition = { fadeOut(tween(dur)) },
        popEnterTransition = { fadeIn(tween(dur)) },
        popExitTransition = { slideOutHorizontally(tween(dur)) { it / 4 } + fadeOut(tween(dur)) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                repository = repository,
                onOpenRecipes = { navController.navigate(Routes.recipes(false)) },
                onOpenFavorites = { navController.navigate(Routes.recipes(true)) },
                onOpenPlanner = { navController.navigate(Routes.MEALS) },
                onOpenShopping = { navController.navigate(Routes.SHOPPING) },
                onOpenFind = { navController.navigate(Routes.FIND) },
                onOpenStock = { navController.navigate(Routes.STOCK) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenRecipe = { navController.navigate(Routes.recipe(it)) },
                onNewRecipe = { navController.navigate(Routes.edit()) },
            )
        }
        composable(
            Routes.RECIPES,
            arguments = listOf(navArgument("favorites") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            RecipeListScreen(
                repository = repository,
                favoritesOnly = entry.arguments?.getBoolean("favorites") ?: false,
                onBack = { navController.popBackStack() },
                onOpenRecipe = { navController.navigate(Routes.recipe(it)) },
                onNewRecipe = { navController.navigate(Routes.edit()) },
            )
        }
        composable(Routes.RECIPE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            RecipeDetailScreen(
                repository = repository,
                recipeId = id,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.edit(id)) },
                onOpenPlanner = { navController.navigate(Routes.MEALS) },
            )
        }
        composable(Routes.EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            RecipeEditScreen(
                repository = repository,
                recipeId = id.takeIf { it >= 0 },
                onBack = { navController.popBackStack() },
                onSaved = { savedId ->
                    navController.popBackStack()
                    if (id < 0) navController.navigate(Routes.recipe(savedId))
                },
            )
        }
        composable(Routes.MEALS) {
            MealsScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenRecipe = { navController.navigate(Routes.recipe(it)) },
                onOpenShopping = { navController.navigate(Routes.SHOPPING) },
            )
        }
        composable(Routes.SHOPPING) {
            ShoppingScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenMeals = { navController.navigate(Routes.MEALS) },
                onOpenStock = { navController.navigate(Routes.STOCK) },
            )
        }
        composable(Routes.STOCK) {
            StockScreen(repository = repository, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(repository = repository, onBack = { navController.popBackStack() })
        }
        composable(Routes.FIND) {
            FindRecipesScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
    }
}
