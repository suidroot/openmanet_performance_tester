package net.openmanet.perfapp.ui.nodeselect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.openmanet.perfapp.connectivity.ConnectionState
import net.openmanet.perfapp.connectivity.PendingNode
import net.openmanet.perfapp.data.entities.NodeProfile
import net.openmanet.perfapp.ui.nav.ConnectionViewModel

/**
 * The app does not join Wi-Fi - the user connects to the mesh SSID themselves via system
 * settings before opening the app. This screen needs the node's address (prefilled from the
 * phone's current default-gateway route, which on OpenManet is normally the node itself) plus
 * the device's real admin credentials (PAM-backed, same as the OpenWrt/LuCI login) - openmanetd
 * rejects unauthenticated API calls with 401, confirmed against a real node.
 */
@Composable
fun NodeSelectScreen(
    viewModel: ConnectionViewModel,
    onOpenSettings: () -> Unit,
) {
    val savedNodes by viewModel.savedNodes.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error = (state as? ConnectionState.Error)?.message
    // Only the live Connecting state disables the form - once it's wrapped in Error, the form
    // must re-enable so the user can edit fields and tap Connect again.
    val isConnecting = state is ConnectionState.Connecting

    var displayName by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var prefilled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!prefilled) {
            viewModel.suggestedNodeAddress()?.let { ip = it }
            prefilled = true
        }
    }

    fun fillFrom(profile: NodeProfile) {
        displayName = profile.displayName
        ip = profile.ipAddress
        username = profile.lastUsername.orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect to OpenManet Node") },
                actions = { TextButton(onClick = onOpenSettings) { Text("Settings") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (savedNodes.isNotEmpty()) {
                LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                    items(savedNodes, key = { it.ipAddress }) { profile ->
                        ListItem(
                            headlineContent = { Text(profile.displayName) },
                            supportingContent = { Text(profile.ipAddress) },
                            modifier = Modifier.fillMaxWidth().clickable { fillFrom(profile) },
                        )
                        HorizontalDivider()
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Make sure you're connected to the mesh Wi-Fi network before continuing.")
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name (optional)") },
                    enabled = !isConnecting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Node address") },
                    enabled = !isConnecting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    enabled = !isConnecting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isConnecting,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        viewModel.connect(
                            PendingNode(ip = ip, displayName = displayName.ifBlank { ip }),
                            username,
                            password,
                        )
                    },
                    enabled = !isConnecting && ip.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isConnecting) "Connecting…" else "Connect")
                }
            }
        }
    }
}
