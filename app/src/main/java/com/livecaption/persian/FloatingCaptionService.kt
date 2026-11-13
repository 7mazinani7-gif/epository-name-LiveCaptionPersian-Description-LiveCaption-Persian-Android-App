package com.livecaption.persian

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Floating caption surface. It stays above other apps after the user grants overlay permission.
 * Speech recognition and translation engines will publish real text in a later integration step. */
class FloatingCaptionService : Service() {
    companion object {
        const val ACTION_START = "com.livecaption.persian.FLOATING_START"
        const val ACTION_UPDATE = "com.livecaption.persian.FLOATING_UPDATE"
        const val ACTION_STOP = "com.livecaption.persian.FLOATING_STOP"
        const val EXTRA_ORIGINAL = "original"
        const val EXTRA_TRANSLATED = "translated"
        const val EXTRA_MODE = "mode"
        const val EXTRA_TARGET = "target"
    }

    private lateinit var windowManager: WindowManager
    private var panel: LinearLayout? = null
    private var originalView: TextView? = null
    private var translatedView: TextView? = null
    private var originalTag: TextView? = null
    private var translatedTag: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var currentMode = 2
    private var target = "فارسی"
    private var original = "زیرنویس زبان اصلی اینجا نمایش داده می‌شود."
    private var translated = "ترجمه در این قسمت نمایش داده می‌شود."

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "floating_caption")
            .setContentTitle("LiveCaption Persian")
            .setContentText("پنجره زیرنویس شناور فعال است")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
        else @Suppress("DEPRECATION") Notification.Builder(this)
            .setContentTitle("LiveCaption Persian").setContentText("پنجره زیرنویس شناور فعال است")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
        startForeground(8, notification)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { removePanel(); stopForeground(true); stopSelf(); return START_NOT_STICKY }
            ACTION_START, ACTION_UPDATE -> {
                original = intent.getStringExtra(EXTRA_ORIGINAL) ?: original
                translated = intent.getStringExtra(EXTRA_TRANSLATED) ?: translated
                currentMode = intent.getIntExtra(EXTRA_MODE, currentMode)
                target = intent.getStringExtra(EXTRA_TARGET) ?: target
                if (Settings.canDrawOverlays(this)) {
                    if (panel == null) createPanel()
                    renderText()
                }
            }
        }
        return START_STICKY
    }

    private fun createPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(12), dp(15), dp(12))
            background = rounded(Color.argb(242, 24, 27, 43), 18)
            elevation = dp(8).toFloat()
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "●  LIVE CAPTION"; textSize = 10f; setTextColor(Color.rgb(158, 151, 255))
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val close = TextView(this).apply {
            text = "×"; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setOnClickListener { removePanel(); stopForeground(true); stopSelf() }
        }
        header.addView(close, LinearLayout.LayoutParams(dp(30), dp(30)))
        root.addView(header)
        originalTag = tag("زبان اصلی", Color.rgb(187, 178, 255))
        root.addView(originalTag)
        originalView = captionText()
        root.addView(originalView)
        val divider = View(this).apply { setBackgroundColor(Color.rgb(75, 79, 100)) }
        root.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
        translatedTag = tag("ترجمه • $target", Color.rgb(105, 224, 205))
        root.addView(translatedTag)
        translatedView = captionText()
        root.addView(translatedView)

        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.90f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(90) }
        layoutParams = params
        root.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0; private var startY = 0; private var touchX = 0f; private var touchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { startX = params.x; startY = params.y; touchX = event.rawX; touchY = event.rawY; return true }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (event.rawX - touchX).toInt()
                        params.y = startY + (event.rawY - touchY).toInt()
                        try { windowManager.updateViewLayout(root, params) } catch (_: Exception) { }
                        return true
                    }
                }
                return false
            }
        })
        panel = root
        try { windowManager.addView(root, params) } catch (_: Exception) { panel = null }
    }

    private fun renderText() {
        originalView?.text = original
        translatedView?.text = translated
        originalView?.visibility = if (currentMode == 1) View.GONE else View.VISIBLE
        originalTag?.visibility = if (currentMode == 1) View.GONE else View.VISIBLE
        translatedView?.visibility = if (currentMode == 0) View.GONE else View.VISIBLE
        translatedTag?.visibility = if (currentMode == 0) View.GONE else View.VISIBLE
        translatedTag?.text = "ترجمه • $target"
    }

    private fun captionText() = TextView(this).apply {
        textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.RIGHT
        setPadding(0, dp(6), 0, dp(9)); setLineSpacing(dp(2).toFloat(), 1f)
    }
    private fun tag(text: String, color: Int) = TextView(this).apply {
        this.text = text; textSize = 10f; setTextColor(color); gravity = Gravity.RIGHT
        setPadding(0, dp(5), 0, 0)
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun removePanel() {
        panel?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
        panel = null
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("floating_caption", "Floating captions", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    override fun onDestroy() { removePanel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
