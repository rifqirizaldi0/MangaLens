package app.mangalens.ui

import app.mangalens.settings.AppLang

object AppStrings {

    fun appTitle(lang: AppLang) = "MangaLens"

    fun appSubtitle(lang: AppLang) = when (lang) {
        AppLang.ID -> "Terjemahan live manhwa · manga · manhua di atas aplikasi apa pun"
        AppLang.EN -> "Live manhwa · manga · manhua translation over any app"
    }

    fun translatingScreen(lang: AppLang) = when (lang) {
        AppLang.ID -> "Sedang menerjemahkan layar"
        AppLang.EN -> "Translating your screen"
    }

    fun notRunning(lang: AppLang) = when (lang) {
        AppLang.ID -> "Tidak berjalan"
        AppLang.EN -> "Not running"
    }

    fun overlayPermissionStep(lang: AppLang) = when (lang) {
        AppLang.ID -> "Langkah 1 · Izinkan MangaLens tampil di atas aplikasi lain"
        AppLang.EN -> "Step 1 · Allow MangaLens to draw over other apps"
    }

    fun grantOverlayBtn(lang: AppLang) = when (lang) {
        AppLang.ID -> "Beri izin overlay"
        AppLang.EN -> "Grant overlay permission"
    }

    fun startBtn(lang: AppLang, granted: Boolean) = when (lang) {
        AppLang.ID -> if (granted) "Mulai menerjemahkan" else "Beri izin dulu"
        AppLang.EN -> if (granted) "Start translating" else "Grant permission first"
    }

    fun stopBtn(lang: AppLang) = when (lang) {
        AppLang.ID -> "Berhenti"
        AppLang.EN -> "Stop"
    }

    fun howToUse(lang: AppLang) = when (lang) {
        AppLang.ID -> "Buka browser atau aplikasi baca komikmu. Saat berhenti scroll, teks komik langsung diterjemahkan di tempat. " +
            "Ketuk tombol melayang 文A untuk menjeda (Freeze) agar teks tidak hilang saat zoom/gerak; tahan lama untuk menu cepat."
        AppLang.EN -> "Then open Brave and read. When you stop scrolling, bubbles are translated in place. " +
            "Tap the floating 文A button to pause/freeze overlays so they don't disappear while zooming; long-press for quick menu."
    }

    fun sectionAppLang(lang: AppLang) = when (lang) {
        AppLang.ID -> "Bahasa Aplikasi"
        AppLang.EN -> "App Language"
    }

    fun sectionEngine(lang: AppLang) = when (lang) {
        AppLang.ID -> "Mesin Terjemahan"
        AppLang.EN -> "Translation Engine"
    }

    fun sectionReading(lang: AppLang) = when (lang) {
        AppLang.ID -> "Pengaturan Membaca"
        AppLang.EN -> "Reading Settings"
    }

    fun sourceLanguage(lang: AppLang) = when (lang) {
        AppLang.ID -> "Bahasa sumber (asal komik)"
        AppLang.EN -> "Source language"
    }

    fun translateTo(lang: AppLang) = when (lang) {
        AppLang.ID -> "Terjemahkan ke"
        AppLang.EN -> "Translate to"
    }

    fun mode(lang: AppLang) = when (lang) {
        AppLang.ID -> "Mode terjemahan"
        AppLang.EN -> "Mode"
    }

    fun modeAuto(lang: AppLang) = when (lang) {
        AppLang.ID -> "Otomatis live (Hands-free)"
        AppLang.EN -> "Auto-live (hands-free)"
    }

    fun modeManual(lang: AppLang) = when (lang) {
        AppLang.ID -> "Ketuk untuk terjemahkan (Manual)"
        AppLang.EN -> "Tap to translate (Manual)"
    }

    fun reactionTime(lang: AppLang) = when (lang) {
        AppLang.ID -> "Waktu reaksi deteksi"
        AppLang.EN -> "Reaction time"
    }

    fun textSize(lang: AppLang) = when (lang) {
        AppLang.ID -> "Ukuran teks terjemahan"
        AppLang.EN -> "Text size"
    }

    fun bubbleOpacity(lang: AppLang) = when (lang) {
        AppLang.ID -> "Opasitas balon terjemahan"
        AppLang.EN -> "Bubble background opacity"
    }

    fun ignoreTop(lang: AppLang) = when (lang) {
        AppLang.ID -> "Abaikan bagian atas layar (Bar browser)"
        AppLang.EN -> "Ignore top of screen (browser bar)"
    }

    fun ignoreBottom(lang: AppLang) = when (lang) {
        AppLang.ID -> "Abaikan bagian bawah layar"
        AppLang.EN -> "Ignore bottom of screen"
    }

    fun sectionTips(lang: AppLang) = when (lang) {
        AppLang.ID -> "Tips Membaca Komik"
        AppLang.EN -> "Reading Tips"
    }

