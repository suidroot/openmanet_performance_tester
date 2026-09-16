package net.openmanet.perfapp.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.openmanet.perfapp.connectivity.ConnectionState
import net.openmanet.perfapp.ui.dashboard.DashboardScreen
import net.openmanet.perfapp.ui.export.ExportScreen
import net.openmanet.perfapp.ui.gps.GpsScreen
import net.openmanet.perfapp.ui.iperf.IperfProfilesScreen
import net.openmanet.perfapp.ui.iperf.IperfScreen
import net.openmanet.perfapp.ui.nodeselect.NodeSelectScreen
import net.openmanet.perfapp.ui.ping.PingScreen
import net.openmanet.perfapp.ui.sessions.SessionListScreen
import net.openmanet.perfapp.ui.settings.SettingsScreen

object ManetRoutes {
    const val NODE_SELECT = "node_select"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val PING = "ping/{sessionId}"
    const val GPS = "gps/{sessionId}"
    const val IPERF_PROFILES = "iperf_profiles/{sessionId}"
    const val IPERF = "iperf/{sessionId}?profileId={profileId}"
    const val SESSIONS = "sessions"
    const val EXPORT = "export/{sessionId}"

    fun ping(sessionId: String) = "ping/$sessionId"
    fun gps(sessionId: String) = "gps/$sessionId"
    fun iperfProfiles(sessionId: String) = "iperf_profiles/$sessionId"
    fun iperf(sessionId: String, profileId: Long = -1L) = "iperf/$sessionId?profileId=$profileId"
    fun export(sessionId: String) = "export/$sessionId"
}

/** Route the ConnectionState machine is currently "at". An Error stays on the step it failed at. */
private fun ConnectionState.route(): String = when (this) {
    is ConnectionState.EnteringNodeAddress -> ManetRoutes.NODE_SELECT
    is ConnectionState.Connecting -> ManetRoutes.NODE_SELECT
    is ConnectionState.Connected -> ManetRoutes.DASHBOARD
    is ConnectionState.Error -> previous.route()
}

@Composable
fun ManetNavHost(navController: NavHostController = rememberNavController()) {
    val connectionViewModel: ConnectionViewModel = hiltViewModel()
    val connectionState by connectionViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(connectionState) {
        val target = connectionState.route()
        if (navController.currentDestination?.route != target) {
            navController.navigate(target) {
                // The connection flow is linear and one-directional (EnteringNodeAddress ->
                // Connecting -> Connected); popUpTo NODE_SELECT keeps back-stack from growing
                // across retries and reconnects, except when landing on node_select itself
                // (disconnect), which has nothing earlier to pop to.
                if (target != ManetRoutes.NODE_SELECT) {
                    popUpTo(ManetRoutes.NODE_SELECT) { inclusive = false }
                } else {
                    popUpTo(ManetRoutes.NODE_SELECT) { inclusive = true }
                }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = ManetRoutes.NODE_SELECT) {
        composable(ManetRoutes.NODE_SELECT) {
            NodeSelectScreen(
                viewModel = connectionViewModel,
                onOpenSettings = { navController.navigate(ManetRoutes.SETTINGS) },
            )
        }
        composable(ManetRoutes.DASHBOARD) {
            DashboardScreen(
                connectionViewModel = connectionViewModel,
                onOpenPing = { sessionId -> navController.navigate(ManetRoutes.ping(sessionId)) },
                onOpenGps = { sessionId -> navController.navigate(ManetRoutes.gps(sessionId)) },
                onOpenIperf = { sessionId -> navController.navigate(ManetRoutes.iperfProfiles(sessionId)) },
                onOpenSessions = { navController.navigate(ManetRoutes.SESSIONS) },
            )
        }
        composable(ManetRoutes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = ManetRoutes.PING,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) {
            PingScreen()
        }
        composable(
            route = ManetRoutes.GPS,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) {
            GpsScreen()
        }
        composable(
            route = ManetRoutes.IPERF_PROFILES,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
            IperfProfilesScreen(
                onRunProfile = { profileId -> navController.navigate(ManetRoutes.iperf(sessionId, profileId)) },
                onRunAdHoc = { navController.navigate(ManetRoutes.iperf(sessionId)) },
            )
        }
        composable(
            route = ManetRoutes.IPERF,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("profileId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            IperfScreen()
        }
        composable(ManetRoutes.SESSIONS) {
            SessionListScreen(
                onOpenSession = { sessionId -> navController.navigate(ManetRoutes.export(sessionId)) },
            )
        }
        composable(
            route = ManetRoutes.EXPORT,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) {
            ExportScreen()
        }
    }
}
