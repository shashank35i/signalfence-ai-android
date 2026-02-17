package com.signalfence.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.Telephony

// --------------------
// Category + reasons
// --------------------
enum class RiskCategory { NORMAL, SPAM, CRITICAL }

data class RiskDetails(
    val score: Int,
    val label: String,
    val isSpam: Boolean,
    val category: RiskCategory,
    val reasons: List<String> = emptyList()
)

// --------------------
// Prefs
// --------------------
object SignalFencePrefs {
    private const val PREF = "signalfence_prefs"
    private const val K_BLOCK_SPAM = "block_spam"
    private const val K_BLOCK_CRITICAL = "block_critical"

    fun blockSpam(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(K_BLOCK_SPAM, false)

    fun blockCritical(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(K_BLOCK_CRITICAL, true)

    fun setBlockSpam(ctx: Context, v: Boolean) =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(K_BLOCK_SPAM, v).apply()

    fun setBlockCritical(ctx: Context, v: Boolean) =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(K_BLOCK_CRITICAL, v).apply()
}

// --------------------
// Quarantine DB (SQLite)
// --------------------
private class FilteredDb(ctx: Context) : SQLiteOpenHelper(ctx, "signalfence.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS filtered_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                address TEXT NOT NULL,
                body TEXT NOT NULL,
                timeMillis INTEGER NOT NULL,
                category TEXT NOT NULL,
                score INTEGER NOT NULL,
                reasons TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_filtered_cat_time ON filtered_messages(category, timeMillis)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_filtered_addr ON filtered_messages(address)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS filtered_messages")
        onCreate(db)
    }
}

data class FilteredConversationRow(
    val address: String,
    val lastTime: Long,
    val lastBody: String,
    val maxScore: Int,
    val category: String
)

data class FilteredMessageRow(
    val address: String,
    val body: String,
    val timeMillis: Long,
    val category: String,
    val score: Int,
    val reasons: String
)

class FilteredStore(ctx: Context) {
    private val db = FilteredDb(ctx.applicationContext)

    fun save(
        address: String,
        body: String,
        timeMillis: Long,
        category: RiskCategory,
        score: Int,
        reasons: List<String>
    ) {
        val w = db.writableDatabase
        val cv = ContentValues().apply {
            put("address", address)
            put("body", body)
            put("timeMillis", timeMillis)
            put("category", category.name)
            put("score", score)
            put("reasons", reasons.joinToString("  "))
        }
        w.insert("filtered_messages", null, cv)
    }

    fun listMessages(address: String, limit: Int = 200): List<FilteredMessageRow> {
        val r = db.readableDatabase
        val out = ArrayList<FilteredMessageRow>()
        val args = arrayOf(address, limit.toString())
        r.rawQuery(
            "SELECT address, body, timeMillis, category, score, reasons FROM filtered_messages WHERE address=? ORDER BY timeMillis ASC LIMIT ?",
            args
        ).use { c ->
            val iAddr = c.getColumnIndex("address")
            val iBody = c.getColumnIndex("body")
            val iTime = c.getColumnIndex("timeMillis")
            val iCat = c.getColumnIndex("category")
            val iScore = c.getColumnIndex("score")
            val iReasons = c.getColumnIndex("reasons")
            while (c.moveToNext()) {
                out.add(
                    FilteredMessageRow(
                        address = if (iAddr >= 0) c.getString(iAddr) else "",
                        body = if (iBody >= 0) c.getString(iBody) else "",
                        timeMillis = if (iTime >= 0) c.getLong(iTime) else 0L,
                        category = if (iCat >= 0) c.getString(iCat) else "SPAM",
                        score = if (iScore >= 0) c.getInt(iScore) else 0,
                        reasons = if (iReasons >= 0) c.getString(iReasons) else ""
                    )
                )
            }
        }
        return out
    }

    fun listConversations(cat: RiskCategory, limit: Int): List<FilteredConversationRow> {
        val r = db.readableDatabase
        val out = ArrayList<FilteredConversationRow>()

        val sql = """
            SELECT address,
                   MAX(timeMillis) AS lastTime,
                   (SELECT body FROM filtered_messages f2 
                        WHERE f2.address = f1.address AND f2.category = ? 
                        ORDER BY f2.timeMillis DESC LIMIT 1) AS lastBody,
                   MAX(score) AS maxScore
            FROM filtered_messages f1
            WHERE category = ?
            GROUP BY address
            ORDER BY lastTime DESC
            LIMIT ?
        """.trimIndent()

        val args = arrayOf(cat.name, cat.name, limit.toString())
        r.rawQuery(sql, args).use { c ->
            val idxAddr = c.getColumnIndex("address")
            val idxTime = c.getColumnIndex("lastTime")
            val idxBody = c.getColumnIndex("lastBody")
            val idxScore = c.getColumnIndex("maxScore")

            while (c.moveToNext()) {
                out.add(
                    FilteredConversationRow(
                        address = if (idxAddr >= 0) c.getString(idxAddr) else "",
                        lastTime = if (idxTime >= 0) c.getLong(idxTime) else 0L,
                        lastBody = if (idxBody >= 0) (c.getString(idxBody) ?: "") else "",
                        maxScore = if (idxScore >= 0) c.getInt(idxScore) else 0,
                        category = cat.name
                    )
                )
            }
        }
        return out
    }
}

// --------------------
// Insert into Telephony provider (default SMS app only)
// --------------------
object SmsProviderWriter {

    fun insertInbox(ctx: Context, address: String, body: String, timeMillis: Long) {
        val cv = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timeMillis)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, cv)
    }

    fun insertSent(ctx: Context, address: String, body: String, timeMillis: Long) {
        val cv = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timeMillis)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
        }
        ctx.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, cv)
    }
}


