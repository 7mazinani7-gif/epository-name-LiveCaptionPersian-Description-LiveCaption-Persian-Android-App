package com.livecaption.persian

import android.Manifest
import android.app.Activity
import android.content.Intent
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

/** Polished Persian-first prototype UI. ASR/translation engines are not yet connected. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var original: TextView
    private lateinit var translated: TextView
    private lateinit var modeGroup: RadioGroup
    private var recognizer: SpeechRecognizer? = null
    private val requestAudio = 41
    private var originalText = "صدای موردنظر را پخش کن و دریافت صدا را شروع کن."
    private var translatedText = "ترجمه پس از اتصال موتور ترجمه در این قسمت نمایش داده می‌شود."
    private var displayMode = 2 // 0=original, 1=translation, 2=both
    private var targetLanguage = "فارسی"

    private val ink = Color.rgb(27, 35, 53)
    private val muted = Color.rgb(112, 123, 145)
    private val purple = Color.rgb(104, 83, 230)
    private val teal = Color.rgb(28, 174, 164)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        // Hero header
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(24))
            background = roundedGradient(intArrayOf(Color.rgb(43, 39, 103), Color.rgb(103, 77, 210)), 26)
        }
        val topLine = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val logo = TextView(this).apply {
            text = "✦"; textSize = 25f; gravity = Gravity.CENTER
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
            text = "هر زبانی؛\nزیرنویس به انتخاب تو"
            textSize = 25f; setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.RIGHT; textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            setPadding(0, dp(23), 0, dp(6))
        }
        hero.addView(headline)
        val sub = TextView(this).apply {
            text = "متن اصلی، ترجمه یا هر دو؛ هم‌زمان با پخش صدا"
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
            text = "آماده دریافت صدا"; textSize = 13f; setTextColor(muted); gravity = Gravity.RIGHT
            setPadding(0, dp(12), 0, 0)
        }
        statusCard.addView(status)
        content.addView(statusCard, margin(-1, -2, 0, 14))

        // Input source actions
        content.addView(sectionTitle("منبع دریافت صدا", "از صدای برنامه‌ها یا میکروفون استفاده کن"))
        val internal = actionButton("◖))  دریافت صدای داخلی گوشی", purple)
        content.addView(internal, margin(-1, dp(10), 0, 9))
        val mic = actionButton("🎙  دریافت صدا از میکروفون", Color.rgb(38, 151, 145))
        content.addView(mic, margin(-1, 0, 0, 9))
        val overlayButton = actionButton("▣  فعال‌سازی زیرنویس شناور روی برنامه‌ها", Color.rgb(46, 63, 91))
        content.addView(overlayButton, margin(-1, 0, 0, 16))

        // Language selection
        content.addView(sectionTitle("زبان‌ها", "زبان ورودی به‌صورت خودکار تشخیص داده می‌شود"))
        val langCard = card()
        val langLabel = TextView(this).apply { text = "زبان مقصد ترجمه"; textSize = 13f; setTextColor(muted); gravity = Gravity.RIGHT }
        langCard.addView(langLabel)
        val languages = arrayOf("فارسی", "English", "العربية", "Deutsch", "Français", "中文", "日本語", "Español", "Русский", "Türkçe", "اردو", "हिन्दी")
        val languagePicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, languages)
            setSelection(0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    targetLanguage = languages[position]
                    translatedText = "زبان مقصد: $targetLanguage — موتور ترجمه هنوز متصل نشده است."
                    refreshCaptions()
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
            text = "نکته: این نسخه، پوسته گرافیکی و کنترل‌های برنامه است. موتورهای تشخیص چندزبانه و ترجمه هنوز باید به پروژه متصل و روی گوشی آزمایش شوند. صدای داخلی فقط از برنامه‌هایی قابل دریافت است که اندروید اجازه ضبط آن‌ها را بدهد."
            textSize = 12f; setTextColor(muted); gravity = Gravity.RIGHT; setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(4), dp(4), dp(4), dp(12))
        }
        content.addView(note)
        setContentView(page)

        mic.setOnClickListener { beginMicRecognition() }
        internal.setOnClickListener { requestInternalCapture() }
        overlayButton.setOnClickListener { enableFloatingCaptions() }
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

    private fun beginMicRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "تشخیص گفتار سیستمی در این دستگاه در دسترس نیست."; return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestAudio); return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status.text = "در حال شنیدن صدای میکروفون" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.text = "گفتار پایان یافت؛ برای شروع دوباره لمس کن" }
                override fun onError(error: Int) { status.text = "خطای تشخیص گفتار: $error" }
                override fun onResults(results: Bundle?) { showResult(results) }
                override fun onPartialResults(partialResults: Bundle?) { showResult(partialResults) }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            sr.startListening(intent)
        }
    }

    private fun showResult(bundle: Bundle?) {
        val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        if (!text.isNullOrBlank()) { originalText = text; refreshCaptions() }
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
        }
        startForegroundService(service)
        status.text = "زیرنویس شناور فعال شد؛ حالا می‌توانی به برنامه دیگری بروی."
    }

    private fun publishOverlayUpdate() {
        if (!Settings.canDrawOverlays(this)) return
        val update = Intent(this, FloatingCaptionService::class.java).apply {
            action = FloatingCaptionService.ACTION_UPDATE
            putExtra(FloatingCaptionService.EXTRA_ORIGINAL, originalText)
            putExtra(FloatingCaptionService.EXTRA_TRANSLATED, translatedText)
            putExtra(FloatingCaptionService.EXTRA_MODE, displayMode)
            putExtra(FloatingCaptionService.EXTRA_TARGET, targetLanguage)
        }
        try { startService(update) } catch (_: Exception) { }
    }

    private fun requestInternalCapture() {
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
            }
            startForegroundService(service)
            status.text = "مجوز صدای داخلی صادر شد؛ موتور تبدیل صدا به متن هنوز متصل نیست."
        } else if (requestCode == 52) status.text = "مجوز دریافت صدای داخلی لغو شد."
    }
    override fun onDestroy() { recognizer?.destroy(); super.onDestroy() }
}
