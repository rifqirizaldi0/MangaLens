package app.mangalens.translate

import app.mangalens.settings.SourceLang
import app.mangalens.settings.TargetLang
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/**
 * Fully offline translation via ML Kit. Downloads a ~30 MB model per language
 * on first use, then works with no network at all. Quality is rougher than the
 * online engines but it never fails on a subway.
 */
class MlKitEngine : TranslationEngine {

    override val label = "On-device"

    private val translators = HashMap<String, Translator>()

    override suspend fun translate(items: List<String>, lang: SourceLang): List<String> =
        translate(items, lang, TargetLang.ID)

    suspend fun translate(
        items: List<String>,
        lang: SourceLang,
        targetLang: TargetLang,
    ): List<String> {
        val effective = if (lang == SourceLang.AUTO) SourceLang.KO else lang
        val translator = translatorFor(effective, targetLang)
        return items.map {
            try {
                translator.translate(it).await()
            } catch (_: Exception) {
                ""
            }
        }
    }

    private suspend fun translatorFor(lang: SourceLang, targetLang: TargetLang): Translator {
        val srcCode = sourceCode(lang)
        val tgtCode = targetCode(targetLang)
        val cacheKey = "$srcCode->$tgtCode"
        val translator = synchronized(translators) {
            translators.getOrPut(cacheKey) {
                Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(srcCode)
                        .setTargetLanguage(tgtCode)
                        .build()
                )
            }
        }
        try {
            translator.downloadModelIfNeeded().await()
        } catch (e: Exception) {
            val srcModel = TranslateRemoteModel.Builder(srcCode).build()
            val tgtModel = TranslateRemoteModel.Builder(tgtCode).build()
            val manager = RemoteModelManager.getInstance()
            val hasSrc = runCatching { manager.isModelDownloaded(srcModel).await() }.getOrDefault(false)
            val hasTgt = runCatching { manager.isModelDownloaded(tgtModel).await() }.getOrDefault(false)
            if (!hasSrc || !hasTgt) {
                val missing = mutableListOf<String>()
                if (!hasSrc) missing.add(srcCode)
                if (!hasTgt) missing.add(tgtCode)
                throw IllegalStateException("Model offline ($missing) belum diunduh. Hubungkan internet dan klik 'Unduh Model Offline' di menu aplikasi.")
            }
        }
        return translator
    }

    companion object {
        fun sourceCode(lang: SourceLang): String = when (lang) {
            SourceLang.KO -> TranslateLanguage.KOREAN
            SourceLang.JA -> TranslateLanguage.JAPANESE
            SourceLang.ZH -> TranslateLanguage.CHINESE
            SourceLang.EN -> TranslateLanguage.ENGLISH
            SourceLang.ES -> TranslateLanguage.SPANISH
            else -> TranslateLanguage.KOREAN
        }

        fun targetCode(targetLang: TargetLang): String = when (targetLang) {
            TargetLang.ID -> TranslateLanguage.INDONESIAN
            else -> TranslateLanguage.ENGLISH
        }

        suspend fun isModelDownloaded(langCode: String): Boolean {
            return try {
                val model = TranslateRemoteModel.Builder(langCode).build()
                RemoteModelManager.getInstance().isModelDownloaded(model).await()
            } catch (_: Exception) {
                false
            }
        }

        suspend fun downloadModel(langCode: String): Boolean {
            return try {
                val model = TranslateRemoteModel.Builder(langCode).build()
                val conditions = DownloadConditions.Builder().build()
                RemoteModelManager.getInstance().download(model, conditions).await()
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
