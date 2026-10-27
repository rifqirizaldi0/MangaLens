package app.mangalens.capture

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import app.mangalens.MainActivity
import app.mangalens.MangaLensApp
import app.mangalens.R
import app.mangalens.ocr.OcrEngine
import app.mangalens.overlay.OverlayController
import app.mangalens.overlay.RenderBubble
import app.mangalens.pipeline.TranslatePipeline
import app.mangalens.pipeline.UpgradeMerge
import app.mangalens.settings.AppLang
import app.mangalens.settings.AppSettings
import app.mangalens.settings.CaptureMode
import app.mangalens.settings.SettingsRepository
import app.mangalens.translate.CastBook
import app.mangalens.translate.GlossaryStore
import app.mangalens.translate.TranslationCache
import app.mangalens.translate.TranslationService
import app.mangalens.translate.WorkMemory
import app.mangalens.ui.AppStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that owns the MediaProjection capture and drives the live
 * translation loop:
 *
 *   SCANNING --(screen stable)--> TRANSLATING --(done)--> SHOWING
 *      ^                                                     |
 *      +----------------(scroll / motion detected)-----------+
 *
 * While SHOWING, overlays sit on top of the page; any scroll clears them
 * instantly so the reader never sees misplaced text. Because overlays are
 * cleared before every capture, the OCR never sees our own English output.
 */
class ScreenCaptureService : Service(), OverlayController.Listener {

    companion object {
        const val ACTION_START = "app.mangalens.action.START"
        const val ACTION_STOP = "app.mangalens.action.STOP"
        const val ACTION_TRANSLATE_NOW = "app.mangalens.action.TRANSLATE_NOW"
        const val ACTION_TOGGLE_PAUSE = "app.mangalens.action.TOGGLE_PAUSE"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"

        private const val NOTIF_ID = 41
        private const val MOTION_THRESHOLD = 4.0

        /**
         * While overlays are shown, require a larger motion to trigger a clear.
         * This prevents pinch-to-zoom and tiny finger movements from dismissing
         * translations the user is still reading.
         */
        private const val MOTION_THRESHOLD_SHOWING = 18.0

        /**
         * Share of the visible page that must differ from the page we
         * translated before it counts as a new page.
         */
        private const val PAGE_CHANGE_FRACTION = 0.08

        private const val SLOW_SCROLL_SAMPLE_MS = 420L

        /** Thumb rows of drift that count as scrolling to a new section. */
        private const val SLOW_SCROLL_MIN_ROWS = 8

        /**
         * Quiet time after which the frame is read ahead of the stability
         * window. OCR and balloon detection are the slow half of a pass and
         * need nothing but the frame, so they start as soon as the screen
         * settles; by the time the reader has provably stopped, the page is
         * usually already read and only translation remains. Painting still
         * waits for the full window, so a brief pause never flashes cards.
         */
        private const val EARLY_ANALYSIS_MS = 150L

        /**
         * How far (thumb mean difference) the live frame may have drifted
         * from the frame read ahead before that reading is thrown away.
         * Identical frames differ by capture noise only, well under one.
         */
        private const val PREPARED_MAX_DRIFT = 1.2

        /**
         * Spacing between the frames actually looked at. A dozen a second
         * is plenty for a 350 ms stability window, and every frame not
         * looked at is a full-screen copy not made. The last frame of a
         * burst is never skipped, whatever the spacing — see [FrameGate].
         */
        private const val FRAME_INTERVAL_MS = 80L

        /**
         * How long the cells a mask has just given up stay excluded. A card
         * is on screen a frame or two after it is placed and gone a frame
         * or two after it is cleared, and the capture sees each frame later
         * still; for that long the mask and the screen disagree, and the
         * cells that still show a card must not be read as the page having
         * changed.
         */
        private const val MASK_GRACE_MS = 300L

        /**
         * Passes a page change may cancel mid-stream, one after another,
         * before the next pass is left to finish. A region that never stops
         * changing — an animated banner, a video — trips the page-change
         * check as surely as a swap does, and left unchecked it would cancel
         * every pass, and the reader would never see a translation at all.
         * Two in a row is a reader skimming; from the third the pass runs
         * to the end, and the page is watched again once it has.
         */
        private const val MAX_MID_PASS_CANCELS = 2

        val running = MutableStateFlow(false)
    }

    private enum class State { SCANNING, TRANSLATING, SHOWING }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settingsRepo: SettingsRepository

