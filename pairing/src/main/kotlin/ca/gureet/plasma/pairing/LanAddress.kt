// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.pairing

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

/** The TV's address on the home network (brief 5.15), which phones on the same Wi-Fi can reach. */
object LanAddress {
    /** The TV's private IPv4 address, Ethernet before Wi-Fi; null when it isn't on a network. */
    fun find(): Inet4Address? = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }
            .sortedBy { rank(it.name) }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
    } catch (error: SocketException) {
        null
    }

    private fun rank(name: String): Int = when {
        name.startsWith("eth") -> 0
        name.startsWith("wlan") -> 1
        else -> 2
    }
}
