package com.meshgen.app.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshgen.app.BuildConfig
import com.meshgen.app.ui.InfoRow
import com.meshgen.app.ui.Panel
import com.meshgen.app.ui.SubScreen
import java.util.Locale

@Composable
fun DeviceScreen(onBack: () -> Unit, vm: HomeViewModel = viewModel()) {
    val d by vm.state.collectAsStateWithLifecycle()
    val device = d.device
    SubScreen(title = "This device", onBack = onBack) {
        Panel {
            Text(device.tier.label, style = MaterialTheme.typography.titleLarge)
            Text(device.tier.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel {
            InfoRow("Phone", "${device.manufacturer} ${device.model}")
            InfoRow("Chip", device.socLabel)
            InfoRow("RAM", String.format(Locale.US, "%.1f GB", device.totalRamGb))
            InfoRow("CPU cores", device.cpuCores.toString())
            InfoRow("Vulkan GPU", if (device.vulkanSupported) "Yes" else "No")
            InfoRow("ABI", device.abis.firstOrNull() ?: "?")
            InfoRow("Android", "${device.androidVersion} (API ${device.sdkInt})")
            InfoRow("App version", BuildConfig.VERSION_NAME)
        }
        Text(
            "Accelerator checks (GPU/NPU delegates) are added when the first AI model is integrated.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
