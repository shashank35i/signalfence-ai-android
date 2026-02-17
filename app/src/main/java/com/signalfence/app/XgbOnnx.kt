package com.signalfence.app

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.nio.FloatBuffer
import java.util.Locale
import kotlin.math.sqrt

/**
 * On-device XGBoost inference via ONNX Runtime.
 *
 * Model input: float32 tensor [1, vocab_size] TF-IDF vector.
 * Outputs: probabilities [1, 2], where index 1 = spam.
 */
object XgbOnnx {

    private const val MODEL_NAME = "signalfence_xgb.onnx"
    private const val VOCAB_NAME = "signalfence_vocab.json"
    private const val IDF_NAME = "signalfence_idf.json"
    private const val META_NAME = "signalfence_meta.json"

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var vocab: Map<String, Int> = emptyMap()
    private var idf: FloatArray = FloatArray(0)
    private var vocabSize: Int = 0

    fun init(appContext: Context) {
        if (session != null && vocab.isNotEmpty() && idf.isNotEmpty()) return

        val assets = appContext.assets

        val meta = JSONObject(assets.open(META_NAME).use { it.readBytes().decodeToString() })
        vocabSize = meta.optInt("vocab_size", 0)

        // Load vocab
        vocab = assets.open(VOCAB_NAME).use { stream ->
            val json = JSONObject(stream.readBytes().decodeToString())
            val out = HashMap<String, Int>(json.length())
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = json.getInt(k)
            }
            out
        }

        // Load idf
        val idfJson = assets.open(IDF_NAME).use { it.readBytes().decodeToString() }
        val idfArr = org.json.JSONArray(idfJson)
        idf = FloatArray(idfArr.length()) { i -> idfArr.optDouble(i, 0.0).toFloat() }

        // Load ONNX model
        env = OrtEnvironment.getEnvironment()
        session = env!!.createSession(assets.open(MODEL_NAME).readBytes())
    }

    val isReady: Boolean
        get() = session != null && vocab.isNotEmpty() && idf.isNotEmpty()

    fun predict(appContext: Context, rawText: String): Float {
        if (rawText.isBlank()) return 0f
        init(appContext)
        val sess = session ?: return 0f
        val environment = env ?: return 0f

        val vector = tfidfVector(rawText)
        if (vector.isEmpty()) return 0f

        val inputName = sess.inputNames.iterator().next()
        val inputShape = longArrayOf(1, vocabSize.toLong())
        val tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(vector), inputShape)

        sess.run(mapOf(inputName to tensor)).use { result ->
            // Expect outputs: label, probabilities
            val probsAny = result[1].value
            return extractSpamProb(probsAny)
        }
    }

    private fun extractSpamProb(value: Any?): Float {
        return when (value) {
            is Array<*> -> {
                val first = value.firstOrNull()
                if (first is FloatArray && first.size >= 2) first[1].coerceIn(0f, 1f)
                else if (first is DoubleArray && first.size >= 2) first[1].toFloat().coerceIn(0f, 1f)
                else 0f
            }
            else -> 0f
        }
    }

    private fun tfidfVector(text: String): FloatArray {
        if (vocabSize <= 0) return FloatArray(0)

        val tokens = tokenize(text)
        if (tokens.isEmpty()) return FloatArray(vocabSize)

        val tf = FloatArray(vocabSize)
        for (t in tokens) {
            val idx = vocab[t] ?: continue
            if (idx in tf.indices) tf[idx] += 1f
        }

        // TF-IDF
        val tfidf = FloatArray(vocabSize)
        var norm = 0f
        for (i in tf.indices) {
            val v = tf[i] * (idf.getOrNull(i) ?: 0f)
            tfidf[i] = v
            norm += v * v
        }

        norm = sqrt(norm)
        if (norm > 0f) {
            for (i in tfidf.indices) tfidf[i] /= norm
        }

        return tfidf
    }

    private fun tokenize(text: String): List<String> {
        val cleaned = text.lowercase(Locale.US)
        val regex = Regex("\\b\\w\\w+\\b")
        return regex.findAll(cleaned).map { it.value }.toList()
    }
}
