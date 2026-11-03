package com.smartexpensetracker

import java.security.MessageDigest
import java.util.Locale

data class ParsedSms(
    val amount: Double,
    val merchant: String,
    val receiver: String,
    val type: String,
    val category: String,
    val sender: String,
    val hash: String
)

object SmsParser {

    // Handles formats such as "Rs. 1.00", "Rs 700.00", "INR 4.17", and "₹1,200.00".
    private val amountRegex = Regex(
        """(?i)(?:INR|Rs\.?|₹)\s*([0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)"""
    )

    private val debitPattern = Regex(
        """(?i)\bdebited\b"""
    )
    private val creditPattern = Regex(
        """(?i)\bcredited\b"""
    )

    // Bank SMS can identify a recipient as "B EARAATHI credited".
    // Prefer this name over later "SMS BLOCK ... to 9215676766" reference text.
    private val debitBeneficiaryCreditedPattern = Regex(
        """(?i)(?:^|[.;]\s*)(?:B\s+)?([A-Z][A-Z .&'-]{1,40}?)\s+credited\b"""
    )

    // Common format: "debited ... to SIVADEEPIKA P info: ..."
    private val debitReceiverPattern = Regex(
        """(?i)\bto\s+([A-Z][A-Z .&'-]{1,40}?)(?=\s+(?:info\s*:|UPI\s*:|Avl(?:ailable)?\s+Bal\b|available\s+balance\b|SMS\s+BLOCK\b)|[.;]|$)"""
    )

    // Image 2 format: "... credited with Rs 700.00 on ... from SIVADEEPIKA P. UPI:..."
    private val creditSourcePattern = Regex(
        """(?i)\bfrom\s+(.+?)(?=\s+(?:UPI\s*:|IMPS\b|NEFT\b|RTGS\b|Ref(?:erence)?\s*(?:No|Number)?\b)|[.;]|$)"""
    )

    private val failedWords = listOf(
        "transaction failed",
        "transaction declined",
        "payment failed",
        "declined",
        "reversed",
        "otp",
        "one time password",
        "offer",
        "promo"
    )

    fun parse(sender: String, body: String): ParsedSms? {
        val normalized = body.lowercase(Locale.ROOT)

        if (failedWords.any { normalized.contains(it) }) return null

        val type = when {
            debitPattern.containsMatchIn(body) -> "EXPENSE"
            creditPattern.containsMatchIn(body) -> "INCOME"
            else -> return null
        }

        val amountMatch = amountRegex.find(body) ?: return null
        val amount = amountMatch.groupValues[1]
            .replace(",", "")
            .toDoubleOrNull()
            ?: return null

        if (amount <= 0.0) return null

        val party = when (type) {
            "EXPENSE" -> debitBeneficiaryCreditedPattern.find(body)?.groupValues?.get(1)
                ?: debitReceiverPattern.find(body)?.groupValues?.get(1)
            "INCOME" -> creditSourcePattern.find(body)?.groupValues?.get(1)
            else -> null
        }?.trim()
            ?.trimEnd('.', ',', ';', ':')
            ?.takeIf { it.isNotBlank() }

        val partyName = party ?: sender.trim().ifBlank { "Unknown" }
        val category = if (type == "INCOME") {
            "Income"
        } else {
            classifyExpense(partyName, body)
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest((sender + body).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        return ParsedSms(
            amount = amount,
            merchant = partyName,
            receiver = partyName,
            type = type,
            category = category,
            sender = sender,
            hash = digest
        )
    }

    // Compatibility wrapper for MainActivity code that calls classify(merchant, body, type).
    fun classify(merchant: String, body: String, type: String = "EXPENSE"): String {
        return if (type.equals("INCOME", ignoreCase = true)) {
            "Income"
        } else {
            classifyExpense(merchant, body)
        }
    }

    private fun classifyExpense(merchant: String, body: String): String {
        val text = (merchant + " " + body).lowercase(Locale.ROOT)

        return when {
            listOf("swiggy", "zomato", "restaurant", "food", "cafe").any(text::contains) ->
                "Food"
            listOf("uber", "ola", "rapido", "fuel", "petrol", "diesel", "bus", "train").any(text::contains) ->
                "Transport"
            listOf("amazon", "flipkart", "shopping", "myntra").any(text::contains) ->
                "Shopping"
            listOf("electricity", "water bill", "recharge", "broadband", "mobile bill").any(text::contains) ->
                "Bills"
            else -> "Other"
        }
    }
}
