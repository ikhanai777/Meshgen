package com.meshgen.app.device

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTierTest {
    private val gib = 1024L * 1024L * 1024L

    @Test
    fun `4 GB phone is entry tier`() {
        assertEquals(DeviceTier.ENTRY, DeviceProfile.classifyTier((3.6 * gib).toLong()))
    }

    @Test
    fun `6 and 8 GB phones are mid tier`() {
        // Android reports less than the advertised RAM.
        assertEquals(DeviceTier.MID, DeviceProfile.classifyTier((5.4 * gib).toLong()))
        assertEquals(DeviceTier.MID, DeviceProfile.classifyTier((7.3 * gib).toLong()))
    }

    @Test
    fun `12 GB phone is high tier`() {
        assertEquals(DeviceTier.HIGH, DeviceProfile.classifyTier((11.2 * gib).toLong()))
    }
}
