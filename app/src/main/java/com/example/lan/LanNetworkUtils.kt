package com.example.lan

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

/**
 * Wi-Fi LAN IPv4 discovery and RFC1918 bind-host validation.
 *
 * Guarantees:
 * - Never binds to `0.0.0.0`, `::`, public routable IPs, or cellular interfaces (`rmnet*`, `ccmni*`).
 * - Strictly accepts private RFC1918 IPv4 addresses (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`).
 */
object LanNetworkUtils {

    private val WIFI_IFACE_PREFIXES = listOf("wlan", "wifi", "swlan", "ap")
    private val CELLULAR_IFACE_PREFIXES = listOf("rmnet", "ccmni", "pdp", "wwan", "clat", "tun", "ppp")

    /**
     * Returns `true` iff [host] is a syntactically valid private RFC1918 IPv4 address:
     * - `10.0.0.0/8`
     * - `172.16.0.0/12` (`172.16.0.0` .. `172.31.255.255`)
     * - `192.168.0.0/16`
     */
    fun isPrivateRfc1918Ipv4(host: String): Boolean {
        val parts = host.trim().split('.')
        if (parts.size != 4) return false
        val octets = IntArray(4)
        for (i in 0 until 4) {
            val s = parts[i]
            if (s.isEmpty() || s.length > 3 || !s.all { it in '0'..'9' }) return false
            if (s.length > 1 && s[0] == '0') return false
            val v = s.toIntOrNull() ?: return false
            if (v !in 0..255) return false
            octets[i] = v
        }
        val a = octets[0]
        val b = octets[1]
        return when {
            a == 10 -> true
            a == 172 && b in 16..31 -> true
            a == 192 && b == 168 -> true
            else -> false
        }
    }

    /**
     * Validates that [host] is safe for binding the embedded Ktor CIO server.
     * Production ALWAYS requires [allowLoopbackForTests] = `false` (RFC1918 Wi-Fi IPv4 only).
     */
    fun isValidLanBindHost(host: String, allowLoopbackForTests: Boolean = false): Boolean {
        val trimmed = host.trim()
        if (trimmed.isEmpty() || trimmed == "0.0.0.0" || trimmed == "::" || trimmed == "*") {
            return false
        }
        if (allowLoopbackForTests && trimmed == "127.0.0.1") {
            return true
        }
        return isPrivateRfc1918Ipv4(trimmed)
    }

    /**
     * Checks whether the device currently has an active Wi-Fi transport.
     */
    fun isWifiConnected(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Resolves the active device's private RFC1918 Wi-Fi IPv4 address, or `null` if Wi-Fi
     * is disconnected or no RFC1918 address is assigned.
     */
    fun findActiveWifiLanIpv4(context: Context): String? {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null

        // 1. Prefer ConnectivityManager LinkProperties on active TRANSPORT_WIFI network
        val candidateNetworks = buildList {
            cm.activeNetwork?.let { add(it) }
            @Suppress("DEPRECATION")
            cm.allNetworks.forEach { if (it !in this) add(it) }
        }
        for (network in candidateNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val linkProps = cm.getLinkProperties(network) ?: continue
            for (linkAddr in linkProps.linkAddresses) {
                val addr = linkAddr.address
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val ip = addr.hostAddress ?: continue
                    if (isPrivateRfc1918Ipv4(ip)) {
                        return ip
                    }
                }
            }
        }

        // 2. Fallback: enumerate NetworkInterfaces restricted strictly to Wi-Fi interface names
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.orEmpty().lowercase(Locale.ROOT)
                if (CELLULAR_IFACE_PREFIXES.any { name.startsWith(it) }) continue
                if (WIFI_IFACE_PREFIXES.none { name.startsWith(it) }) continue
                for (addr in iface.inetAddresses.toList()) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (isPrivateRfc1918Ipv4(ip)) {
                            return@runCatching ip
                        }
                    }
                }
            }
            null
        }.getOrNull()
    }
}
