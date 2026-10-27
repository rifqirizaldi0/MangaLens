package app.mangalens.translate

import app.mangalens.ocr.Script
import app.mangalens.settings.SourceLang
import app.mangalens.settings.TargetLang
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Zero-setup engine using Google Translate's public web endpoint, with
 * automatic MyMemory fallback when Google triggers anti-bot HTTP 429.
 */
class GoogleFreeEngine : TranslationEngine {

    override val label = "Google"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    override suspend fun translate(items: List<String>, lang: SourceLang): List<String> =
        translate(items, lang, TargetLang.EN)

    suspend fun translate(
        items: List<String>,
        lang: SourceLang,
        targetLang: TargetLang,
    ): List<String> {
        if (items.isEmpty()) return emptyList()
        return coroutineScope {
            val semaphore = Semaphore(3)
            items.mapIndexed { idx, text ->
                async {
                    if (text.isBlank()) ""
                    else {
                        if (idx > 0) delay((idx * 40L).coerceAtMost(300L))
                        semaphore.withPermit { translateOne(text, lang, targetLang) }
                    }
                }
            }.map { it.await() }
        }
    }

    private suspend fun translateOne(text: String, lang: SourceLang, targetLang: TargetLang): String {
        return try {
            val sb = StringBuilder()
            for ((trans, _) in fetchRows(text, lang, targetLang)) sb.append(trans)
            val res = sb.toString().trim()
            if (res.isNotEmpty()) res else text
        } catch (e: Exception) {
            val sl = when {
                lang == SourceLang.ES -> "es"
                lang == SourceLang.EN -> "en"
                lang == SourceLang.KO -> "ko"
                lang == SourceLang.JA -> "ja"
                lang == SourceLang.ZH -> "zh"
                else -> "auto"
            }
            val tl = if (targetLang == TargetLang.ID) "id" else "en"
            runCatching { fetchMyMemory(text, sl, tl) }.getOrNull() ?: throw e
        }
    }

    private suspend fun fetchMyMemory(text: String, sl: String, tl: String): String =
        withContext(Dispatchers.IO) {
            val encoded = java.net.URLEncoder.encode(text, "UTF-8")
            val src = if (sl == "auto") "es" else sl
            val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=$src|$tl"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            LlmHttp.await(client.newCall(request)).use { resp ->
                if (!resp.isSuccessful) return@withContext text
                val body = resp.body?.string() ?: return@withContext text
                val json = JSONObject(body)
                val responseData = json.optJSONObject("responseData")
                val translated = responseData?.optString("translatedText", "") ?: ""
                if (translated.isNotBlank()) translated else text
            }
        }

    /** One request; returns Google's (translated, original) segment pairs. */
    private suspend fun fetchRows(text: String, lang: SourceLang, targetLang: TargetLang = TargetLang.EN): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val sl = when {
                lang == SourceLang.ES -> "es"
                lang == SourceLang.EN -> "en"
                lang == SourceLang.KO -> "ko"
                lang == SourceLang.JA -> "ja"
                lang == SourceLang.ZH -> "zh-CN"
                Script.hangulCount(text) > 0 -> "ko"
                Script.kanaCount(text) > 0 -> "ja"
                Script.hanCount(text) > 0 -> "zh-CN"
                Script.cjkCount(text) == 0 -> "auto"
                else -> "auto"
            }
            val tl = when (targetLang) {
                TargetLang.ID -> "id"
                else -> "en"
            }
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx" +
                "&sl=" + sl + "&tl=" + tl + "&dt=t&ie=UTF-8&oe=UTF-8"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Tablet) AppleWebKit/537.36")
                .post(FormBody.Builder().add("q", text).build())
                .build()
            LlmHttp.await(client.newCall(request)).use { resp ->
                if (!resp.isSuccessful) throw RuntimeException("Google translate HTTP " + resp.code)
                val body = resp.body?.string() ?: throw RuntimeException("empty translate response")
                val rows = JSONArray(body).getJSONArray(0)
                val out = ArrayList<Pair<String, String>>(rows.length())
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONArray(i) ?: continue
                    out.add(row.optString(0, "") to row.optString(1, ""))
                }
                out
            }
        }

    private fun squash(s: String) = s.filterNot { it.isWhitespace() }
}
