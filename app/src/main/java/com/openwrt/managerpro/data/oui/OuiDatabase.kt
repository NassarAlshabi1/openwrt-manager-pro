package com.openwrt.managerpro.data.oui

/**
 * Local MAC -> vendor (OUI) database.
 * Entries are loaded from assets/oui.txt (lines "XX:XX:XX,Vendor Name").
 * Unknown vendors are guessed from common hostnames.
 */
class OuiDatabase(entries: Map<String, String>) {

    private val map: Map<String, String> = entries.mapKeys { it.key.lowercase().trim() }

    fun lookup(mac: String): String? = map[MacUtils.normalizeMac(mac).take(8)]

    companion object {
        fun parse(lines: List<String>): Map<String, String> = buildMap {
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split(',', limit = 2)
                if (parts.size == 2 && parts[0].trim().isNotEmpty()) {
                    put(parts[0].trim().lowercase(), parts[1].trim())
                }
            }
        }
    }
}

object MacUtils {

    private val MAC_REGEX = Regex("^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")

    /** Normalize to lowercase colon-separated form; null when not a valid MAC. */
    fun normalizeMac(input: String): String {
        val hex = input.trim().lowercase().replace('-', ':').replace('.', ':')
        val cleaned = hex.replace(":", "")
        if (cleaned.length != 12 || cleaned.any { !it.isDigit() && it !in 'a'..'f' }) {
            return input.trim().lowercase()
        }
        val normalized = cleaned.chunked(2).joinToString(":")
        return if (MAC_REGEX.matches(normalized)) normalized else normalized
    }

    fun isValidMac(input: String): Boolean = MAC_REGEX.matches(normalizeMac(input))

    /** Best-effort vendor guess from hostname (used when OUI lookup misses). */
    fun vendorFromHostname(hostname: String?): String? {
        if (hostname.isNullOrBlank()) return null
        val h = hostname.lowercase()
        return when {
            "iphone" in h || "ipad" in h || "macbook" in h || "apple" in h || "imac" in h -> "Apple"
            "galaxy" in h || "samsung" in h -> "Samsung"
            "huawei" in h || "honor" in h -> "Huawei"
            "xiaomi" in h || "redmi" in h || "poco" in h -> "Xiaomi"
            "pixel" in h -> "Google"
            "nest" in h -> "Nest"
            "esp_" in h || "esp-" in h || "espressif" in h -> "Espressif"
            "raspberrypi" in h || "raspberry" in h -> "Raspberry Pi"
            "desktop-" in h || "windows" in h -> "Windows PC"
            "tv" == h || h.endsWith("-tv") || "bravia" in h -> "Smart TV"
            else -> null
        }
    }
}
