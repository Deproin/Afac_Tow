package com.example.util

object PhoneUtils {
    /**
     * Formats a phone number for the WhatsApp API URL.
     * Ensures country code is present (defaults to 967 for Yemen if local),
     * and removes the "+" sign as the WhatsApp API prefers numbers without it (e.g., 96777XXXXXXX).
     */
    fun formatForWhatsApp(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        
        // Remove spaces, dashes, parentheses
        var cleaned = phone.replace(Regex("[\\s\\-\\(\\)]"), "")
        
        // Convert "00" prefix to "+"
        if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.substring(2)
        }
        
        // If it starts with "+", remove it for WhatsApp API URL
        if (cleaned.startsWith("+")) {
            return cleaned.substring(1)
        }
        
        // At this point, there is no "+" sign.
        // Check for local Yemeni number (9 digits starting with 7)
        if (cleaned.length == 9 && cleaned.startsWith("7")) {
            return "967$cleaned"
        }
        
        // Check for local Saudi number (10 digits starting with 05)
        if (cleaned.length == 10 && cleaned.startsWith("05")) {
            return "966${cleaned.substring(1)}"
        }
        
        // Return whatever is left (user might have entered country code without + like 967...)
        return cleaned
    }

    /**
     * Formats a phone number for standard SMS Intent (smsto:).
     * Keeps the "+" sign if present, defaults to +967 for local Yemeni numbers.
     */
    fun formatForSms(phone: String?): String {
        val waFormatted = formatForWhatsApp(phone)
        if (waFormatted.isEmpty()) return ""
        return "+$waFormatted" // SMS intents work best with the proper + sign to ensure correct routing
    }
}
