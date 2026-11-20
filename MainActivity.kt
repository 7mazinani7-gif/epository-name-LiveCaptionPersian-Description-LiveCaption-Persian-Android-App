package com.livecaption.persian

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter

/** Persian-first interface with microphone speech recognition and on-device text translation. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var original: TextView
    private lateinit var translated: TextView
    private lateinit var modeGroup: RadioGroup
    private var recognizer: SpeechRecognizer? = null
    private val requestAudio = 41
    private val requestInternalAudioPermission = 42
    private var originalText = "صدای موردنظر را پخش کن و دریافت صدا را شروع کن."
    private var translatedText = "ترجمه پس از دریافت نخستین جمله نمایش داده می‌شود."
    private var displayMode = 2 // 0=original, 1=translation, 2=both
    private var targetLanguage = "فارسی"
    private var sourceLanguageLabel = "فارسی"
    private var sourceLanguageCode = "fa-IR"
    private var captionFontSize = 20f
    private var captionTextColor = Color.WHITE
    private var captionOutlineColor = Color.BLACK
    private var captionOutlineWidth = 2f
    private var captionShadow = true
    private var translator: CaptionTranslator? = null
    private var micListening = false
    private var micRestartTask: Runnable? = null
    private var captureReceiverRegistered = false
    private var floatingEnabled = false
    private var internalCaptureActive = false
    private lateinit var internalButton: TextView
    private lateinit var floatingToggle: TextView
    private lateinit var micButton: TextView
    private val translationHandler = Handler(Looper.getMainLooper())
    private var translationTask: Runnable? = null
    private var lastTranslatedInput = ""
    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                PlaybackCaptureService.ACTION_STATUS -> {
                    status.text = intent.getStringExtra(PlaybackCaptureService.EXTRA_TEXT) ?: ""
                    if (intent.hasExtra(PlaybackCaptureService.EXTRA_RUNNING)) {
                        internalCaptureActive = intent.getBooleanExtra(PlaybackCaptureService.EXTRA_RUNNING, false)
                        updateInternalButton()
                    }
                }
                PlaybackCaptureService.ACTION_TRANSCRIPT -> {
                    val text = intent.getStringExtra(PlaybackCaptureService.EXTRA_TEXT).orEmpty()
                    if (text.isNotBlank()) {
                        originalText = text
                        refreshCaptions()
                        if (intent.getBooleanExtra(PlaybackCaptureService.EXTRA_FINAL, false)) translateWhenReady(text)
                    }
                }
            }
        }
    }

    private val ink = Color.rgb(27, 35, 53)
    private val muted = Color.rgb(112, 123, 145)
    private val purple = Color.rgb(104, 83, 230)
    private val teal = Color.rgb(28, 174, 164)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val captureFilter = IntentFilter().apply {
            addAction(PlaybackCaptureService.ACTION_TRANSCRIPT)
            addAction(PlaybackCaptureService.ACTION_STATUS)
        }
        @Suppress("DEPRECATION")
        registerReceiver(captureReceiver, captureFilter)
        captureReceiverRegistered = true
        window.statusBarColor = Color.rgb(35, 33, 79)
        window.navigationBarColor = Color.rgb(248, 249, 253)
        window.decorView.systemUiVisibility = 0

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(248, 249, 253))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(28))
        }
        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // User-provided wheat-and-sky artwork inside the app
        val banner = ImageView(this).apply {
            setImageResource(com.livecaption.persian.R.drawable.iran_wheat_banner)
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.WHITE, 18)
            clipToOutline = true
            contentDescription = "تصویر گندمزار"
        }
        content.addView(banner, LinearLayout.LayoutParams(-1, dp(142)).apply { bottomMargin = dp(14) })

        // Hero header
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(24))
            background = roundedGradient(intArrayOf(Color.rgb(43, 39, 103), Color.rgb(103, 77, 210)), 26)
        }
        val topLine = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val logo = TextView(this).apply {
            text = "ز"; textSize = 25f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); background = rounded(Color.argb(45,255,255,255), 15)
        }
        topLine.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))
        val brand = TextView(this).apply {
            text = "  LIVECAPTION\n  PERSIAN"
            textSize = 15f; setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setLineSpacing(dp(1).toFloat(), 1f)
        }
        topLine.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val free = TextView(this).apply {
            text = "رایگان"; textSize = 12f; setTextColor(Color.WHITE)
            setPadding(dp(12), dp(7), dp(12), dp(7)); background = rounded(Color.argb(48,255,255,255), 30)
        }
        topLine.addView(free)
        hero.addView(topLine)
        val headline = TextView(this).apply {
            text = "گفتار فارسی؛\nزیرنویس به انتخاب تو"
            textSize = 25f; setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.RIGHT; textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            setPadding(0, dp(23), 0, dp(6))
        }
        hero.addView(headline)
        val sub = TextView(this).apply {
            text = "متن اصلی فارسی، ترجمه یا هر دو روی صفحه"
            textSize = 13f; setTextColor(Color.rgb(226, 224, 255)); gravity = Gravity.RIGHT
        }
        hero.addView(sub)
        content.addView(hero, margin(-1, 0, 0, 16))

        // Status card
        val statusCard = card()
        val statusRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val pulse = TextView(this).apply {
            text = "●"; textSize = 16f; setTextColor(teal); gravity = Gravity.CENTER
            background = rounded(Color.rgb(226, 248, 245), 14)
        }
        statusRow.addView(pulse, LinearLayout.LayoutParams(dp(38), dp(38)))
        val statusText = TextView(this).apply {
            text = "  وضعیت برنامه"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink)
        }
        statusRow.addView(statusText)
        statusCard.addView(statusRow)
        status = TextView(this).apply {
            text = "آماده دریافت صدا؛ مدل زبان انتخابی در اولین اجرا دانلود می‌شود."; textSize = 13f; setTextColor(muted); gravity = Gravity.RIGHT
            setPadding(0, dp(12), 0, 0)
        }
        statusCard.addView(status)
        content.addView(statusCard, margin(-1, -2, 0, 14))

        // Input source actions
        content.addView(sectionTitle("منبع دریافت صدا", "از صدای برنامه‌ها یا میکروفون استفاده کن"))
        val internal = actionButton("◖))  دریافت صدای داخلی گوشی", purple)
        internalButton = internal
        content.addView(internal, margin(-1, dp(10), 0, 9))
        val mic = actionButton("🎙  شروع زیرنویس از میکروفون", Color.rgb(38, 151, 145))
        micButton = mic
        content.addView(mic, margin(-1, 0, 0, 9))
        floatingToggle = actionButton("●  روشن کردن زیرنویس شناور", Color.rgb(28, 145, 93))
        content.addView(floatingToggle, margin(-1, 0, 0, 16))

        // Speech source language selection
        content.addView(sectionTitle("زبان‌ها", "زبان گفتار ورودی و زبان ترجمه را جداگانه انتخاب کن"))
        val langCard = card()
        val sourceLabel = TextView(this).apply { text = "زبان گفتار ورودی (تشخیص)"; textSize = 13f; setTextColor(muted); gravity = Gravity.RIGHT }
        langCard.addView(sourceLabel)
        val sourceLanguages = arrayOf("فارسی", "English", "العربية")
        val sourcePicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, sourceLanguages)
            setSelection(0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    sourceLanguageLabel = sourceLanguages[position]
                    sourceLanguageCode = when (sourceLanguageLabel) { "English" -> "en-US"; "العربية" -> "ar"; else -> "fa-IR" }
                    status.text = when (sourceLanguageCode) {
                        "ar" -> "مدل عربی Vosk حدود ۳۱۸ مگابایت است و برای بار اول دانلود می‌شود."
                        "en-US" -> "مدل انگلیسی Vosk حدود ۴۰ مگابایت است و برای بار اول دانلود می‌شود."
                        else -> "مدل فارسی Vosk حدود ۵۳ مگابایت است."
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        langCard.addView(sourcePicker, LinearLayout.LayoutParams(-1, dp(48)))
        val langLabel = TextView(this).apply { text = "زبان مقصد ترجمه"; textSize = 13f; setTextColor(muted); gravity = Gravity.RIGHT; setPadding(0, dp(8), 0, 0) }
        langCard.addView(langLabel)
        val languages = arrayOf("فارسی", "English", "العربية", "Deutsch", "Français", "中文", "日本語", "Español", "Русский", "Türkçe", "اردو", "हिन्दी")
        val languagePicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, languages)
            setSelection(0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    targetLanguage = languages[position]
                    translatedText = "ترجمه به $targetLanguage در حال آماده‌سازی است."
                    lastTranslatedInput = ""
                    refreshCaptions()
                    if (originalText.isNotBlank() && !originalText.startsWith("صدای موردنظر")) {
                        translateWhenReady(originalText)
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        langCard.addView(languagePicker, LinearLayout.LayoutParams(-1, dp(48)))
        content.addView(langCard, margin(-1, dp(10), 0, 16))

        // Three modes
        content.addView(sectionTitle("نحوه نمایش زیرنویس", "هر زمان خواستی حالت نمایش را عوض کن"))
        val modeCard = card()
        modeGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val opts = listOf("فقط زبان اصلی" to 1001, "فقط ترجمه" to 1002, "زبان اصلی + ترجمه هم‌زمان" to 1003)
        opts.forEach { (title, id) ->
            val rb = RadioButton(this).apply {
                text = title; this.id = id; textSize = 14f; setTextColor(ink)
                buttonTintList = android.content.res.ColorStateList.valueOf(purple)
                setPadding(dp(5), dp(7), dp(5), dp(7))
            }
            modeGroup.addView(rb)
        }
        modeGroup.check(1003)
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            displayMode = when (checkedId) { 1001 -> 0; 1002 -> 1; else -> 2 }
            refreshCaptions()
        }
        modeCard.addView(modeGroup)
        content.addView(modeCard, margin(-1, dp(10), 0, 16))

        // Caption appearance controls
        content.addView(sectionTitle("ظاهر زیرنویس شناور", "پس‌زمینه کاملاً شفاف؛ تنظیم متن، دورخط و سایه"))
        val appearance = card()
        val sizeLabel = TextView(this).apply { text = "اندازه نوشته: ۲۰"; textSize = 13f; setTextColor(ink); gravity = Gravity.RIGHT }
        appearance.addView(sizeLabel)
        val sizeSeek = SeekBar(this).apply {
            max = 22; progress = 8
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    captionFontSize = (12 + progress).toFloat(); sizeLabel.text = "اندازه نوشته: ${captionFontSize.toInt()}"; refreshCaptions()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        appearance.addView(sizeSeek)
        val outlineLabel = TextView(this).apply { text = "ضخامت دورخط: ۲"; textSize = 13f; setTextColor(ink); gravity = Gravity.RIGHT }
        appearance.addView(outlineLabel)
        val outlineSeek = SeekBar(this).apply {
            max = 6; progress = 2
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    captionOutlineWidth = progress.toFloat(); outlineLabel.text = "ضخامت دورخط: $progress"; refreshCaptions()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        appearance.addView(outlineSeek)
        val styleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val colorButton = actionButton("رنگ نوشته", purple).apply {
            setOnClickListener {
                val colors = intArrayOf(Color.WHITE, Color.YELLOW, Color.CYAN, Color.GREEN, Color.rgb(255, 170, 190))
                val next = (colors.indexOf(captionTextColor) + 1).let { if (it <= 0) 1 else it }
                captionTextColor = colors[next % colors.size]; refreshCaptions()
            }
        }
        val outlineColorButton = actionButton("رنگ دورخط", Color.rgb(65, 78, 105)).apply {
            setOnClickListener {
                val colors = intArrayOf(Color.BLACK, Color.WHITE, Color.rgb(30, 80, 190), Color.rgb(150, 30, 30))
                captionOutlineColor = colors[(colors.indexOf(captionOutlineColor) + 1).let { if (it <= 0) 1 else it } % colors.size]
                refreshCaptions()
            }
        }
        val shadowButton = actionButton("سایه: روشن", teal).apply {
            setOnClickListener { captionShadow = !captionShadow; text = if (captionShadow) "سایه: روشن" else "سایه: خاموش"; refreshCaptions() }
        }
        styleRow.addView(colorButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        styleRow.addView(outlineColorButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        styleRow.addView(shadowButton, LinearLayout.LayoutParams(0, -2, 1f))
        appearance.addView(styleRow)
        content.addView(appearance, margin(-1, dp(10), 0, 16))

        // Caption preview
        content.addView(sectionTitle("پیش‌نمایش زیرنویس", "نمایش نمونه از چیدمان خروجی"))
        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(Color.rgb(29, 33, 49), 20)
        }
        val originalTag = smallTag("متن اصلی", Color.rgb(179, 170, 255))
        preview.addView(originalTag)
        original = TextView(this).apply {
            text = originalText; textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.RIGHT
            setPadding(0, dp(7), 0, dp(13))
        }
        preview.addView(original)
        val divider = View(this).apply { setBackgroundColor(Color.rgb(72, 77, 98)) }
        preview.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
        val translationTag = smallTag("ترجمه • $targetLanguage", Color.rgb(111, 224, 207))
        preview.addView(translationTag)
        translated = TextView(this).apply {
            text = translatedText; textSize = 16f; setTextColor(Color.rgb(222, 229, 243)); gravity = Gravity.RIGHT
            setPadding(0, dp(7), 0, 0)
        }
        preview.addView(translated)
        content.addView(preview, margin(-1, dp(10), 0, 16))
        refreshCaptions()

        val note = TextView(this).apply {
            text = "دریافت صدای داخلی با مدل آفلاین Vosk و زبان ورودی انتخابی (فارسی، انگلیسی یا عربی) انجام می‌شود؛ دانلود اولیه مدل به اینترنت نیاز دارد. دریافت از میکروفون از سرویس تشخیص گفتار اندروید و زبان انتخابی استفاده می‌کند و بسته به گوشی ممکن است به اینترنت نیاز داشته باشد. ترجمه با مدل‌های ML Kit روی دستگاه انجام می‌شود و مدل زبان‌ها بار اول دانلود می‌شوند. صدای داخلی فقط از برنامه‌هایی قابل دریافت است که ضبط آن را مجاز کرده باشند."
            textSize = 12f; setTextColor(muted); gravity = Gravity.RIGHT; setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(4), dp(4), dp(4), dp(12))
        }
        content.addView(note)
        setContentView(page)

        translator = CaptionTranslator(
            onResult = { result -> runOnUiThread { translatedText = result; refreshCaptions() } },
            onStatus = { message -> runOnUiThread { status.text = message } }
        )
        mic.setOnClickListener {
            if (micListening) {
                micListening = false
                micRestartTask?.let { status.removeCallbacks(it) }
                micRestartTask = null
                try { recognizer?.stopListening() } catch (_: Exception) { }
                try { recognizer?.cancel() } catch (_: Exception) { }
                mic.text = "🎙  شروع زیرنویس از میکروفون"
                status.text = "دریافت صدای میکروفون متوقف شد."
            } else {
                mic.text = "■  توقف دریافت میکروفون"
                beginMicRecognition()
            }
        }
        internal.setOnClickListener {
            if (internalCaptureActive) {
                try { startService(Intent(this, PlaybackCaptureService::class.java).setAction(PlaybackCaptureService.ACTION_STOP)) }
                catch (_: Exception) { stopService(Intent(this, PlaybackCaptureService::class.java)) }
                internalCaptureActive = false
                updateInternalButton()
                status.text = "درخواست توقف دریافت صدای داخلی ارسال شد."
            } else requestInternalCapture()
        }
        floatingToggle.setOnClickListener {
            if (floatingEnabled) {
                stopService(Intent(this, FloatingCaptionService::class.java).apply { action = FloatingCaptionService.ACTION_STOP })
                floatingEnabled = false
                floatingToggle.text = "●  روشن کردن زیرنویس شناور"
                floatingToggle.setBackground(rounded(Color.rgb(28, 145, 93), 16))
                status.text = "زیرنویس شناور خاموش شد."
            } else enableFloatingCaptions()
        }
        val creator = TextView(this).apply {
            text = "ساخته‌شده با افتخار  |  Mazinani7 & AI\nLiveCaption Persian"
            textSize = 12f; setTextColor(muted); gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        }
        content.addView(creator, LinearLayout.LayoutParams(-1, -2))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun margin(w: Int, top: Int, bottom: Int, after: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, -2).apply { setMargins(0, top, 0, after) }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun roundedGradient(colors: IntArray, radius: Int): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, colors
    ).apply { cornerRadius = dp(radius).toFloat() }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(15), dp(16), dp(15))
        background = rounded(Color.WHITE, 19)
        elevation = dp(2).toFloat()
    }
    private fun sectionTitle(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@MainActivity).apply {
            text = title; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink); gravity = Gravity.RIGHT
        })
        addView(TextView(this@MainActivity).apply {
            text = subtitle; textSize = 11f; setTextColor(muted); gravity = Gravity.RIGHT
            setPadding(0, dp(3), 0, 0)
        })
    }
    private fun actionButton(textValue: String, color: Int): TextView = TextView(this).apply {
        text = textValue; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        gravity = Gravity.CENTER; setPadding(dp(12), dp(15), dp(12), dp(15)); background = rounded(color, 16)
        elevation = dp(2).toFloat()
    }
    private fun smallTag(textValue: String, color: Int): TextView = TextView(this).apply {
        text = textValue; textSize = 11f; setTextColor(color); gravity = Gravity.RIGHT
    }

    private fun translateWhenReady(text: String) {
        if (text.isBlank() || text == lastTranslatedInput) return
        translationTask?.let { translationHandler.removeCallbacks(it) }
        translationTask = Runnable {
            lastTranslatedInput = text
            translator?.translate(text, targetLanguage)
        }
        translationHandler.postDelayed(translationTask!!, 450)
    }

    private fun beginMicRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            micListening = false
            micButton.text = "🎙  شروع زیرنویس از میکروفون"
            status.text = "تشخیص گفتار سیستمی در این دستگاه در دسترس نیست."; return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestAudio); return
        }
        recognizer?.destroy()
        micListening = true
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { micListening = true; status.text = "در حال شنیدن صدای میکروفون" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.text = "در حال پردازش گفتار…" }
                override fun onError(error: Int) {
                    val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                    if (micListening && recoverable) {
                        scheduleMicRestart(sr, if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 900 else 450)
                    } else {
                        micListening = false
                        micButton.text = "🎙  شروع زیرنویس از میکروفون"
                        status.text = "تشخیص گفتار متوقف شد (کد خطا: $error). دوباره شروع کن."
                    }
                }
                override fun onResults(results: Bundle?) { showResult(results); scheduleMicRestart(sr, 250) }
                override fun onPartialResults(partialResults: Bundle?) { showResult(partialResults) }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, sourceLanguageCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sourceLanguageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            sr.startListening(intent)
        }
    }

    private fun scheduleMicRestart(sr: SpeechRecognizer, delay: Long) {
        micRestartTask?.let { status.removeCallbacks(it) }
        val task = Runnable {
            micRestartTask = null
            // Ignore delayed callbacks belonging to a recognizer that has since been replaced.
            if (micListening && recognizer === sr) try {
                sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, sourceLanguageCode)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sourceLanguageCode)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                })
            } catch (_: Exception) {
                micListening = false
                micButton.text = "🎙  شروع زیرنویس از میکروفون"
                status.text = "راه‌اندازی دوباره تشخیص گفتار ناموفق بود. دوباره شروع کن."
            }
        }
        micRestartTask = task
        status.postDelayed(task, delay)
    }

    private fun showResult(bundle: Bundle?) {
        val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        if (!text.isNullOrBlank()) {
            originalText = text
            refreshCaptions()
            translateWhenReady(text)
        }
    }
    private fun refreshCaptions() {
        if (!::original.isInitialized || !::translated.isInitialized) return
        original.visibility = if (displayMode == 1) View.GONE else View.VISIBLE
        translated.visibility = if (displayMode == 0) View.GONE else View.VISIBLE
        original.text = originalText
        translated.text = translatedText
        publishOverlayUpdate()
    }
    private fun enableFloatingCaptions() {
        if (!Settings.canDrawOverlays(this)) {
            status.text = "برای نمایش روی برنامه‌های دیگر، مجوز پنجره شناور را فعال کن."
            val permissionIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(permissionIntent)
            return
        }
        val service = Intent(this, FloatingCaptionService::class.java).apply {
            action = FloatingCaptionService.ACTION_START
            putExtra(FloatingCaptionService.EXTRA_ORIGINAL, originalText)
            putExtra(FloatingCaptionService.EXTRA_TRANSLATED, translatedText)
            putExtra(FloatingCaptionService.EXTRA_MODE, displayMode)
            putExtra(FloatingCaptionService.EXTRA_TARGET, targetLanguage)
            putExtra(FloatingCaptionService.EXTRA_FONT_SIZE, captionFontSize)
            putExtra(FloatingCaptionService.EXTRA_TEXT_COLOR, captionTextColor)
            putExtra(FloatingCaptionService.EXTRA_OUTLINE_COLOR, captionOutlineColor)
            putExtra(FloatingCaptionService.EXTRA_OUTLINE_WIDTH, captionOutlineWidth)
            putExtra(FloatingCaptionService.EXTRA_SHADOW, captionShadow)
        }
        startForegroundService(service)
        floatingEnabled = true
        floatingToggle.text = "■  خاموش کردن زیرنویس شناور"
        floatingToggle.setBackground(rounded(Color.rgb(180, 55, 55), 16))
        status.text = "زیرنویس شناور فعال شد؛ حالا می‌توانی به برنامه دیگری بروی."
    }

    private fun publishOverlayUpdate() {
        // Do not start the foreground overlay service as a side effect of rendering
        // the in-app preview. The overlay must only run after the user enables it.
        if (!floatingEnabled || !Settings.canDrawOverlays(this)) return
        val update = Intent(this, FloatingCaptionService::class.java).apply {
            action = FloatingCaptionService.ACTION_UPDATE
            putExtra(FloatingCaptionService.EXTRA_ORIGINAL, originalText)
            putExtra(FloatingCaptionService.EXTRA_TRANSLATED, translatedText)
            putExtra(FloatingCaptionService.EXTRA_MODE, displayMode)
            putExtra(FloatingCaptionService.EXTRA_TARGET, targetLanguage)
            putExtra(FloatingCaptionService.EXTRA_FONT_SIZE, captionFontSize)
            putExtra(FloatingCaptionService.EXTRA_TEXT_COLOR, captionTextColor)
            putExtra(FloatingCaptionService.EXTRA_OUTLINE_COLOR, captionOutlineColor)
            putExtra(FloatingCaptionService.EXTRA_OUTLINE_WIDTH, captionOutlineWidth)
            putExtra(FloatingCaptionService.EXTRA_SHADOW, captionShadow)
        }
        try { startService(update) } catch (_: Exception) { }
    }

    private fun updateInternalButton() {
        if (!::internalButton.isInitialized) return
        internalButton.text = if (internalCaptureActive) "■  توقف دریافت صدای داخلی" else "◖))  دریافت صدای داخلی گوشی"
        internalButton.setBackground(rounded(if (internalCaptureActive) Color.rgb(180, 55, 55) else purple, 16))
    }

    private fun requestInternalCapture() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "برای دریافت صدای داخلی، ابتدا مجوز میکروفون/ضبط صدا لازم است."
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestInternalAudioPermission)
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        status.text = "برای دریافت صدای داخلی، مجوز ضبط صفحه/صدا را تأیید کن."
        startActivityForResult(manager.createScreenCaptureIntent(), 52)
    }
    @Deprecated("Deprecated API retained for Android 11 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 52 && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, PlaybackCaptureService::class.java).apply {
                putExtra("resultCode", resultCode); putExtra("projectionData", data)
                putExtra(PlaybackCaptureService.EXTRA_SOURCE_LANGUAGE, sourceLanguageCode)
            }
            try {
                startForegroundService(service)
                internalCaptureActive = true
                updateInternalButton()
                status.text = "مجوز صادر شد؛ مدل $sourceLanguageLabel در حال آماده‌سازی است. برای دانلود اولیه اینترنت لازم است."
            } catch (e: Exception) {
                internalCaptureActive = false
                updateInternalButton()
                status.text = "شروع سرویس دریافت صدای داخلی ناموفق بود: ${e.message ?: "خطا"}"
            }
        } else if (requestCode == 52) {
            internalCaptureActive = false
            updateInternalButton()
            status.text = "مجوز دریافت صدای داخلی لغو شد."
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestAudio && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) { micListening = true; beginMicRecognition() }
        else if (requestCode == requestAudio) { micButton.text = "🎙  شروع زیرنویس از میکروفون"; status.text = "مجوز میکروفون داده نشد." }
        else if (requestCode == requestInternalAudioPermission && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) requestInternalCapture()
        else if (requestCode == requestInternalAudioPermission) {
            internalCaptureActive = false
            updateInternalButton()
            status.text = "بدون مجوز ضبط صدا، دریافت صدای داخلی ممکن نیست."
        }
    }

    override fun onDestroy() {
        micListening = false
        micRestartTask?.let { status.removeCallbacks(it) }
        micRestartTask = null
        translationTask?.let { translationHandler.removeCallbacks(it) }
        if (captureReceiverRegistered) {
            try { unregisterReceiver(captureReceiver) } catch (_: Exception) { }
            captureReceiverRegistered = false
        }
        recognizer?.destroy(); translator?.close(); super.onDestroy()
    }
}