    @Volatile private var settings = AppSettings()

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    @Volatile private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private var controller: OverlayController? = null
    private val ocr = OcrEngine()
    private val cache = TranslationCache()
    // lazy: these need a Context, which a Service only has after construction
    private val glossary by lazy { GlossaryStore(this) }
    private val cast by lazy { CastBook(this) }

    /**
     * Keeps each series' glossary, cast and story context to itself. Reading
     * several works in a sitting otherwise pools them, and the memory that
     * makes one story consistent then contradicts the next.
     */
    private val works by lazy { WorkMemory(this, glossary, cast) }
    private val translation by lazy { TranslationService(cache, glossary, cast) }
    private val pipeline by lazy { TranslatePipeline(ocr, translation, cache, glossary, cast) }

    private val frameLock = Any()
    private var latestBitmap: Bitmap? = null
    private var prevThumb: IntArray? = null

    /**
     * The page as it looked when we translated it — taken from the very
     * bitmap the pipeline processed, never from a later frame that may
     * already show something else. While overlays are up, every frame is
     * compared against this rather than against the frame before it: a
     * tap-to-turn swap produces one changed frame and then stillness, so
     * frame-to-frame differencing has a single chance to catch it and
     * cumulative comparison has every frame.
     */
    @Volatile private var shownThumb: IntArray? = null

    /**
     * The thumb cells our own overlays currently cover: the cards, and the
     * floating button with its status pill. All of it is captured along
     * with the page, so these cells show us, not the reader's content, and
     * every comparison excludes them. Maintained by [paintCards],
     * [clearCards] and the controls' own layout changes, so it is always
     * the truth about what is on screen — including the fast draft that
     * paints mid-translation and a pill that comes and goes on its own —
     * and it keeps the cells just given up for [MASK_GRACE_MS], see
     * [setOverlayMask].
     */
    @Volatile private var overlayMask: BooleanArray? = null

    /** Bumped on every mask change, so a stale narrowing never lands. Main thread only. */
    private var maskEpoch = 0

    // Reference for slow-scroll drift detection (capture thread only).
    private var slowRefThumb: IntArray? = null
    private var slowRefAt = 0L

    /** The newest frame's thumb, for judging whether a frame read ahead is still the one on screen. */
    @Volatile private var latestThumb: IntArray? = null

    /**
     * A frame grabbed and read ahead of the stability window: the page
     * analysis runs while the loop is still waiting to be sure the reader
     * has stopped. Main thread only; [preparing] mirrors it for the
     * capture thread, which must cancel it the moment the screen moves.
     */
    private class Prepared(
        val bitmap: Bitmap,
        val job: Deferred<Pair<IntArray, TranslatePipeline.Analysis>>,
    )

    private var prepared: Prepared? = null
    @Volatile private var preparing = false

    /**
     * Passes in flight, so a cancelled pass's late cleanup can never switch
     * the busy ring off under a newer pass. Main thread only.
     */
    private var busyDepth = 0

    // Reused frame buffers (capture thread only): the display feeds frames at
    // refresh rate, but scroll detection only needs ~12 fps, and allocating a
    // full-screen bitmap per frame melts batteries. One buffer holds the
    // latest complete frame; the other is being written.
    private var frameA: Bitmap? = null
    private var frameB: Bitmap? = null
    private val gate = FrameGate(FRAME_INTERVAL_MS)
    private val recheckRunnable = Runnable { recheck() }

    private var heldBitmap: Bitmap? = null
    private var heldAt = 0L

    @Volatile private var lastMotionAt = 0L

    /** Passes cancelled by the page-change check, one after another; see [MAX_MID_PASS_CANCELS]. */
    @Volatile private var midPassCancels = 0
    @Volatile private var lastFrameAt = 0L
    @Volatile private var suppressUntil = 0L
    @Volatile private var state = State.SCANNING
    @Volatile private var paused = false

    /**
     * Bubbles frozen by [onTogglePause]. While paused, overlays stay visible
     * even when the screen moves. Cleared when the user resumes.
     */
    private var frozenBubbles: List<RenderBubble>? = null

