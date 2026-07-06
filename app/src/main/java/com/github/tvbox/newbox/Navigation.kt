package com.github.tvbox.newbox

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.github.tvbox.newbox.domain.VodItem
import com.github.tvbox.newbox.feature.detailplayer.DetailPlayerScreen
import com.github.tvbox.newbox.feature.home.HomeScreen
import com.github.tvbox.newbox.feature.mine.MineScreen
import com.github.tvbox.newbox.feature.search.SearchScreen
import com.github.tvbox.newbox.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object SearchRoute
@Serializable data class SearchRouteWithQuery(val query: String)
@Serializable object SettingsRoute
@Serializable object MineRoute
@Serializable data class DetailRoute(
    val id: String,
    val name: String,
    val pic: String = "",
    val note: String = "",
    val type: String = "",
    val sourceKey: String = "",
)

private const val ANIM_DURATION = 300

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    fun openVod(vod: VodItem) {
        if (vod.id.isBlank() || vod.id.startsWith("msearch:")) {
            navController.navigate(SearchRouteWithQuery(vod.name))
        } else {
            navController.navigate(DetailRoute(vod.id, vod.name, vod.pic, vod.note, vod.type, vod.sourceKey))
        }
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(ANIM_DURATION)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(ANIM_DURATION)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(ANIM_DURATION)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(ANIM_DURATION)) },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onVodClick = ::openVod,
                onSearchClick = { navController.navigate(SearchRoute) },
                onMineClick = { navController.navigate(MineRoute) },
                onSubscriptionClick = { navController.navigate(SettingsRoute) },
            )
        }
        composable<SearchRoute> {
            SearchScreen(
                onVodClick = ::openVod,
                onBackClick = { navController.popBackStack() },
            )
        }
        composable<SearchRouteWithQuery> { backStackEntry ->
            val route = backStackEntry.toRoute<SearchRouteWithQuery>()
            SearchScreen(
                initialQuery = route.query,
                onVodClick = ::openVod,
                onBackClick = { navController.popBackStack() },
            )
        }
        composable<DetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<DetailRoute>()
            val vodItem = VodItem(
                id = route.id,
                name = route.name,
                pic = route.pic,
                note = route.note,
                type = route.type,
                sourceKey = route.sourceKey,
            )
            DetailPlayerScreen(
                vodItem = vodItem,
                onBackClick = { navController.popBackStack() },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
            )
        }
        composable<MineRoute> {
            MineScreen(
                onBackClick = { navController.popBackStack() },
                onSubscriptionClick = { navController.navigate(SettingsRoute) },
            )
        }
    }
}