    fun tipsContent(lang: AppLang) = when (lang) {
        AppLang.ID -> listOf(
            "Zoom in & Freeze" to "Ketuk tombol melayang 文A untuk menjeda (Freeze). Teks terjemahan akan terkunci di layar sehingga kamu bisa zoom in / zoom out atau menggeser tanpa takut teks terjemahan hilang.",
            "Mode Ketuk (Manual)" to "Jika membaca manhwa panjang dengan scroll cepat, gunakan 'Ketuk untuk terjemahkan' agar terjemahan hanya muncul saat kamu butuhkan.",
            "Komik Bahasa Inggris" to "Jika membaca komik yang sudah berbahasa Inggris, pilih Bahasa Sumber 'English' dan Terjemahkan ke 'Indonesia'.",
            "Balon Tertutup" to "Sesuaikan 'Ukuran teks' atau 'Opasitas balon' agar balon terjemahan pas menutupi teks asli komik.",
        )
        AppLang.EN -> listOf(
            "Zoom in & Freeze" to "Tap the floating 文A button to pause/freeze. Translated bubbles will lock on screen so you can pinch-to-zoom or scroll without losing translations.",
            "Tap to Translate Mode" to "When reading fast-scrolling manhwa, switch to 'Tap to translate' so translations only run when requested.",
            "English Scans" to "When reading English scanlations, set Source language to 'English' and Translate to 'Indonesia'.",
            "Bubble Alignment" to "Adjust 'Text size' or 'Bubble opacity' so translated cards cleanly cover the original dialogue.",
        )
    }

    // Floating Menu Items
    fun menuTranslateNow(lang: AppLang) = when (lang) {
        AppLang.ID -> "⚡  Terjemahkan sekarang"
        AppLang.EN -> "⚡  Translate now"
    }

    fun menuPause(lang: AppLang) = when (lang) {
        AppLang.ID -> "⏸  Jeda (Freeze layar)"
        AppLang.EN -> "⏸  Pause (Freeze overlay)"
    }

    fun menuResume(lang: AppLang) = when (lang) {
        AppLang.ID -> "▶  Lanjutkan live"
        AppLang.EN -> "▶  Resume live mode"
    }

    fun menuSwitchToManual(lang: AppLang) = when (lang) {
        AppLang.ID -> "✋  Ganti ke mode ketuk"
        AppLang.EN -> "✋  Switch to tap-to-translate"
    }

    fun menuSwitchToAuto(lang: AppLang) = when (lang) {
        AppLang.ID -> "🔄  Ganti ke otomatis live"
        AppLang.EN -> "🔄  Switch to auto-live"
    }

    fun menuPeek(lang: AppLang) = when (lang) {
        AppLang.ID -> "👁  Lihat teks asli (4 dtk)"
        AppLang.EN -> "👁  Peek at original (4 s)"
    }

    fun menuNewSeries(lang: AppLang) = when (lang) {
        AppLang.ID -> "📖  Judul baru — reset nama & memori"
        AppLang.EN -> "📖  New series — forget names so far"
    }

    fun menuSettings(lang: AppLang) = when (lang) {
        AppLang.ID -> "⚙  Pengaturan"
        AppLang.EN -> "⚙  Settings"
    }

    fun menuStop(lang: AppLang) = when (lang) {
        AppLang.ID -> "✕  Tutup MangaLens"
        AppLang.EN -> "✕  Stop translating"
    }

    // Pill & Notification
    fun pillTranslating(lang: AppLang) = when (lang) {
        AppLang.ID -> "menerjemahkan…"
        AppLang.EN -> "translating…"
    }

    fun pillPaused(lang: AppLang) = when (lang) {
        AppLang.ID -> "⏸ Dijeda (Freeze) — ketuk untuk lanjut"
        AppLang.EN -> "⏸ Paused (Frozen) — tap to resume"
    }

    fun pillLive(lang: AppLang) = when (lang) {
        AppLang.ID -> "▶ Live aktif"
        AppLang.EN -> "▶ Live mode"
    }

    fun pillNoText(lang: AppLang) = when (lang) {
        AppLang.ID -> "tidak ada teks ditemukan"
        AppLang.EN -> "no text found"
    }

    fun notifTitle(lang: AppLang) = when (lang) {
        AppLang.ID -> "MangaLens menerjemahkan layar Anda"
        AppLang.EN -> "MangaLens is translating your screen"
    }

    fun notifContent(lang: AppLang, paused: Boolean) = when (lang) {
        AppLang.ID -> if (paused) "Dijeda (Freeze)" else "Live — balon diterjemahkan saat membaca"
        AppLang.EN -> if (paused) "Paused (Frozen)" else "Live — bubbles translate as you read"
    }

    fun notifActionResume(lang: AppLang) = when (lang) {
        AppLang.ID -> "Lanjut"
        AppLang.EN -> "Resume"
    }

    fun notifActionPause(lang: AppLang) = when (lang) {
        AppLang.ID -> "Jeda"
        AppLang.EN -> "Pause"
    }

    fun notifActionStop(lang: AppLang) = when (lang) {
        AppLang.ID -> "Berhenti"
        AppLang.EN -> "Stop"
    }

    // Tabs
    fun tabMain(lang: AppLang) = when (lang) {
        AppLang.ID -> "Utama"
        AppLang.EN -> "General"
    }

