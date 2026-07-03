package com.github.tvbox.newbox

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.github.tvbox.newbox.domain.VodItem
import com.github.tvbox.newbox.feature.detail.DetailScreen
import com.github.tvbox.newbox.feature.home.HomeScreen
import com.github.tvbox.newbox.feature.player.PlayerScreen
import com.github.tvbox.newbox.feature.search.SearchScreen
import com.github.tvbox.newbox.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object SearchRoute
@Serializable data class SearchRouteWithQuery(val query: String)
@Serializable object SettingsRoute
@Serializable data class DetailRoute(
    val id: String,
    val name: String,
    val pic: String = "",
    val note: String = "",
    val type: String = "",
    val sourceKey: String = "",
)
@Serializable data class PlayerRoute(
    val flag: String,
    val playUrl: String,
    val sourceKey: String,
    val title: String = "",
)

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
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onVodClick = ::openVod,
                onSearchClick = { navController.navigate(SearchRoute) },
                onSettingsClick = { navController.navigate(SettingsRoute) },
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
            DetailScreen(
                vodItem = vodItem,
                onBackClick = { navController.popBackStack() },
                onPlayClick = { flag, url, sourceKey ->
                    navController.navigate(PlayerRoute(flag, url, sourceKey, route.name))
                },
            )
        }
        composable<PlayerRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PlayerRoute>()
            PlayerScreen(
                flag = route.flag,
                playUrl = route.playUrl,
                sourceKey = route.sourceKey,
                title = route.title,
                onBackClick = { navController.popBackStack() },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
            )
        }
    }
}
