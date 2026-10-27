package app.mangalens.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import app.mangalens.settings.SourceLang
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * On-device OCR built on ML Kit's CJK recognizers.
 *
 * In AUTO mode, all three recognizers race on the same frame and the result with
 * the strongest script signature wins. After the same language wins twice in a
 * row it is "pinned" so subsequent frames only pay for a single recognizer; the
 * pin is dropped again whenever a frame produces almost no text in that script.
 */
class OcrEngine {

    data class Result(val lines: List<OcrLine>, val lang: SourceLang)

    private val korean: TextRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }
    private val japanese: TextRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }
    private val chinese: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val latin: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.Builder().build())
    }

    private var pinned: SourceLang? = null
    private var lastWinner: SourceLang? = null
    private var winStreak = 0

    suspend fun recognize(bitmap: Bitmap, setting: SourceLang): Result {
        if (setting != SourceLang.AUTO) {
            return Result(run(recognizerFor(setting), bitmap), setting)
        }
        pinned?.let { lang ->
            val lines = run(recognizerFor(lang), bitmap)
            val strength = if (lang == SourceLang.ES || lang == SourceLang.EN) {
                lines.sumOf { l -> l.text.count { it.isLetter() } }
            } else {
                lines.sumOf { Script.cjkCount(it.text) }
            }
            if (strength >= 6) return Result(lines, lang)
            pinned = null
            lastWinner = null
            winStreak = 0
        }
        return race(bitmap)
    }

    fun reset() {
        pinned = null
        lastWinner = null
        winStreak = 0
    }

    /**
     * Reads one balloon's crop, enlarged by the caller so the recognizers
     * see lettering at the size they were trained on. With [lang] known the
     * matching recognizer runs alone; otherwise all recognizers race and the
     * strongest script signature wins.
     */
    suspend fun recognizeRegion(bitmap: Bitmap, lang: SourceLang?): List<OcrLine> {
        val known = lang ?: pinned
        if (known != null && known != SourceLang.AUTO) return run(recognizerFor(known), bitmap)
        return coroutineScope {
            val image = InputImage.fromBitmap(bitmap, 0)
            val ko = async { runCatching { korean.process(image).await() }.getOrNull() }
            val ja = async { runCatching { japanese.process(image).await() }.getOrNull() }
            val zh = async { runCatching { chinese.process(image).await() }.getOrNull() }
            val lat = async { runCatching { latin.process(image).await() }.getOrNull() }
            val koRes = ko.await()
            val jaRes = ja.await()
            val zhRes = zh.await()
            val latRes = lat.await()

            val koScore = koRes?.text?.let { Script.hangulCount(it) * 3 } ?: 0
            val jaScore = jaRes?.text?.let { Script.kanaCount(it) * 3 + Script.hanCount(it) } ?: 0
            val zhScore = zhRes?.text?.let { Script.hanCount(it) * 2 } ?: 0
            val latLetters = latRes?.text?.count { it.isLetter() } ?: 0

            val cjkMax = maxOf(koScore, jaScore, zhScore)
            if (latLetters > 4 && latLetters > cjkMax) {
                return@coroutineScope toLines(latRes)
            }
            val results = listOf(koRes, jaRes, zhRes)
            val scored = listOf(koScore, jaScore, zhScore)
            val best = scored.indices.maxBy { scored[it] }
            if (scored[best] >= 2) {
                toLines(results[best])
            } else if (latLetters > 0) {
                toLines(latRes)
            } else {
                toLines(results.maxByOrNull { it?.text?.count { c -> c.isLetter() } ?: 0 })
            }
        }
    }

    private fun recognizerFor(lang: SourceLang): TextRecognizer = when (lang) {
        SourceLang.KO -> korean
        SourceLang.JA -> japanese
        SourceLang.ZH -> chinese
        SourceLang.EN -> latin
        SourceLang.ES -> latin
        SourceLang.AUTO -> korean
    }

    private suspend fun race(bitmap: Bitmap): Result = coroutineScope {
        val image = InputImage.fromBitmap(bitmap, 0)
        val ko = async { runCatching { korean.process(image).await() }.getOrNull() }
        val ja = async { runCatching { japanese.process(image).await() }.getOrNull() }
        val zh = async { runCatching { chinese.process(image).await() }.getOrNull() }
        val lat = async { runCatching { latin.process(image).await() }.getOrNull() }
        val koText = ko.await()
        val jaText = ja.await()
        val zhText = zh.await()
        val latText = lat.await()

        val koScore = koText?.text?.let { Script.hangulCount(it) * 3 } ?: 0
        val jaScore = jaText?.text?.let { Script.kanaCount(it) * 3 + Script.hanCount(it) } ?: 0
        val zhScore = zhText?.text?.let { Script.hanCount(it) * 2 - Script.kanaCount(it) * 2 } ?: 0

        val best = listOf(
            SourceLang.KO to koScore,
            SourceLang.JA to jaScore,
            SourceLang.ZH to zhScore,
        ).maxBy { it.second }

        if (best.second >= 4) {
            val winner = best.first
            if (winner == lastWinner) {
                if (++winStreak >= 2) pinned = winner
            } else {
                lastWinner = winner
                winStreak = 1
            }
            val winText = when (winner) {
                SourceLang.KO -> koText
                SourceLang.JA -> jaText
                SourceLang.ZH -> zhText
                else -> koText
            }
            return@coroutineScope Result(toLines(winText), winner)
        }

        // CJK signature is weak — inspect Latin recognizer output for Spanish or English
        val latinTextStr = latText?.text ?: ""
        val latinLetters = latinTextStr.count { it.isLetter() }
        val hasSpanishMarkers = latinTextStr.any { it in "¡¿ñáéíóúÁÉÍÓÚÑ" }
        val isSpanish = hasSpanishMarkers || Regex(
            "\\b(el|la|los|las|un|una|que|es|en|por|con|para|pero|como|del|al|no|si|yo|tu|te|me|se)\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(latinTextStr)

        if (latinLetters >= 4) {
            val winner = if (isSpanish) SourceLang.ES else SourceLang.EN
            if (winner == lastWinner) {
                if (++winStreak >= 2) pinned = winner
            } else {
                lastWinner = winner
                winStreak = 1
            }
            return@coroutineScope Result(toLines(latText), winner)
        }

        // Low confidence fallback
        lastWinner = null
        winStreak = 0
        Result(toLines(latText ?: koText), SourceLang.AUTO)
    }

    private suspend fun run(recognizer: TextRecognizer, bitmap: Bitmap): List<OcrLine> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = runCatching { recognizer.process(image).await() }.getOrNull() ?: return emptyList()
        return toLines(text)
    }

    private fun toLines(text: Text?): List<OcrLine> {
        if (text == null) return emptyList()
        val out = ArrayList<OcrLine>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                val box: Rect = line.boundingBox ?: continue
                val t = line.text.trim()
                if (t.isEmpty()) continue
                val vertical = box.height() > box.width() * 1.4 && t.length > 1
                out.add(OcrLine(t, box, vertical))
            }
        }
        return out
    }
}
