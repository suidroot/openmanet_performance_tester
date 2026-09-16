package net.openmanet.perfapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import net.openmanet.perfapp.ui.nav.ManetNavHost
import net.openmanet.perfapp.ui.theme.ManetPerfAppTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ManetPerfAppTheme {
                ManetNavHost()
            }
        }
    }
}
