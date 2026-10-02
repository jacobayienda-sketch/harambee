package com.harambee.tracker.core

object Names {
    private val honorifics = setOf("mr", "mrs", "ms", "miss", "dr", "rev", "pst", "pastor", "prof", "eng", "hon", "co", "cec", "mama", "baba", "mzee")

    /** M-Pesa sends names in capitals ("JANE WANJIKU"); show them as "Jane Wanjiku". */
    fun titleCase(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }

    /** Titles kept in front of shortened names: "CO", "Mr", "Pastor"… */
    fun isTitle(word: String): Boolean = word.lowercase().trimEnd('.') in honorifics

    fun tokens(name: String): List<String> =
        name.lowercase()
            .split(Regex("[^\\p{L}0-9]+"))
            .filter { it.length > 1 && it !in honorifics }

    /**
     * Whether a name typed on a WhatsApp list ("CO Peter chesos") refers to the M-Pesa sender
     * ("PETER KIPRONO CHESOS"): every meaningful word on the list side must appear in the sender name.
     */
    fun matches(listName: String, mpesaName: String): Boolean {
        val listTokens = tokens(listName)
        val senderTokens = tokens(mpesaName).toSet()
        if (listTokens.isEmpty() || senderTokens.isEmpty()) return false
        if (listTokens.size == 1) {
            // A single word is only trusted if the sender name is also a single word.
            return senderTokens.size == 1 && listTokens[0] in senderTokens
        }
        return listTokens.all { it in senderTokens }
    }

    fun normalized(name: String): String = tokens(name).joinToString(" ")
}
