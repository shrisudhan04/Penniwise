package com.smartexpensetracker

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.Telephony

/**
 * Scans SMS messages received within the requested number of previous days.
 * Detects both INCOME and EXPENSE using SmsParser.parse().
 *
 * Existing SMS hashes are skipped to avoid showing already-imported messages.
 *
 * This helper returns transactions for review; it does not insert them into the database.
 */
fun scanRecentSms(
    context: Context,
    existingHashes: Set<String>,
    lookbackDays: Int = 7
): List<ExpenseEntity> {

    require(lookbackDays > 0) { "lookbackDays must be greater than zero" }

    val cutoff = System.currentTimeMillis() -
            lookbackDays.toLong() * 24L * 60L * 60L * 1000L

    val projection = arrayOf(
        Telephony.Sms.DATE,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY
    )

    val selection = "${Telephony.Sms.DATE} >= ?"
    val selectionArgs = arrayOf(cutoff.toString())

    val detected = mutableListOf<ExpenseEntity>()
    val seen = existingHashes.toMutableSet()

    val cursor: Cursor? = context.contentResolver.query(
        Telephony.Sms.Inbox.CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        "${Telephony.Sms.DATE} DESC"
    )

    cursor?.use { c ->
        val dateIndex = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
        val senderIndex = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val bodyIndex = c.getColumnIndexOrThrow(Telephony.Sms.BODY)

        while (c.moveToNext()) {
            val date = c.getLong(dateIndex)
            val sender = c.getString(senderIndex).orEmpty()
            val body = c.getString(bodyIndex).orEmpty()

            val parsed = SmsParser.parse(sender, body) ?: continue

            if (!seen.add(parsed.hash)) continue

            detected += ExpenseEntity(
                amount = parsed.amount,
                merchant = parsed.merchant,
                receiver = parsed.receiver,
                category = parsed.category,
                transactionType = parsed.type,
                paymentMethod = "UPI / Bank SMS",
                transactionDate = date,
                smsSender = parsed.sender,
                smsHash = parsed.hash,
                notes = "Automatically detected from SMS",
                manuallyAdded = false
            )
        }
    }

    return detected
}
