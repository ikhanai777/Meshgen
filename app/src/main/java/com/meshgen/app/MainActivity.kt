package com.meshgen.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
            MeshGenTheme { MeshGenNavHost() }
        }
    }
}