    fun tabBubble(lang: AppLang) = when (lang) {
        AppLang.ID -> "Tampilan Balon"
        AppLang.EN -> "Bubble Styling"
    }

    fun tabEngine(lang: AppLang) = when (lang) {
        AppLang.ID -> "Mesin AI & Bahasa"
        AppLang.EN -> "AI Engines"
    }

    fun tabTips(lang: AppLang) = when (lang) {
        AppLang.ID -> "Panduan"
        AppLang.EN -> "Guide"
    }

    // Bubble Themes & Fonts
    fun bubbleThemeTitle(lang: AppLang) = when (lang) {
        AppLang.ID -> "Warna / Tema Balon"
        AppLang.EN -> "Bubble Theme"
    }

    fun bubbleThemeAuto(lang: AppLang) = when (lang) {
        AppLang.ID -> "Sesuai Komik"
        AppLang.EN -> "Match Comic"
    }

    fun bubbleThemeWhite(lang: AppLang) = when (lang) {
        AppLang.ID -> "Putih Klasik"
        AppLang.EN -> "Classic White"
    }

    fun bubbleThemeDark(lang: AppLang) = when (lang) {
        AppLang.ID -> "Mode Malam / OLED"
        AppLang.EN -> "Dark / OLED"
    }

    fun bubbleThemeSepia(lang: AppLang) = when (lang) {
        AppLang.ID -> "Kertas Sepia"
        AppLang.EN -> "Sepia Paper"
    }

    fun bubbleFontTitle(lang: AppLang) = when (lang) {
        AppLang.ID -> "Gaya Huruf / Font"
        AppLang.EN -> "Font Style"
    }

    fun bubbleFontComic(lang: AppLang) = when (lang) {
        AppLang.ID -> "Komika Axis (Komik/Webtoon)"
        AppLang.EN -> "Komika Axis (Comic/Webtoon)"
    }

    fun bubbleFontSans(lang: AppLang) = when (lang) {
        AppLang.ID -> "Modern Sans"
        AppLang.EN -> "Modern Sans"
    }

    fun bubbleFontSerif(lang: AppLang) = when (lang) {
        AppLang.ID -> "Serif Klasik"
        AppLang.EN -> "Classic Serif"
    }

    fun bubbleShadowTitle(lang: AppLang) = when (lang) {
        AppLang.ID -> "Bayangan Halus Balon (Drop Shadow)"
        AppLang.EN -> "Bubble Drop Shadow"
    }

    fun bubbleShadowDesc(lang: AppLang) = when (lang) {
        AppLang.ID -> "Memberikan efek bayangan melayang agar tulisan terpisah jelas dari gambar komik"
        AppLang.EN -> "Adds a soft depth shadow so translated dialogue floats cleanly over art"
    }

    fun autoDimTitle(lang: AppLang) = when (lang) {
        AppLang.ID -> "Mode Hantu Tombol (Auto-Dim)"
        AppLang.EN -> "Ghost Mode (Auto-Dim)"
    }

    fun autoDimDesc(lang: AppLang) = when (lang) {
        AppLang.ID -> "Meredupkan tombol melayang jika tidak disentuh selama 3.5 detik agar tidak menghalangi panel komik"
        AppLang.EN -> "Fades the floating button to 30% when idle so it never obscures comic panels"
    }

    // Live Preview
    fun livePreviewTitle(lang: AppLang) = when (lang) {
        AppLang.ID -> "Pratinjau Langsung (Live Preview)"
        AppLang.EN -> "Live Bubble Preview"
    }

    fun livePreviewSampleOriginal(lang: AppLang) = "¡No te rindas jamás!"

    fun livePreviewSampleTranslated(lang: AppLang) = when (lang) {
        AppLang.ID -> "Jangan pernah menyerah!"
        AppLang.EN -> "Never give up!"
    }

    // Quick Menu & Actions
    fun menuCopyText(lang: AppLang) = when (lang) {
        AppLang.ID -> "📋  Salin teks terjemahan"
        AppLang.EN -> "📋  Copy translated text"
    }

    fun toastCopied(lang: AppLang) = when (lang) {
        AppLang.ID -> "Teks terjemahan disalin ke papan klip!"
        AppLang.EN -> "Translation copied to clipboard!"
    }

    // Gemini Tester
    fun geminiTester(lang: AppLang) = when (lang) {
        AppLang.ID -> "Tes Koneksi Gemini AI"
        AppLang.EN -> "Test Gemini AI Connection"
    }

    fun geminiTesting(lang: AppLang) = when (lang) {
        AppLang.ID -> "Menghubungkan ke Gemini…"
        AppLang.EN -> "Connecting to Gemini…"
    }

    fun geminiSuccess(lang: AppLang) = when (lang) {
        AppLang.ID -> "✅ Koneksi Sukses! API Key aktif dan siap dipakai."
        AppLang.EN -> "✅ Connection Success! API Key is active and ready."
    }

    fun geminiFailed(lang: AppLang) = when (lang) {
        AppLang.ID -> "❌ Gagal terhubung: "
        AppLang.EN -> "❌ Connection failed: "
    }
}
