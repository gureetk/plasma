// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only this TV's own adb services count (brief 4.24). */
class AdbServicesTest {
    private val own = listOf(InetAddress.getByName("192.168.1.42"), InetAddress.getByName("fe80::1"))

    @Test fun anAddressOfThisTvIsIt() {
        assertTrue(AdbServices.isThisTv(InetAddress.getByName("192.168.1.42")) { own })
        assertTrue(AdbServices.isThisTv(InetAddress.getByName("127.0.0.1")) { own })
    }

    @Test fun anotherDeviceOnTheNetworkIsnt() {
        assertFalse(AdbServices.isThisTv(InetAddress.getByName("192.168.1.77")) { own })
    }
}
