package com.smartexpensetracker


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IncomingSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        // Do not process SMS unless the user enabled SMS tracking.
        val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

        if (!settings.getBoolean("sms_tracking", false)) {
            return
        }

        // Keep the receiver alive while the database operation runs.
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

                if (messages.isEmpty()) return@launch

                // Combine message parts when an SMS is multipart.
                val sender = messages.firstOrNull()
                    ?.originatingAddress
                    .orEmpty()

                val body = messages.joinToString(separator = "") {
                    it.messageBody.orEmpty()
                }

                val timestamp = messages.firstOrNull()?.timestampMillis
                    ?: System.currentTimeMillis()

                // Analyze SMS for a transaction pattern.
                val parsed = SmsParser.parse(sender, body)
                    ?: return@launch

                // Ignore credits. Automatically import only debit expenses.
                if (parsed.type != "EXPENSE") {
                    return@launch
                }

                val database = ExpenseDatabase.get(context)
                val dao = database.expenseDao()

                val expense = ExpenseEntity(
                    amount = parsed.amount,
                    merchant = parsed.merchant,
                    receiver = parsed.receiver,
                    category = parsed.category,
                    transactionType = "EXPENSE",
                    paymentMethod = "UPI / Bank SMS",
                    transactionDate = timestamp,
                    smsSender = parsed.sender,
                    smsHash = parsed.hash,
                    notes = "Automatically detected from incoming SMS",
                    manuallyAdded = false
                )

                // Save immediately to the local Room database.
                dao.insert(expense)

            } catch (exception: Exception) {
                exception.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}