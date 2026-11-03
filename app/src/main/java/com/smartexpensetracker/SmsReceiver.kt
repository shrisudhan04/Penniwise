
package com.smartexpensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val preferences = context.getSharedPreferences(
            "settings",
            Context.MODE_PRIVATE
        )

        if (!preferences.getBoolean("sms_tracking", false)) {
            Log.d("SmsReceiver", "SMS tracking is disabled")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        if (messages.isEmpty()) {
            Log.d("SmsReceiver", "No SMS messages found")
            return
        }

        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {

            try {

                // Combine multipart SMS messages into one complete message.
                val sender = messages.firstOrNull()
                    ?.originatingAddress
                    .orEmpty()

                val body = messages.joinToString(separator = "") {
                    it.messageBody.orEmpty()
                }

                Log.d("SmsReceiver", "SMS Sender: $sender")
                Log.d("SmsReceiver", "SMS Body: $body")

                // Parse the complete SMS.
                val parsed = SmsParser.parse(sender, body)

                if (parsed == null) {
                    Log.d("SmsReceiver", "SMS was not recognized as a transaction")
                    return@launch
                }

                Log.d(
                    "SmsReceiver",
                    "Detected ${parsed.type}: ${parsed.amount}"
                )

                val dao = ExpenseDatabase
                    .get(context)
                    .expenseDao()

                // Insert transaction into database.
                dao.insert(
                    ExpenseEntity(
                        amount = parsed.amount,
                        merchant = parsed.merchant,
                        receiver = parsed.receiver,
                        category = parsed.category,
                        transactionType = parsed.type,
                        smsSender = parsed.sender,
                        smsHash = parsed.hash,
                        manuallyAdded = false,
                        transactionDate = messages.first().timestampMillis
                    )
                )

                Log.d(
                    "SmsReceiver",
                    "Transaction inserted successfully"
                )

            } catch (e: Exception) {

                Log.e(
                    "SmsReceiver",
                    "Failed to process incoming SMS",
                    e
                )

            } finally {

                pending.finish()
            }
        }
    }
}
