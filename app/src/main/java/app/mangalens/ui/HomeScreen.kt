package app.mangalens.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mangalens.R
import app.mangalens.capture.ScreenCaptureService
import app.mangalens.settings.AiReasoning
import app.mangalens.settings.AiVisionMode
import app.mangalens.settings.AppLang
import app.mangalens.settings.AppSettings
import app.mangalens.settings.BubbleFont
import app.mangalens.settings.BubbleTheme
import app.mangalens.settings.CaptureMode
import app.mangalens.settings.EngineKind
import app.mangalens.settings.LlmProvider
import app.mangalens.settings.SettingsRepository
import app.mangalens.settings.SourceLang
import app.mangalens.settings.TargetLang
import app.mangalens.translate.GoogleFreeEngine
import app.mangalens.translate.LlmEngine
import app.mangalens.translate.MlKitEngine
import app.mangalens.translate.ModelCatalog
import app.mangalens.update.UpdateChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    repo: SettingsRepository,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onGrantOverlay: () -> Unit,
) {
    val context = LocalContext.current
    val settings by repo.flow.collectAsState(initial = AppSettings())
    val running by ScreenCaptureService.running.collectAsState()

    var overlayGranted by remember {
        mutableStateOf(android.provider.Settings.canDrawOverlays(context))
    }
    LaunchedEffect(Unit) {
        while (true) {
            overlayGranted = android.provider.Settings.canDrawOverlays(context)
            delay(1000)
        }
    }

    var update by remember { mutableStateOf<UpdateChecker.Update?>(null) }
    LaunchedEffect(Unit) {
        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        if (installed != null) {
            update = UpdateChecker.check(
                currentVersion = installed,
                sdkInt = Build.VERSION.SDK_INT,
                signingTrack = UpdateChecker.installedSigningTrack(context),
            )
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        if (settings.appLang == AppLang.ID) "Terjemahan" else "Translate",
        if (settings.appLang == AppLang.ID) "Tampilan Balon" else "Bubble Style",
        if (settings.appLang == AppLang.ID) "Panduan & Info" else "Guide & Info",
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .fillMaxSize()
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Header(settings.appLang)
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                fontSize = 13.sp,
                            )
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        StatusCard(running, overlayGranted, settings.appLang, onStart, onStop, onGrantOverlay)
                        Spacer(Modifier.height(14.dp))
                        LanguageCard(settings, repo)
                        Spacer(Modifier.height(14.dp))
                        EngineCard(settings, repo)
                        Spacer(Modifier.height(14.dp))
                        ModeCard(settings, repo)
                    }
                    1 -> {
                        LivePreviewCard(settings)
                        Spacer(Modifier.height(14.dp))
                        BubbleStyleCard(settings, repo)
                    }
                    2 -> {
                        TipsCard(settings.appLang)
                        Spacer(Modifier.height(14.dp))
                        AppLanguageCard(settings, repo)
                        update?.let {
                            Spacer(Modifier.height(14.dp))
                            UpdateCard(it)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Header(lang: AppLang) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("文A", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(AppStrings.appTitle(lang), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                AppStrings.appSubtitle(lang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppLanguageCard(settings: AppSettings, repo: SettingsRepository) {
    val scope = rememberCoroutineScope()
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle(AppStrings.sectionAppLang(settings.appLang))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("🇮🇩  Bahasa Indonesia", settings.appLang == AppLang.ID) {
                    scope.launch { repo.setAppLang(AppLang.ID) }
                }
                Chip("🇬🇧  English", settings.appLang == AppLang.EN) {
                    scope.launch { repo.setAppLang(AppLang.EN) }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    running: Boolean,
    overlayGranted: Boolean,
    lang: AppLang,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onGrantOverlay: () -> Unit,
) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(
                            if (running) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.outline,
                            CircleShape
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (running) AppStrings.translatingScreen(lang) else AppStrings.notRunning(lang),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(10.dp))
            if (!overlayGranted) {
                Text(
                    AppStrings.overlayPermissionStep(lang),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onGrantOverlay) { Text(AppStrings.grantOverlayBtn(lang)) }
                Spacer(Modifier.height(10.dp))
            }
            if (running) {
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text(AppStrings.stopBtn(lang)) }
            } else {
                Button(
                    onClick = onStart,
                    enabled = overlayGranted,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(AppStrings.startBtn(lang, overlayGranted)) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                AppStrings.howToUse(lang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModeCard(settings: AppSettings, repo: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val lang = settings.appLang
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle(AppStrings.mode(lang))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(AppStrings.modeAuto(lang), settings.mode == CaptureMode.AUTO) {
                    scope.launch { repo.setMode(CaptureMode.AUTO) }
                }
                Chip(AppStrings.modeManual(lang), settings.mode == CaptureMode.MANUAL) {
                    scope.launch { repo.setMode(CaptureMode.MANUAL) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (settings.mode == CaptureMode.AUTO) {
                    if (lang == AppLang.ID) "Menerjemahkan secara otomatis saat kamu membaca dan layar berhenti bergerak sejenak."
                    else "Translates automatically as you read whenever the page stays still."
                } else {
                    if (lang == AppLang.ID) "Hanya menerjemahkan saat kamu mengetuk tombol melayang 文A."
                    else "Translates only when you tap the floating 文A button."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (settings.mode == CaptureMode.AUTO) {
                Spacer(Modifier.height(12.dp))
                LabeledSlider(
                    AppStrings.reactionTime(lang),
                    settings.stabilityMs.toFloat(),
                    200f..900f,
                    { "${it.toInt()} ms" },
                ) { scope.launch { repo.setStabilityMs(it.toInt()) } }
            }
        }
    }
}

@Composable
private fun LanguageCard(settings: AppSettings, repo: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val lang = settings.appLang
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle(AppStrings.sourceLanguage(lang))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Chip("Auto", settings.sourceLang == SourceLang.AUTO) {
                    scope.launch { repo.setSourceLang(SourceLang.AUTO) }
                }
                Chip("한국어 (Korea)", settings.sourceLang == SourceLang.KO) {
                    scope.launch { repo.setSourceLang(SourceLang.KO) }
                }
                Chip("日本語 (Jepang)", settings.sourceLang == SourceLang.JA) {
                    scope.launch { repo.setSourceLang(SourceLang.JA) }
                }
                Chip("中文 (China)", settings.sourceLang == SourceLang.ZH) {
                    scope.launch { repo.setSourceLang(SourceLang.ZH) }
                }
                Chip("English", settings.sourceLang == SourceLang.EN) {
                    scope.launch { repo.setSourceLang(SourceLang.EN) }
                }
                Chip("Español (Spanyol)", settings.sourceLang == SourceLang.ES) {
                    scope.launch { repo.setSourceLang(SourceLang.ES) }
                }
            }
            Spacer(Modifier.height(14.dp))
            SectionTitle(AppStrings.translateTo(lang))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("🇮🇩  Indonesia", settings.targetLang == TargetLang.ID) {
                    scope.launch { repo.setTargetLang(TargetLang.ID) }
                }
                Chip("🇬🇧  English", settings.targetLang == TargetLang.EN) {
                    scope.launch { repo.setTargetLang(TargetLang.EN) }
                }
            }
        }
    }
}

@Composable
private fun LivePreviewCard(settings: AppSettings) {
    val lang = settings.appLang
    var peeked by remember { mutableStateOf(false) }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(AppStrings.livePreviewTitle(lang))
                Text(
                    if (lang == AppLang.ID) "Ketuk balon untuk uji intip" else "Tap bubble to peek",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))

            // Faux Comic Panel Frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Color(0xFF1E212B),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background comic panel art simulation
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("💥 💬", fontSize = 22.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        AppStrings.livePreviewSampleOriginal(lang),
                        color = Color(0xFF8C92AC),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Translated floating bubble
                if (!peeked) {
                    val (bgColor, textColor) = when (settings.bubbleTheme) {
                        BubbleTheme.WHITE -> Color.White to Color(0xFF17181C)
                        BubbleTheme.DARK -> Color(0xFF181920) to Color(0xFFF4F5F8)
                        BubbleTheme.SEPIA -> Color(0xFFFFF8E7) to Color(0xFF2C2218)
                        BubbleTheme.AUTO -> Color.White to Color(0xFF17181C)
                    }

                    val fontFamily = when (settings.bubbleFont) {
                        BubbleFont.COMIC -> FontFamily(Font(R.font.komika_axis))
                        BubbleFont.SANS -> FontFamily.SansSerif
                        BubbleFont.SERIF -> FontFamily.Serif
                    }

                    Box(
                        modifier = Modifier
                            .clickable { peeked = true }
                            .then(
                                if (settings.bubbleShadow) {
                                    Modifier
                                        .background(
                                            Color(0x35000000),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .padding(bottom = 2.dp)
                                } else Modifier
                            )
                            .background(bgColor, RoundedCornerShape(14.dp))
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = AppStrings.livePreviewSampleTranslated(lang),
                            color = textColor,
                            fontSize = (14 * settings.textScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            LaunchedEffect(peeked) {
                if (peeked) {
                    delay(1200)
                    peeked = false
                }
            }
        }
    }
}

@Composable
private fun BubbleStyleCard(settings: AppSettings, repo: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val lang = settings.appLang

    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle(AppStrings.bubbleThemeTitle(lang))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Chip(AppStrings.bubbleThemeAuto(lang), settings.bubbleTheme == BubbleTheme.AUTO) {
                    scope.launch { repo.setBubbleTheme(BubbleTheme.AUTO) }
                }
                Chip(AppStrings.bubbleThemeWhite(lang), settings.bubbleTheme == BubbleTheme.WHITE) {
                    scope.launch { repo.setBubbleTheme(BubbleTheme.WHITE) }
                }
                Chip(AppStrings.bubbleThemeDark(lang), settings.bubbleTheme == BubbleTheme.DARK) {
                    scope.launch { repo.setBubbleTheme(BubbleTheme.DARK) }
                }
                Chip(AppStrings.bubbleThemeSepia(lang), settings.bubbleTheme == BubbleTheme.SEPIA) {
                    scope.launch { repo.setBubbleTheme(BubbleTheme.SEPIA) }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionTitle(AppStrings.bubbleFontTitle(lang))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Chip(AppStrings.bubbleFontComic(lang), settings.bubbleFont == BubbleFont.COMIC) {
                    scope.launch { repo.setBubbleFont(BubbleFont.COMIC) }
                }
                Chip(AppStrings.bubbleFontSans(lang), settings.bubbleFont == BubbleFont.SANS) {
                    scope.launch { repo.setBubbleFont(BubbleFont.SANS) }
                }
                Chip(AppStrings.bubbleFontSerif(lang), settings.bubbleFont == BubbleFont.SERIF) {
                    scope.launch { repo.setBubbleFont(BubbleFont.SERIF) }
                }
            }

            Spacer(Modifier.height(14.dp))
            LabeledSlider(
                AppStrings.textSize(lang),
                settings.textScale,
                0.8f..1.5f,
                { "${(it * 100).toInt()}%" },
            ) { scope.launch { repo.setTextScale(it) } }

            Spacer(Modifier.height(12.dp))
            // Bubble Drop Shadow Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(AppStrings.bubbleShadowTitle(lang), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(AppStrings.bubbleShadowDesc(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = settings.bubbleShadow,
                    onCheckedChange = { checked -> scope.launch { repo.setBubbleShadow(checked) } }
                )
            }

            Spacer(Modifier.height(14.dp))
            // Auto-Dim Ghost Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(AppStrings.autoDimTitle(lang), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(AppStrings.autoDimDesc(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = settings.autoDimFloating,
                    onCheckedChange = { checked -> scope.launch { repo.setAutoDimFloating(checked) } }
                )
            }

            Spacer(Modifier.height(14.dp))
            LabeledSlider(
                AppStrings.ignoreTop(lang),
                settings.ignoreTopPct,
                0f..0.15f,
                { "${(it * 100).toInt()}%" },
            ) { scope.launch { repo.setIgnoreTopPct(it) } }

            LabeledSlider(
                AppStrings.ignoreBottom(lang),
                settings.ignoreBottomPct,
                0f..0.15f,
                { "${(it * 100).toInt()}%" },
            ) { scope.launch { repo.setIgnoreBottomPct(it) } }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun EngineCard(settings: AppSettings, repo: SettingsRepository) {
    val scope = rememberCoroutineScope()
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var keyDraft by remember(settings.provider) { mutableStateOf(settings.apiKey) }
    var keyEdited by remember(settings.provider) { mutableStateOf(false) }
    var modelDraft by remember(settings.provider) { mutableStateOf(settings.model) }
    var modelEdited by remember(settings.provider) { mutableStateOf(false) }
    var customUrlDraft by remember { mutableStateOf(settings.customUrl) }
    var customUrlEdited by remember { mutableStateOf(false) }

    LaunchedEffect(settings.provider, settings.apiKey) {
        if (!keyEdited) keyDraft = settings.apiKey
    }
    LaunchedEffect(settings.provider, settings.model) {
        if (!modelEdited) modelDraft = settings.model
    }
    LaunchedEffect(settings.customUrl) {
        if (!customUrlEdited) customUrlDraft = settings.customUrl
    }
    val draftSettings = settings.copy(
        apiKey = keyDraft.trim(),
        model = modelDraft.trim(),
        customUrl = customUrlDraft.trim(),
    )

    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle(if (settings.appLang == AppLang.ID) "Pilihan Mesin Terjemahan" else "Translation Engine")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Free · Google", settings.engine == EngineKind.GOOGLE) {
                    scope.launch { repo.setEngine(EngineKind.GOOGLE) }
                }
                Chip("AI Pro ✨ (Gemini)", settings.engine == EngineKind.LLM) {
                    scope.launch { repo.setEngine(EngineKind.LLM) }
                }
                Chip("Offline", settings.engine == EngineKind.MLKIT) {
                    scope.launch { repo.setEngine(EngineKind.MLKIT) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (settings.engine) {
                    EngineKind.GOOGLE -> if (settings.appLang == AppLang.ID)
                        "Langsung aktif tanpa setup. Cepat untuk penggunaan sehari-hari."
                    else
                        "Works instantly, no setup. Solid everyday quality."
                    EngineKind.LLM -> if (settings.appLang == AppLang.ID)
                        "Kualitas seperti komik resmi: memahami alur cerita, konteks visual, nama karakter, dan panggilan alami. Gratis via Gemini API."
                    else
                        "Feels like an official release: reads whole pages with story memory and natural tone. Needs an API key — Gemini's is free."
                    EngineKind.MLKIT -> if (settings.appLang == AppLang.ID)
                        "100% offline setelah unduh model ~30 MB. Kualitas standar."
                    else
                        "100% offline after a one-time ~30 MB model download per language."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (settings.engine == EngineKind.LLM) {
                Spacer(Modifier.height(12.dp))
                ProviderPicker(settings, repo)
                Spacer(Modifier.height(10.dp))
                var showKey by remember(settings.provider) { mutableStateOf(false) }
                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = {
                        keyDraft = it
                        keyEdited = true
                        val provider = settings.provider
                        scope.launch { repo.setApiKey(provider, it.trim()) }
                    },
                    label = { Text(apiKeyLabel(settings.provider)) },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        Text(
                            if (showKey) "sembunyikan" else "lihat",
                            modifier = Modifier
                                .clickable { showKey = !showKey }
                                .padding(end = 10.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Dedicated Gemini Connection Tester
                if (settings.provider == LlmProvider.GEMINI) {
                    Spacer(Modifier.height(8.dp))
                    var testerLoading by remember { mutableStateOf(false) }
                    var testStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

                    OutlinedButton(
                        onClick = {
                            val key = keyDraft.trim()
                            if (key.isBlank()) {
                                testStatus = false to if (settings.appLang == AppLang.ID) "Tempel API Key terlebih dahulu." else "Please enter your API Key first."
                                return@OutlinedButton
                            }
                            testerLoading = true
                            testStatus = null
                            scope.launch {
                                try {
                                    val list = ModelCatalog.gemini(key)
                                    if (list.isNotEmpty()) {
                                        testStatus = true to AppStrings.geminiSuccess(settings.appLang)
                                    } else {
                                        testStatus = false to (AppStrings.geminiFailed(settings.appLang) + "Tidak ada model yang ditemukan")
                                    }
                                } catch (e: Exception) {
                                    testStatus = false to (AppStrings.geminiFailed(settings.appLang) + (e.message ?: "Koneksi bermasalah"))
                                } finally {
                                    testerLoading = false
                                }
                            }
                        },
                        enabled = !testerLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (testerLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(AppStrings.geminiTesting(settings.appLang))
                        } else {
                            Text("⚡ " + AppStrings.geminiTester(settings.appLang))
                        }
                    }
                    testStatus?.let { (success, msg) ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = modelDraft,
                    onValueChange = {
                        modelDraft = it
                        modelEdited = true
                        val provider = settings.provider
                        scope.launch { repo.setModel(provider, it.trim()) }
                    },
                    label = { Text("Model (kosong = ${draftSettings.copy(model = "").effectiveModel()})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (settings.provider == LlmProvider.GEMINI) {
                    Spacer(Modifier.height(6.dp))
                    GeminiModelRow(apiKey = keyDraft.trim()) { picked ->
                        modelDraft = picked
                        modelEdited = true
                        val provider = settings.provider
                        scope.launch { repo.setModel(provider, picked) }
                    }
                }
                if (settings.provider == LlmProvider.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customUrlDraft,
                        onValueChange = {
                            customUrlDraft = it
                            customUrlEdited = true
                            scope.launch { repo.setCustomUrl(it.trim()) }
                        },
                        label = { Text("Chat-completions endpoint URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("AI Vision — izin AI membaca gambar komik langsung", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("AI Vision (Rekomendasi)", settings.aiVision != AiVisionMode.OFF) {
                        scope.launch { repo.setAiVision(AiVisionMode.AUTO) }
                    }
                    Chip("Hanya Teks", settings.aiVision == AiVisionMode.OFF) {
                        scope.launch { repo.setAiVision(AiVisionMode.OFF) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (settings.aiVision != AiVisionMode.OFF)
                        "AI membaca gambar komik langsung sehingga menangkap tulisan tangan, efek suara, dan teks meliuk yang luput oleh OCR."
                    else
                        "Hanya teks hasil OCR yang dikirim (hemat kuota).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text("AI reasoning — kedalaman pemikiran AI per halaman", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Cepat (Fast)", settings.aiReasoning == AiReasoning.FAST) {
                        scope.launch { repo.setAiReasoning(AiReasoning.FAST) }
                    }
                    Chip("Seimbang (Balanced)", settings.aiReasoning == AiReasoning.BALANCED) {
                        scope.launch { repo.setAiReasoning(AiReasoning.BALANCED) }
                    }
                    Chip("Mendalam (Thorough)", settings.aiReasoning == AiReasoning.THOROUGH) {
                        scope.launch { repo.setAiReasoning(AiReasoning.THOROUGH) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Penghemat data — kompresi gambar halaman", settings.dataSaver) {
                        scope.launch { repo.setDataSaver(!settings.dataSaver) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Diagnostik — tampilkan garis deteksi balon", settings.diagnostics) {
                        scope.launch { repo.setDiagnostics(!settings.diagnostics) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val uriHandler = LocalUriHandler.current
                val keyHelp = providerKeyHelp(settings.provider)
                Text(
                    keyHelp.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                keyHelp.url?.let { url ->
                    Text(
                        keyHelp.linkLabel,
                        modifier = Modifier
                            .clickable { uriHandler.openUri(url) }
                            .padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (settings.engine == EngineKind.MLKIT) {
                Spacer(Modifier.height(12.dp))
                OfflineModelSection(settings)
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    enabled = !testing,
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            testResult = try {
                                val (sampleText, sampleLang) = when (settings.sourceLang) {
                                    SourceLang.ES -> "¡Hola! No te preocupes, te protegeré." to SourceLang.ES
                                    SourceLang.JA -> "大丈夫、僕が守ってあげる。" to SourceLang.JA
                                    SourceLang.ZH -> "没关系，我会保护你的。" to SourceLang.ZH
                                    SourceLang.EN -> "It's okay. I will protect you." to SourceLang.EN
                                    else -> "괜찮아. 내가 지켜줄게." to SourceLang.KO
                                }
                                val sample = listOf(sampleText)
                                val out = when (settings.engine) {
                                    EngineKind.LLM -> LlmEngine(draftSettings).translate(sample, sampleLang)
                                    EngineKind.MLKIT -> MlKitEngine().translate(sample, sampleLang, settings.targetLang)
                                    EngineKind.GOOGLE -> GoogleFreeEngine().translate(sample, sampleLang, settings.targetLang)
                                }
                                "“$sampleText” → “" + out.first() + "”"
                            } catch (e: Exception) {
                                "⚠ " + (e.message ?: "failed")
                            } finally {
                                testing = false
                            }
                        }
                    }
                ) { Text(if (testing) "Menerjemahkan…" else "Uji Terjemahan Contoh") }
            }
            testResult?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun OfflineModelSection(settings: AppSettings) {
    val scope = rememberCoroutineScope()
    val srcCode = MlKitEngine.sourceCode(settings.sourceLang)
    val tgtCode = MlKitEngine.targetCode(settings.targetLang)

    var srcDownloaded by remember(settings.sourceLang) { mutableStateOf(false) }
    var tgtDownloaded by remember(settings.targetLang) { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloadMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settings.sourceLang, settings.targetLang) {
        srcDownloaded = MlKitEngine.isModelDownloaded(srcCode)
        tgtDownloaded = MlKitEngine.isModelDownloaded(tgtCode)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Status Model Offline (ML Kit)",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Bahasa Asal ($srcCode):", style = MaterialTheme.typography.bodySmall)
                Text(
                    if (srcDownloaded) "Siap Digunakan ✅" else "Belum Diunduh ❌",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (srcDownloaded) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Bahasa Tujuan ($tgtCode):", style = MaterialTheme.typography.bodySmall)
                Text(
                    if (tgtDownloaded) "Siap Digunakan ✅" else "Belum Diunduh ❌",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tgtDownloaded) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(10.dp))
            if (!srcDownloaded || !tgtDownloaded) {
                Button(
                    onClick = {
                        downloading = true
                        downloadMsg = null
                        scope.launch {
                            val sOk = if (!srcDownloaded) MlKitEngine.downloadModel(srcCode) else true
                            val tOk = if (!tgtDownloaded) MlKitEngine.downloadModel(tgtCode) else true
                            srcDownloaded = MlKitEngine.isModelDownloaded(srcCode)
                            tgtDownloaded = MlKitEngine.isModelDownloaded(tgtCode)
                            downloading = false
                            downloadMsg = if (srcDownloaded && tgtDownloaded) {
                                "✅ Model berhasil diunduh! Mode offline siap digunakan 100% tanpa internet."
                            } else {
                                "⚠ Gagal mengunduh model. Hubungkan internet lalu coba lagi."
                            }
                        }
                    },
                    enabled = !downloading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (downloading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Mengunduh model offline (~30 MB)...")
                    } else {
                        Text("📥 Unduh Model Offline Sekarang")
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "🎉 Model offline lengkap! Dapat menerjemahkan 100% tanpa internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            downloadMsg?.let { msg ->
                Spacer(Modifier.height(6.dp))
                Text(msg, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ProviderPicker(settings: AppSettings, repo: SettingsRepository) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(
                "Provider: " + when (settings.provider) {
                    LlmProvider.ANTHROPIC -> "Anthropic Claude"
                    LlmProvider.OPENAI -> "OpenAI"
                    LlmProvider.GEMINI -> "Google Gemini (Gratis)"
                    LlmProvider.OPENROUTER -> "OpenRouter"
                    LlmProvider.CUSTOM -> "Custom endpoint"
                }
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LlmProvider.entries.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (p) {
                                LlmProvider.ANTHROPIC -> "Anthropic Claude"
                                LlmProvider.OPENAI -> "OpenAI"
                                LlmProvider.GEMINI -> "Google Gemini (Gratis tier)"
                                LlmProvider.OPENROUTER -> "OpenRouter"
                                LlmProvider.CUSTOM -> "Custom endpoint"
                            }
                        )
                    },
                    onClick = {
                        open = false
                        scope.launch { repo.setProvider(p) }
                    }
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onCommit: (Float) -> Unit,
) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                display(v),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = v,
            onValueChange = { v = it },
            valueRange = range,
            onValueChangeFinished = { onCommit(v) }
        )
    }
}

@Composable
private fun UpdateCard(update: UpdateChecker.Update) {
    val uriHandler = LocalUriHandler.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Update tersedia", fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        update.requiresReinstall ->
                            "MangaLens ${update.version} telah rilis. Unduh APK baru untuk memperbarui."
                        else -> "MangaLens ${update.version} telah rilis."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(onClick = { uriHandler.openUri(update.url) }) {
                Text(if (update.requiresReinstall) "Unduh" else "Unduh APK")
            }
        }
    }
}

private data class ProviderKeyHelp(val message: String, val linkLabel: String = "", val url: String? = null)

private fun apiKeyLabel(provider: LlmProvider): String = when (provider) {
    LlmProvider.ANTHROPIC -> "Anthropic API key"
    LlmProvider.OPENAI -> "OpenAI API key"
    LlmProvider.GEMINI -> "Gemini API key"
    LlmProvider.OPENROUTER -> "OpenRouter API key"
    LlmProvider.CUSTOM -> "Bearer token (optional)"
}

private fun providerKeyHelp(provider: LlmProvider): ProviderKeyHelp = when (provider) {
    LlmProvider.ANTHROPIC -> ProviderKeyHelp(
        "Kunci ini disimpan hanya untuk Anthropic.",
        "Buat kunci Anthropic →",
        "https://console.anthropic.com/settings/keys",
    )
    LlmProvider.OPENAI -> ProviderKeyHelp(
        "Kunci ini disimpan hanya untuk OpenAI.",
        "Buat kunci OpenAI →",
        "https://platform.openai.com/api-keys",
    )
    LlmProvider.GEMINI -> ProviderKeyHelp(
        "Gemini menyediakan kuota gratis (tanpa perlu kartu kredit). Kunci disimpan aman hanya untuk Gemini.",
        "Dapatkan Gemini API Key Gratis →",
        "https://aistudio.google.com/apikey",
    )
    LlmProvider.OPENROUTER -> ProviderKeyHelp(
        "Kunci ini disimpan khusus untuk OpenRouter.",
        "Buat kunci OpenRouter →",
        "https://openrouter.ai/settings/keys",
    )
    LlmProvider.CUSTOM -> ProviderKeyHelp(
        "Bearer token opsional untuk endpoint custom.",
    )
}

@Composable
private fun GeminiModelRow(apiKey: String, onPick: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf<List<ModelCatalog.LiveModel>>(emptyList()) }
    Box {
        OutlinedButton(
            enabled = !loading,
            onClick = {
                if (apiKey.isBlank()) {
                    error = "Tempel API Key terlebih dahulu."
                    return@OutlinedButton
                }
                error = null
                if (models.isNotEmpty()) {
                    open = true
                    return@OutlinedButton
                }
                loading = true
                scope.launch {
                    try {
                        models = ModelCatalog.gemini(apiKey)
                        open = models.isNotEmpty()
                        if (models.isEmpty()) error = "Google tidak mengembalikan model yang dapat digunakan."
                    } catch (e: Exception) {
                        error = "Gagal mengambil daftar model: " + (e.message ?: "koneksi bermasalah")
                    } finally {
                        loading = false
                    }
                }
            }
        ) { Text(if (loading) "Mengambil daftar model live…" else "Pilih dari daftar model Google live ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            models.forEach { m ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(m.label)
                            Text(
                                m.id,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        open = false
                        onPick(m.id)
                    }
                )
            }
        }
    }
    error?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun TipsCard(lang: AppLang) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle(AppStrings.sectionTips(lang))
            Spacer(Modifier.height(8.dp))
            AppStrings.tipsContent(lang).forEach { (title, desc) ->
                Text(
                    "• $title: $desc",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
