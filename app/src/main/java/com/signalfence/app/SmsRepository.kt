package com.signalfence.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat

class SmsRepository(private val context: Context) {

    data class Conversation(
        val threadId: Long,
        val address: String,
        val title: String,
        val snippet: String,
        val timeMillis: Long
    )

    data class Message(
        val id: Long,
        val threadId: Long,
        val address: String,
        val body: String,
        val timeMillis: Long,
        val type: Int // 1=inbox, 2=sent
    ) {
        val isOutgoing: Boolean get() = type == 2
    }

    fun getConversations(limit: Int = 200): List<Conversation> {
        if (!hasReadSms()) return emptyList()

        val out = ArrayList<Conversation>()
        val uri = Uri.parse("content://mms-sms/conversations?simple=true")
        val projection = arrayOf("_id", "snippet", "date")
        val sort = "date DESC"

        try {
            context.contentResolver.query(uri, projection, null, null, sort)?.use { c ->
                val idxId = c.getColumnIndex("_id")
                val idxSnippet = c.getColumnIndex("snippet")
                val idxDate = c.getColumnIndex("date")

                var count = 0
                while (c.moveToNext() && count < limit) {
                    val threadId = if (idxId >= 0) c.getLong(idxId) else 0L
                    val snippet = if (idxSnippet >= 0) (c.getString(idxSnippet) ?: "") else ""
                    val date = if (idxDate >= 0) c.getLong(idxDate) else 0L

                    val address = getLatestAddressForThread(threadId) ?: "Unknown"
                    val name = lookupContactName(address)
                    val title = name ?: address

                    out.add(Conversation(threadId, address, title, snippet, date))
                    count++
                }
            }
        } catch (_: Throwable) {
            return emptyList()
        }

        return out
    }
    fun getLastMessageForThread(threadId: Long): Message? {
        if (!hasReadSms() || threadId <= 0) return null

        val uri = Uri.parse("content://sms")
        val projection = arrayOf("_id", "thread_id", "address", "body", "date", "type")
        val selection = "thread_id=?"
        val args = arrayOf(threadId.toString())
        val sort = "date DESC"

        return try {
            context.contentResolver.query(uri, projection, selection, args, sort)?.use { c ->
                val idxId = c.getColumnIndex("_id")
                val idxThread = c.getColumnIndex("thread_id")
                val idxAddr = c.getColumnIndex("address")
                val idxBody = c.getColumnIndex("body")
                val idxDate = c.getColumnIndex("date")
                val idxType = c.getColumnIndex("type")

                if (c.moveToFirst()) {
                    Message(
                        id = if (idxId >= 0) c.getLong(idxId) else 0L,
                        threadId = if (idxThread >= 0) c.getLong(idxThread) else threadId,
                        address = if (idxAddr >= 0) (c.getString(idxAddr) ?: "") else "",
                        body = if (idxBody >= 0) (c.getString(idxBody) ?: "") else "",
                        timeMillis = if (idxDate >= 0) c.getLong(idxDate) else 0L,
                        type = if (idxType >= 0) c.getInt(idxType) else 1
                    )
                } else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun getThreadMessages(threadId: Long, limit: Int = 500): List<Message> {
        if (!hasReadSms() || threadId <= 0) return emptyList()

        val out = ArrayList<Message>()
        val uri = Uri.parse("content://sms")
        val projection = arrayOf("_id", "thread_id", "address", "body", "date", "type")
        val selection = "thread_id=?"
        val args = arrayOf(threadId.toString())
        val sort = "date ASC"

        try {
            context.contentResolver.query(uri, projection, selection, args, sort)?.use { c ->
                val idxId = c.getColumnIndex("_id")
                val idxThread = c.getColumnIndex("thread_id")
                val idxAddr = c.getColumnIndex("address")
                val idxBody = c.getColumnIndex("body")
                val idxDate = c.getColumnIndex("date")
                val idxType = c.getColumnIndex("type")

                while (c.moveToNext()) {
                    val id = if (idxId >= 0) c.getLong(idxId) else 0L
                    val tId = if (idxThread >= 0) c.getLong(idxThread) else threadId
                    val address = if (idxAddr >= 0) (c.getString(idxAddr) ?: "") else ""
                    val body = if (idxBody >= 0) (c.getString(idxBody) ?: "") else ""
                    val date = if (idxDate >= 0) c.getLong(idxDate) else 0L
                    val type = if (idxType >= 0) c.getInt(idxType) else 1

                    out.add(Message(id, tId, address, body, date, type))
                }
            }
        } catch (_: Throwable) {
            return emptyList()
        }

        return if (out.size <= limit) out else out.takeLast(limit)
    }

    // ✅ If threadId not known yet (new chat), fetch by address
    fun getMessagesByAddress(address: String, limit: Int = 500): List<Message> {
        if (!hasReadSms() || address.isBlank()) return emptyList()

        val out = ArrayList<Message>()
        val uri = Uri.parse("content://sms")
        val projection = arrayOf("_id", "thread_id", "address", "body", "date", "type")
        val selection = "address=?"
        val args = arrayOf(address)
        val sort = "date ASC"

        try {
            context.contentResolver.query(uri, projection, selection, args, sort)?.use { c ->
                val idxId = c.getColumnIndex("_id")
                val idxThread = c.getColumnIndex("thread_id")
                val idxAddr = c.getColumnIndex("address")
                val idxBody = c.getColumnIndex("body")
                val idxDate = c.getColumnIndex("date")
                val idxType = c.getColumnIndex("type")

                while (c.moveToNext()) {
                    val id = if (idxId >= 0) c.getLong(idxId) else 0L
                    val tId = if (idxThread >= 0) c.getLong(idxThread) else 0L
                    val addr = if (idxAddr >= 0) (c.getString(idxAddr) ?: "") else ""
                    val body = if (idxBody >= 0) (c.getString(idxBody) ?: "") else ""
                    val date = if (idxDate >= 0) c.getLong(idxDate) else 0L
                    val type = if (idxType >= 0) c.getInt(idxType) else 1

                    out.add(Message(id, tId, addr, body, date, type))
                }
            }
        } catch (_: Throwable) {
            return emptyList()
        }

        return if (out.size <= limit) out else out.takeLast(limit)
    }

    // ✅ This is the key: write outgoing message to provider so it appears in chat immediately
    fun insertSentSms(address: String, body: String, timeMillis: Long = System.currentTimeMillis()): Long {
        if (address.isBlank() || body.isBlank()) return -1L

        // You must be default SMS app for this to work reliably
        val values = ContentValues().apply {
            put("address", address)
            put("body", body)
            put("date", timeMillis)
            put("read", 1)
            put("seen", 1)
            put("type", 2) // sent
        }

        return try {
            val sentUri = try {
                Telephony.Sms.Sent.CONTENT_URI
            } catch (_: Throwable) {
                Uri.parse("content://sms/sent")
            }

            val inserted = context.contentResolver.insert(sentUri, values)
            inserted?.lastPathSegment?.toLongOrNull() ?: -1L
        } catch (_: Throwable) {
            -1L
        }
    }

    fun resolveThreadIdForAddress(address: String): Long {
        if (!hasReadSms() || address.isBlank()) return -1L

        val uri = Uri.parse("content://sms")
        val projection = arrayOf("thread_id")
        val selection = "address=?"
        val args = arrayOf(address)
        val sort = "date DESC"

        return try {
            context.contentResolver.query(uri, projection, selection, args, sort)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else -1L
            } ?: -1L
        } catch (_: Throwable) {
            -1L
        }
    }

    // -------------------------
    // Helpers
    // -------------------------
    private fun getLatestAddressForThread(threadId: Long): String? {
        if (!hasReadSms()) return null

        val uri = Uri.parse("content://sms")
        val projection = arrayOf("address", "date")
        val selection = "thread_id=?"
        val args = arrayOf(threadId.toString())
        val sort = "date DESC"

        return try {
            context.contentResolver.query(uri, projection, selection, args, sort)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun lookupContactName(phone: String): String? {
        if (!hasReadContacts()) return null

        return try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phone)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

            context.contentResolver.query(lookupUri, projection, null, null, null)?.use { cur ->
                if (cur.moveToFirst()) cur.getString(0) else null
            }
        } catch (_: Throwable) {
            null
        }
    }
    fun insertOutgoingMessage(address: String, body: String): Boolean {
        // Only default SMS app can write to provider
        if (!AccessManager.isDefaultSmsApp(context)) return false

        return try {
            val values = ContentValues().apply {
                put("address", address)
                put("body", body)
                put("date", System.currentTimeMillis())
                put("read", 1)
                put("seen", 1)
                put("type", 2) // sent
            }
            context.contentResolver.insert(Uri.parse("content://sms/sent"), values)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun hasReadSms(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun hasReadContacts(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
    }
}
