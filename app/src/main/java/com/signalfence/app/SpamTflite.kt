package com.signalfence.app

import android.content.Context
import org.json.JSONObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import kotlin.math.min
import org.tensorflow.lite.Interpreter

/**
 * Lightweight TFLite wrapper for the SMS spam model exported by train_sms_spam_model.py.
 *
 * Model input: int32 tensor [1, max_len] of token ids.
 * Output: float32 [[probability]] where 1.0 == spam.
 */
object SpamTflite {

    private const val MODEL_NAME = "sms_spam_model.tflite"
    private const val TOKENIZER_NAME = "sms_tokenizer.json"
    private const val META_NAME = "sms_meta.json"

    private var interpreter: Interpreter? = null
    private var wordIndex: Map<String, Int> = emptyMap()
    private var maxLen: Int = 40
    private var vocabSize: Int = 8000
    private var oovIndex: Int = 1 // Keras default when oov_token is used

    fun init(appContext: Context) {
        if (interpreter != null && wordIndex.isNotEmpty()) return

        val assets = appContext.assets

        // Load metadata
        assets.open(META_NAME).use { metaStream ->
            val meta = JSONObject(metaStream.readBytes().decodeToString())
            maxLen = meta.optInt("max_len", maxLen)
            vocabSize = meta.optInt("vocab_size", vocabSize)
        }

        // Load tokenizer word_index
        assets.open(TOKENIZER_NAME).use { tokStream ->
            wordIndex = parseWordIndex(tokStream)
            oovIndex = wordIndex["<OOV>"] ?: oovIndex
        }

        // Load model
        interpreter = Interpreter(loadModelFile(appContext))
    }

    val isReady: Boolean
        get() = interpreter != null && wordIndex.isNotEmpty()

    /**
     * Returns spam probability in 0f..1f.
     */
    fun predict(appContext: Context, rawText: String): Float {
        if (rawText.isBlank()) return 0f
        init(appContext)
        val tfl = interpreter ?: return 0f

        val input = tokenize(rawText)
        val inputBuffer = arrayOf(input)
        val output = Array(1) { FloatArray(1) }

        tfl.run(inputBuffer, output)
        return output[0][0].coerceIn(0f, 1f)
    }

    // -------------------- helpers --------------------

    private fun loadModelFile(context: Context): MappedByteBuffer {
        context.assets.openFd(MODEL_NAME).use { afd ->
            FileChannel.MapMode.READ_ONLY.let { mode ->
                return afd.createInputStream().channel.map(mode, afd.startOffset, afd.declaredLength)
            }
        }
    }

    private fun parseWordIndex(input: InputStream): Map<String, Int> {
        val json = JSONObject(input.readBytes().decodeToString())
        val wi = json.getJSONObject("word_index")
        val out = HashMap<String, Int>(wi.length())
        val keys = wi.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = wi.getInt(k)
        }
        return out
    }

    private fun tokenize(text: String): IntArray {
        val cleaned = text.lowercase(Locale.US)
            .replace("[^a-z0-9']+".toRegex(), " ")
            .trim()
        if (cleaned.isBlank()) return IntArray(maxLen)

        val tokens = cleaned.split("\\s+".toRegex())
        val ids = IntArray(maxLen) { 0 }

        var i = 0
        for (tok in tokens) {
            if (i >= maxLen) break
            val id = wordIndex[tok]?.takeIf { it <= vocabSize } ?: oovIndex
            ids[i++] = id
        }
        // Remaining positions stay 0 (post-padding)
        return ids
    }
}
