package com.meshgen.app.device

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

enum class DeviceTier(val label: String, val summary: String) {
    ENTRY("Entry", "Under 6 GB RAM. Text-to-shape may run at reduced resolution; photo and scan engines are not recommended."),
    MID("Mid-range", "Text-to-shape runs well. Photo and scan engines are experimental and may be slow."),
    HIGH("Flagship", "All engines supported. Photo and scan engines are still experimental."),
}

data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val socManufacturer: String?,
    val socModel: String?,
    val totalRamBytes: Long,
    val cpuCores: Int,
    val abis: List<String>,
    val vulkanSupported: Boolean,
    val androidVersion: String,
    val sdkInt: Int,
) {
    val totalRamGb: Double get() = totalRamBytes / (1024.0 * 1024.0 * 1024.0)
    val tier: DeviceTier get() = classifyTier(totalRamBytes)
    val socLabel: String
        get() = listOfNotNull(socManufacturer, socModel)
            .filter { it.isNotBlank() && it != Build.UNKNOWN }
            .joinToString(" ")
            .ifBlank { "Unknown SoC" }

    companion object {
        private const val GIB = 1024L * 1024L * 1024L

        /**
         * Android reports usable RAM, which is a bit below the advertised size
         * (an "8 GB" phone typically reports ~7.3 GiB), so thresholds sit below the marketing numbers.
         */
        fun classifyTier(totalRamBytes: Long): DeviceTier = when {
            totalRamBytes < 5L * GIB -> DeviceTier.ENTRY
            totalRamBytes < 10L * GIB -> DeviceTier.MID
            else -> DeviceTier.HIGH
        }

        fun detect(context: Context): DeviceProfile {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
            val (socMfr, socModel) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MANUFACTURER to Build.SOC_MODEL
            } else {
                null to Build.HARDWARE
            }
            return DeviceProfile(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                socManufacturer = socMfr,
                socModel = socModel,
                totalRamBytes = mem.totalMem,
                cpuCores = Runtime.getRuntime().availableProcessors(),
                abis = Build.SUPPORTED_ABIS.toList(),
                vulkanSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL),
                androidVersion = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
            )
        }
    }
}
