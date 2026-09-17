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
    const val PING = "ping/{sessionId}?nodeIp={nodeIp}"
    const val GPS = "gps/{sessionId}"
    const val IPERF_PROFILES = "iperf_profiles/{nodeIp}"
    const val IPERF = "iperf/{nodeIp}?profileId={profileId}"
    const val SESSIONS = "sessions"
    const val EXPORT = "export/{sessionId}"

    fun ping(sessionId: String, nodeIp: String) = "ping/$sessionId?nodeIp=$nodeIp"
    fun gps(sessionId: String) = "gps/$sessionId"
    // iperf is a standalone test, not gated on the ping/GPS logging session being active - it's
    // keyed by the connected node's address, not a sessionId (see IperfSessionService).
    fun iperfProfiles(nodeIp: String) = "iperf_profiles/$nodeIp"
    fun iperf(nodeIp: String, profileId: Long = -1L) = "iperf/$nodeIp?profileId=$profileId"
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
                onOpenGps = { sessionId -> navController.navigate(ManetRoutes.gps(sessionId)) },
                onOpenIperf = { nodeIp -> navController.navigate(ManetRoutes.iperfProfiles(nodeIp)) },
                onOpenSessions = { navController.navigate(ManetRoutes.SESSIONS) },
                onOpenExport = { sessionId -> navController.navigate(ManetRoutes.export(sessionId)) },
                onOpenSettings = { navController.navigate(ManetRoutes.SETTINGS) },
            )
        }
        composable(ManetRoutes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = ManetRoutes.PING,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("nodeIp") { type = NavType.StringType; defaultValue = "" },
            ),
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
            arguments = listOf(navArgument("nodeIp") { type = NavType.StringType }),
        ) { backStackEntry ->
            val nodeIp = backStackEntry.arguments?.getString("nodeIp").orEmpty()
            IperfProfilesScreen(
                onRunProfile = { profileId -> navController.navigate(ManetRoutes.iperf(nodeIp, profileId)) },
                onRunAdHoc = { navController.navigate(ManetRoutes.iperf(nodeIp)) },
            )
        }
        composable(
            route = ManetRoutes.IPERF,
            arguments = listOf(
                navArgument("nodeIp") { type = NavType.StringType },
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
