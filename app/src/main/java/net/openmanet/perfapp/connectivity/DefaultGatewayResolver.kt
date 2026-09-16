package net.openmanet.perfapp.connectivity

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the default gateway address off the currently active network's route table - used to
 * prefill the node-address field, since the user is expected to have already joined the mesh
 * Wi-Fi manually before opening the app, at which point the mesh node is almost always also the
 * gateway (see the networking docs: mesh gateways sit at addresses like 10.41.1.1).
 */
@Singleton
class DefaultGatewayResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun currentGatewayAddress(): String? {
        val network = connectivityManager.activeNetwork ?: return null
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return null
        return linkProperties.routes.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
    }
}