    private var translateJob: Job? = null
    private var lastShown: List<RenderBubble> = emptyList()
    private var capW = 0
    private var capH = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepo = SettingsRepository(this)
        scope.launch {
            settingsRepo.flow.collect { s ->
                settings = s
                controller?.autoDim = s.autoDimFloating
                controller?.bubbleView?.let { v ->
                    v.textScale = s.textScale
                    v.bgOpacity = s.bgOpacity
                    v.bubbleTheme = s.bubbleTheme
                    v.bubbleFont = s.bubbleFont
                    v.bubbleShadow = s.bubbleShadow
                    v.postInvalidate()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
                if (code != Activity.RESULT_OK || data == null) {
                    stopSelf()
                } else {
                    startAsForeground()
                    startProjection(code, data)
                }
            }
            ACTION_STOP -> stopSelf()
            ACTION_TRANSLATE_NOW -> onTranslateNow()
            ACTION_TOGGLE_PAUSE -> onTogglePause()
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        )
    }

    private fun startProjection(code: Int, data: Intent) {
        if (projection != null) return
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val mp = try {
            mpm.getMediaProjection(code, data)
        } catch (e: Exception) {
            null
        }
        if (mp == null) {
            stopSelf()
            return
        }
        projection = mp
        val thread = HandlerThread("mangalens-capture").also { it.start() }
        captureThread = thread
        captureHandler = Handler(thread.looper)
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                scope.launch { stopSelf() }
            }
        }, captureHandler)
        setupDisplay()
        controller = OverlayController(this, this).also { it.attach() }
        controller?.autoDim = settings.autoDimFloating
        controller?.bubbleView?.let { v ->
            v.textScale = settings.textScale
            v.bgOpacity = settings.bgOpacity
            v.bubbleTheme = settings.bubbleTheme
            v.bubbleFont = settings.bubbleFont
            v.bubbleShadow = settings.bubbleShadow
        }
        controller?.onFootprintChanged = { refreshOverlayMask() }
        refreshOverlayMask()
        running.value = true
        startTicker()
        setPill(if (settings.appLang == AppLang.ID) "MangaLens aktif — buka komikmu" else "MangaLens is live — open your manhwa", 2600)
    }

    private fun displaySize(): Triple<Int, Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            Triple(b.width(), b.height(), resources.configuration.densityDpi)
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            Triple(dm.widthPixels, dm.heightPixels, dm.densityDpi)
        }
    }

    private fun setupDisplay() {
        val (w, h, dpi) = displaySize()
        capW = w
        capH = h
        // Three buffers: one may be held across the frame interval (see
        // [FrameGate]), one is being acquired, and the display always has
        // one left to draw into.
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        reader.setOnImageAvailableListener({ r -> onFrame(r) }, captureHandler)
        val vd = virtualDisplay
        if (vd == null) {
            virtualDisplay = projection?.createVirtualDisplay(
                "mangalens",
                w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                captureHandler
            )
        } else {
            // Android 14+ allows only one createVirtualDisplay per projection,
            // so rotations are handled by resizing the existing one.
            vd.resize(w, h, dpi)
            vd.surface = reader.surface
        }
        imageReader?.let { old ->
            val handler = captureHandler
            if (handler != null && handler.looper.thread.isAlive) {
                handler.post { runCatching { old.close() } }
            } else {
                runCatching { old.close() }
            }
        }
        imageReader = reader
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (projection == null) return
        val (w, h, _) = displaySize()
        if (w != capW || h != capH) {
            translateJob?.cancel()
            discardPrepared()
            state = State.SCANNING
            shownThumb = null
            clearCards()
            releaseFrameBuffers()
            setupDisplay()
        }
    }

    /** Runs on the capture HandlerThread. */
    private fun onFrame(reader: ImageReader) {
        val image = try {
            reader.acquireLatestImage()
        } catch (_: Throwable) {
            null
        } ?: return

        try {
            val bmp = imageToBitmap(image) ?: return
            val now = SystemClock.uptimeMillis()
            lastFrameAt = now
            when (gate.arrival(now)) {
                FrameGate.Action.PROCESS -> {
                    heldBitmap = null
                    processBitmap(bmp, now)
                }
                FrameGate.Action.HOLD_AND_SCHEDULE -> {
                    heldBitmap = bmp
                    heldAt = now
                    captureHandler?.removeCallbacks(recheckRunnable)
                    captureHandler?.postDelayed(recheckRunnable, gate.delayFor(now))
                }
                FrameGate.Action.HOLD -> {
                    heldBitmap = bmp
                    heldAt = now
                }
            }
        } finally {
            runCatching { image.close() }
        }
    }

    /**
     * The interval since the last frame looked at is up: the frame held
     * meanwhile — or a newer one, if the reader has one — is looked at now.
     * Runs on the capture HandlerThread.
     */
    private fun recheck() {
        val now = SystemClock.uptimeMillis()
        val bmp = heldBitmap ?: run {
            gate.recheckIdle()
            return
        }
        val arrivedAt = heldAt
        when (gate.recheck(now)) {
            FrameGate.Action.PROCESS -> {
                heldBitmap = null
                processBitmap(bmp, arrivedAt)
            }
            else -> {
                captureHandler?.removeCallbacks(recheckRunnable)
                captureHandler?.postDelayed(recheckRunnable, gate.delayFor(now))
            }
        }
    }

    private fun processBitmap(bmp: Bitmap, now: Long) {
        try {
            synchronized(frameLock) {
                latestBitmap = bmp
            }
            val thumb = FrameStability.grayThumb(bmp, capW, capH)
            val diff = FrameStability.meanDiff(prevThumb, thumb)
            prevThumb = thumb
            latestThumb = thumb
            val mask = overlayMask
            if (now >= suppressUntil) {
                val motionBar = if (state == State.SHOWING) MOTION_THRESHOLD_SHOWING else MOTION_THRESHOLD
                if (diff > motionBar) {
                    lastMotionAt = now
                    slowRefThumb = null
                    if (state != State.SCANNING) scope.launch { onMotion() }
                    else if (preparing) scope.launch { discardPrepared() }
                    return
                }
                val ref = slowRefThumb
                if (ref == null) {
                    slowRefThumb = thumb
                    slowRefAt = now
                } else if (now - slowRefAt >= SLOW_SCROLL_SAMPLE_MS) {
                    val drift = FrameStability.verticalShift(ref, thumb, mask)
                    slowRefThumb = thumb
                    slowRefAt = now
                    if (kotlin.math.abs(drift) >= SLOW_SCROLL_MIN_ROWS) {
                        lastMotionAt = now
                        if (state != State.SCANNING) scope.launch { onMotion() }
                        else if (preparing) scope.launch { discardPrepared() }
                        return
                    }
                }
            }
            if (state == State.SHOWING || (state == State.TRANSLATING && midPassCancels < MAX_MID_PASS_CANCELS)) {
                val base = shownThumb ?: return
                val cov = FrameStability.coverage(mask)
                val pageBar = PAGE_CHANGE_FRACTION * when {
                    cov > 0.60 -> 0.30
                    cov > 0.35 -> 0.60
                    else -> 1.0
                }
                if (FrameStability.changedFraction(base, thumb, mask) > pageBar ||
                    kotlin.math.abs(FrameStability.verticalShift(base, thumb, mask)) >= SLOW_SCROLL_MIN_ROWS
                ) {
                    lastMotionAt = now
                    scope.launch { onMotion(pageChanged = true) }
                }
            }
        } catch (_: Throwable) {
            // Never crash foreground capture loop
        }
    }

    /**
     * Safely copies the frame into one of two reused buffers (stride-padded width).
     * Protected against JNI or Surface invalidation errors.
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val planes = image.planes
            if (planes.isNullOrEmpty()) return null
            val plane = planes[0] ?: return null
            val buffer = plane.buffer ?: return null
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            if (pixelStride <= 0 || rowStride <= 0) return null
            val strideW = rowStride / pixelStride
            val current = synchronized(frameLock) { latestBitmap }
            var target = if (current === frameA && frameA != null) frameB else frameA
            if (target == null || target.width != strideW || target.height != image.height) {
                target?.recycle()
                target = Bitmap.createBitmap(strideW, image.height, Bitmap.Config.ARGB_8888)
                if (current === frameA && frameA != null) frameB = target else frameA = target
            }
            buffer.rewind()
            target.copyPixelsFromBuffer(buffer)
            target
        } catch (_: Throwable) {
            null
        }
    }

    /** Serialized onto the capture thread so a buffer is never freed mid-write. */
    private fun releaseFrameBuffers() {
        val action = Runnable {
            heldBitmap = null
            synchronized(frameLock) { latestBitmap = null }
            frameA?.recycle()
            frameB?.recycle()
            frameA = null
            frameB = null
            prevThumb = null
            slowRefThumb = null
            slowRefAt = 0L
        }
        val handler = captureHandler
        if (handler != null && handler.looper.thread.isAlive &&
            Thread.currentThread() !== handler.looper.thread
        ) {
            handler.post(action)
        } else {
            action.run()
        }
    }

    /**
     * The screen moved, or — [pageChanged] — the page under the cards is no
     * longer the page they were written for. Main thread only.
     * When [paused], motion never clears cards — the overlay is frozen.
     */
    private fun onMotion(pageChanged: Boolean = false) {
        if (paused) {
            // Restore the frozen overlay so it stays visible even after motion or zoom.
            frozenBubbles?.let { paintCards(it) }
            return
        }
        if (settings.mode == CaptureMode.MANUAL && state == State.SHOWING) {
            // In manual mode (tap to translate), the user wants to read and zoom without automatic dismiss.
            return
        }
        when (state) {
            State.TRANSLATING -> {
                if (pageChanged) midPassCancels++ else midPassCancels = 0
                translateJob?.cancel()
                state = State.SCANNING
                shownThumb = null
                clearCards()
                setPill(null)
            }
            State.SHOWING -> {
                // In auto mode, only clear cards when the page actually changed (swapped or scrolled far away).
                // Gentle motion, hand jitter, or pinch-zoom should NOT dismiss cards!
                if (pageChanged) {
                    state = State.SCANNING
                    shownThumb = null
                    clearCards()
                    setPill(null)
                }
            }
            State.SCANNING -> Unit
        }
    }

    private fun pushBusy() {
        busyDepth++
        controller?.setBusy(true)
    }

    private fun popBusy() {
        busyDepth--
        if (busyDepth <= 0) {
            busyDepth = 0
            controller?.setBusy(false)
        }
    }

    /**
     * Paints cards and records the cells they cover, in one step — the mask
     * must never describe cards other than the ones actually on screen.
     * That includes the fast draft painted mid-pass: left unrecorded, its
     * static pixels would count as the page not having changed, and the
     * balloon that slid in under it would never be noticed. Main thread only.
     */
    private fun paintCards(bubbles: List<RenderBubble>) {
        val view = controller?.bubbleView ?: return
        view.setBubbles(bubbles)
        setOverlayMask(currentOverlayMask())
    }

    /** Clears cards and the mask that described them. Main thread only. */
    private fun clearCards() {
        controller?.bubbleView?.clear()
        setOverlayMask(currentOverlayMask())
    }

    /** The floating controls moved, or the pill came, went or was re-measured. Main thread only. */
    private fun refreshOverlayMask() {
        setOverlayMask(currentOverlayMask())
    }

    /** The cells under the cards on screen, if any, and under the floating controls. */
    private fun currentOverlayMask(): BooleanArray? {
        val rects = ArrayList<Rect>()
        controller?.let { c ->
            if (c.bubbleView.hasBubbles()) rects.addAll(c.bubbleView.placedRects())
            rects.addAll(c.bubbleView.debugStrokeRects())
            rects.addAll(c.overlayExclusions())
        }
        return if (rects.isEmpty() || capW <= 0 || capH <= 0) null else FrameStability.mask(rects, capW, capH)
    }

    /**
     * Records what our overlays cover. The cells the previous mask covered
     * and this one does not stay excluded for [MASK_GRACE_MS] more: the
     * screen catches up with a change a few frames after it is made, and
     * until it has, those cells still show the card or pill that was there
     * — which, compared against the translated page, looks exactly like
     * the page having changed. Main thread only.
     */
    private fun setOverlayMask(current: BooleanArray?) {
        val previous = overlayMask
        val epoch = ++maskEpoch
        overlayMask = FrameStability.union(previous, current)
        if (previous == null) return
        scope.launch {
            delay(MASK_GRACE_MS)
            if (maskEpoch == epoch) overlayMask = current
        }
    }

    private fun startTicker() {
        scope.launch {
            while (isActive) {
                delay(60)
                if (paused || settings.mode == CaptureMode.MANUAL) continue
                if (projection == null || state != State.SCANNING) continue
                val now = SystemClock.uptimeMillis()
                if (lastFrameAt <= 0 || now < suppressUntil) continue
                val quiet = now - lastMotionAt
                if (quiet >= settings.stabilityMs) {
                    startTranslate(auto = true)
                } else if (quiet >= EARLY_ANALYSIS_MS && !preparing) {
                    startPrepare()
                }
            }
        }
    }

    /**
     * Grabs the frame and starts reading it before the stability window
     * has run out. Nothing is painted from here; [startTranslate] picks the
     * reading up if the frame is still the one on screen, and any motion
     * in between throws it away.
     */
    private fun startPrepare() {
        // Never read a frame with our own cards on it.
        if (controller?.bubbleView?.hasBubbles() == true) return
        val bmp = grabFrame() ?: return
        val exclusions = controller?.overlayExclusions() ?: emptyList()
        preparing = true
        val job = scope.async(Dispatchers.Default) {
            FrameStability.grayThumbOf(bmp) to pipeline.analyze(bmp, settings, exclusions)
        }
        prepared = Prepared(bmp, job)
    }

    /** Drops the frame read ahead, if any. Main thread only. */
    private fun discardPrepared() {
        val p = prepared
        prepared = null
        preparing = false
        if (p == null) return
        p.job.cancel()
        retireLater(p.bitmap)
    }

    /**
     * Whether the frame read ahead is still what the screen shows, judged
     * over the cells our own overlays do not cover — the pill saying
     * "translating…" is up by now, and must not count. Drift is measured
     * two ways, as a page change is: by how far the cells moved on average,
     * which a scroll makes obvious, and by how many moved at all, which a
     * tap-to-turn between two mostly-white pages does and the average hides.
     */
    private fun stillOnScreen(read: IntArray, live: IntArray?): Boolean {
        val mask = overlayMask
        return FrameStability.meanDiff(read, live, mask) <= PREPARED_MAX_DRIFT &&
            FrameStability.changedFraction(read, live, mask) <= PAGE_CHANGE_FRACTION
    }

    /** Hands over the frame read ahead, or null when there is none. Main thread only. */
    private fun takePrepared(): Prepared? {
        val p = prepared ?: return null
        prepared = null
        preparing = false
        if (p.job.isCancelled) {
            retireLater(p.bitmap)
            return null
        }
        return p
    }

    /**
     * Frees a frame whose reading was cut short. The recognizer may still
     * be looking at the pixels for a moment after its coroutine is
     * cancelled, so the memory is given back a little later rather than
     * from under it.
     */
    private fun retireLater(bmp: Bitmap) {
        scope.launch {
            delay(1500)
            runCatching { bmp.recycle() }
        }
    }

    private fun startTranslate(auto: Boolean) {
        if (state == State.TRANSLATING) return
        state = State.TRANSLATING
        translateJob = scope.launch {
            pushBusy()
            var bmp: Bitmap? = null
            try {
                // A long enough break since the last translated page means the
                // next one probably belongs to a different series.
                works.beginPass(System.currentTimeMillis())
                shownThumb = null
                val exclusions = controller?.overlayExclusions() ?: emptyList()

                // The page may already have been read: analysis starts as
                // soon as the screen goes quiet, ahead of the stability
                // window, and is picked up here when the frame has not moved
                // since. Otherwise the frame is grabbed and read now.
                //
                // Either way the baseline for page-change detection is taken
                // from the exact bitmap handed to the pipeline. Waiting for a
                // later "settled" frame instead left a hole: a fling inside
                // the suppression window put a new page on screen first, the
                // baseline then described the new page while the cards
                // described the old one, and the mismatch could never be
                // noticed.
                var ahead: TranslatePipeline.Analysis? = null
                val prep = takePrepared()
                if (prep != null) {
                    setPill(AppStrings.pillTranslating(settings.appLang))
                    val read = try {
                        prep.job.await()
                    } catch (e: CancellationException) {
                        if (!isActive) throw e
                        null
                    } catch (_: Exception) {
                        null
                    }
                    if (read != null && stillOnScreen(read.first, latestThumb)) {
                        bmp = prep.bitmap
                        shownThumb = read.first
                        ahead = read.second
                    } else {
                        // The screen moved under the reading, or it failed.
                        retireLater(prep.bitmap)
                    }
                }
                val analysis: TranslatePipeline.Analysis = ahead ?: run {
                    val fresh = grabCleanBitmap()
                    if (fresh == null) {
                        state = State.SCANNING
                        return@launch
                    }
                    bmp = fresh
                    setPill(AppStrings.pillTranslating(settings.appLang))
                    withContext(Dispatchers.Default) {
                        shownThumb = FrameStability.grayThumbOf(fresh)
                        pipeline.analyze(fresh, settings, exclusions)
                    }
                }

                var draftShown: List<RenderBubble> = emptyList()
                val result = withContext(Dispatchers.Default) {
                    pipeline.translate(analysis, settings) { partial ->
                        // A draft or a streamed batch landed — paint it now,
                        // the rest of the polish follows.
                        withContext(Dispatchers.Main.immediate) {
                            if (isActive && state == State.TRANSLATING) {
                                draftShown = partial.bubbles
                                lastShown = partial.bubbles
                                suppressUntil = SystemClock.uptimeMillis() + 600
                                paintCards(partial.bubbles)
                                setPill("✓ ${partial.bubbles.size} · ${partial.engineLabel} · ✨ upgrading…")
                            }
                        }
                    }
                }
                if (!isActive) return@launch
                // The polish replaces what it answered and never erases what
                // it didn't: an empty or partial upgrade keeps the draft cards
                // the reader is already reading.
                val shown = UpgradeMerge.merge(draftShown, result.bubbles)
                lastShown = shown
                suppressUntil = SystemClock.uptimeMillis() + 500
                paintCards(shown)
                state = State.SHOWING
                // A page with dialogue keeps the current work alive and feeds it
                // the names that identify it; a run of pages without any means
                // the reader has left the story — an index, a cover, a menu.
                if (shown.isNotEmpty()) {
                    works.noteTranslated(System.currentTimeMillis(), glossary.snapshot().keys)
                } else {
                    works.noteQuietPass()
                }
                controller?.bubbleView?.setDebugBalloons(
                    if (settings.diagnostics) result.balloons else emptyList(),
                    if (settings.diagnostics) result.panels else emptyList(),
                )
                if (settings.diagnostics) refreshOverlayMask()
                // A page that stays the page it was for a moment after the
                // pass is a page whose changes are real, not perpetual.
                scope.launch {
                    delay(1500)
                    if (state == State.SHOWING && lastShown === shown) midPassCancels = 0
                }
                // Diagnostics stay up: they exist to be read off a page that
                // came back wrong, and a pill that vanishes is no use for that.
                if (result.diag != null) {
                    setPill("${result.engineLabel.ifBlank { "—" }} · ${result.diag} · ${works.describe()}")
                } else if (shown.isEmpty()) {
                    setPill(if (auto) null else AppStrings.pillNoText(settings.appLang), 1800)
                } else {
                    val mark = if (result.polished && result.bubbles.isNotEmpty()) "✨" else "✓"
                    val kept = if (shown.size > result.bubbles.size) " · draft kept" else ""
                    val extra = result.note?.let { " · $it" } ?: ""
                    setPill("$mark ${shown.size} · ${result.engineLabel}$extra$kept", 2400)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The page still needs watching or a failed pass would sit
                // here until something scrolls. The baseline from the grabbed
                // frame (when the grab got that far) and the mask over
                // whatever draft cards are up are both already in place.
                state = State.SHOWING
                setPill("⚠ " + (e.message?.take(90) ?: "translation failed"), 4500)
            } finally {
                // Cancellation is this loop's steady state — every scroll
                // that interrupts a pass lands here — so the full-screen
                // copy is reclaimed on that path too, not only on success.
                bmp?.let { if (isActive) it.recycle() else retireLater(it) }
                popBusy()
            }
        }
    }

    /**
     * Returns a copy of the newest frame with our own overlays guaranteed
     * absent. If overlays are visible they are cleared first and a fresh,
     * clean frame awaited, so OCR never reads our own English.
     */
    private suspend fun grabCleanBitmap(): Bitmap? {
        val hadOverlays = controller?.bubbleView?.hasBubbles() == true
        if (hadOverlays) {
            clearCards()
            suppressUntil = SystemClock.uptimeMillis() + 900
            delay(280)
        }
        return grabFrame()
    }

    /**
     * A private copy of the newest frame, cropped of any stride padding, or
     * null before the first frame has arrived. Always a copy, so the reused
     * capture buffers stay the capture thread's own.
     */
    private fun grabFrame(): Bitmap? = synchronized(frameLock) {
        latestBitmap?.let { src ->
            val w = capW.coerceAtMost(src.width)
            val h = capH.coerceAtMost(src.height)
            val out = Bitmap.createBitmap(src, 0, 0, w, h)
            if (out === src) src.copy(Bitmap.Config.ARGB_8888, false) else out
        }
    }

    private fun setPill(text: String?, autoHideMs: Long = 0) {
        controller?.setStatus(text, autoHideMs)
    }

    // ---- OverlayController.Listener (all invoked on main thread) ----

    override fun onTranslateNow() {
        if (projection == null || state == State.TRANSLATING) return
        startTranslate(auto = false)
    }

    override fun onNewSeries() {
        // The automatic boundaries — a long gap, or a run of pages with no
        // dialogue — cover switching series the usual way. This is for going
        // straight from one work to the next with neither.
        works.startNewWork()
        setPill("new series · names cleared", 2000)
    }

    override fun onTogglePause() {
        paused = !paused
        if (paused) {
            // Freeze the overlay: cancel any in-flight pass and lock the cards on screen
            // so the reader can pinch-to-zoom and read without cards disappearing.
            translateJob?.cancel()
            discardPrepared()
            val toFreeze = if (lastShown.isNotEmpty()) lastShown else controller?.bubbleView?.currentBubbles.orEmpty()
            frozenBubbles = toFreeze.ifEmpty { null }
            frozenBubbles?.let { paintCards(it) }
            setPill(AppStrings.pillPaused(settings.appLang), 2500)
        } else {
            // Resume: drop the frozen snapshot and go back to live mode.
            frozenBubbles = null
            state = State.SCANNING
            shownThumb = null
            setPill(AppStrings.pillLive(settings.appLang), 1200)
        }
        controller?.setPaused(paused)
        updateNotification()
    }

    override fun onToggleMode() {
        val next = if (settings.mode == CaptureMode.AUTO) CaptureMode.MANUAL else CaptureMode.AUTO
        scope.launch { settingsRepo.setMode(next) }
        val msg = if (settings.appLang == AppLang.ID) {
            if (next == CaptureMode.AUTO) "Mode otomatis live" else "Ketuk tombol untuk menerjemahkan"
        } else {
            if (next == CaptureMode.AUTO) "auto-live mode" else "tap the button to translate"
        }
        setPill(msg, 2200)
    }

    override fun onPeek() {
        if (state != State.SHOWING) return
        val shown = lastShown
        if (shown.isEmpty()) return
        scope.launch {
            clearCards()
            suppressUntil = SystemClock.uptimeMillis() + 900
            delay(4000)
            if (state != State.SHOWING) return@launch
            suppressUntil = SystemClock.uptimeMillis() + 900
            if (lastShown === shown) {
                // A newer pass owns the screen now; resurrecting the list
                // captured at peek time would paint the previous page's
                // lines over it — and the mask would then hide the mistake
                // from every detector.
                paintCards(shown)
            }
        }
    }

    override fun onCopyText() {
        val shown = lastShown
        val textToCopy = shown
            .map { it.translated.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        if (textToCopy.isNotBlank()) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("MangaLens", textToCopy)
            cm?.setPrimaryClip(clip)
            setPill(AppStrings.toastCopied(settings.appLang), 2000)
        } else {
            setPill(if (settings.appLang == AppLang.ID) "Tidak ada teks untuk disalin" else "No text to copy", 1800)
        }
    }

    override fun onOpenSettings() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    override fun onStopRequested() {
        stopSelf()
    }

    override fun isPaused() = paused

    override fun isAutoMode() = settings.mode == CaptureMode.AUTO
    override fun appLang() = settings.appLang

    // ---- notification ----

    private fun buildNotification(): Notification {
        fun serviceIntent(action: String, req: Int): PendingIntent = PendingIntent.getService(
            this, req,
            Intent(this, ScreenCaptureService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val open = PendingIntent.getActivity(
            this, 3,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val lang = settings.appLang
        return NotificationCompat.Builder(this, MangaLensApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bubble)
            .setContentTitle(AppStrings.notifTitle(lang))
            .setContentText(AppStrings.notifContent(lang, paused))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, if (paused) AppStrings.notifActionResume(lang) else AppStrings.notifActionPause(lang), serviceIntent(ACTION_TOGGLE_PAUSE, 2))
            .addAction(0, AppStrings.notifActionStop(lang), serviceIntent(ACTION_STOP, 1))
            .build()
    }

    private fun updateNotification() {
        runCatching { NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification()) }
    }

    override fun onDestroy() {
        running.value = false
        translateJob?.cancel()
        discardPrepared()
        scope.cancel()
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { projection?.stop() }
        projection = null
        controller?.onFootprintChanged = null
        controller?.detach()
        controller = null
        releaseFrameBuffers()
        captureThread?.quitSafely()
        captureThread = null
        super.onDestroy()
    }
}
