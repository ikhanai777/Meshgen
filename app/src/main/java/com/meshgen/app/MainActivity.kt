package com.meshgen.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.toArgb
import com.meshgen.app.ui.navigation.MeshGenNavHost
import com.meshgen.app.ui.theme.MeshColors
import com.meshgen.app.ui.theme.MeshGenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bars = SystemBarStyle.dark(MeshColors.Graphite950.copy(alpha = 0f).toArgb())
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            MeshGenTheme {
                // Surface sets the default text/icon colour for every screen (light on graphite).
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                    MeshGenNavHost()
                }
            }
        }
    }
}
