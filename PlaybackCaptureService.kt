package com.livecaption.persian

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

/** Captures eligible playback audio and transcribes it locally with the selected Vosk model. */
class PlaybackCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var recorder: AudioRecord? = null
    private var model: Model? = null
    private var speechRecognizer: Recognizer? = null
    @Volatile private var running = false
    @Volatile private var destroyed = false
    private var captureThread: Thread? = null
    private lateinit var selectedModel: SpeechModel

    companion object {
        const val ACTION_TRANSCRIPT = "com.livecaption.persian.TRANSCRIPT"
        const val ACTION_STATUS = "com.livecaption.persian.CAPTURE_STATUS"
        const val EXTRA_TEXT = "text"
        const val EXTRA_FINAL = "is_final"
        const val EXTRA_RUNNING = "is_running"
        const val ACTION_STOP = "com.livecaption.persian.STOP_CAPTURE"
        const val EXTRA_SOURCE_LANGUAGE = "source_language"
        private data class SpeechModel(val url: String, val directory: String, val label: String, val maxExpandedBytes: Long)
        private fun speechModel(code: String): SpeechModel = when (code) {
            "en-US" -> SpeechModel("https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip", "vosk-model-small-en-us-0.15", "انگلیسی", 180L * 1024 * 1024)
            "ar" -> SpeechModel("https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip", "vosk-model-ar-mgb2-0.4", "عربی", 700L * 1024 * 1024)
            else -> SpeechModel("https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip", "vosk-model-small-fa-0.42", "فارسی", 180L * 1024 * 1024)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            running = false
            sendStatus("دریافت صدای داخلی متوقف شد.", false)
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        // MediaProjection consent tokens are single-use. Ignore accidental duplicate starts.
        if (projection != null || destroyed) return START_NOT_STICKY
        createChannel()
        val notification = Notification.Builder(this, "capture")
            .setContentTitle("LiveCaption Persian")
            .setContentText("آماده‌سازی تشخیص گفتار فارسی آفلاین")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        startForeground(7, notification)
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("projectionData")
        if (resultCode != Activity.RESULT_OK || data == null) {
            sendStatus("اطلاعات مجوز ضبط معتبر نیست.", false)
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val sourceCode = intent?.getStringExtra(EXTRA_SOURCE_LANGUAGE) ?: "fa-IR"
            selectedModel = speechModel(sourceCode)
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            projectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    running = false
                    try { recorder?.stop() } catch (_: Exception) { }
                    sendStatus("دسترسی ضبط صفحه/صدای داخلی متوقف شد.", false)
                    stopSelf()
                }
            }
            projection?.registerCallback(projectionCallback!!, android.os.Handler(mainLooper))
            prepareModelAndCapture()
        } catch (e: Exception) {
            sendStatus("شروع دریافت صدای داخلی ناموفق بود: ${e.message ?: "خطای ناشناخته"}", false)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun prepareModelAndCapture() {
        thread(name = "vosk-model-init") {
            try {
                val modelDirectory = File(filesDir, selectedModel.directory)
                if (!isModelComplete(modelDirectory)) {
                    val sizeHint = if (selectedModel.label == "عربی") "حدود ۳۱۸ مگابایت" else if (selectedModel.label == "انگلیسی") "حدود ۴۰ مگابایت" else "حدود ۵۳ مگابایت"
                    sendStatus("مدل ${selectedModel.label} در حال دانلود است ($sizeHint)…")
                    downloadAndExtractModel(modelDirectory)
                }
                if (destroyed) return@thread
                ensureModelConfig(modelDirectory)
                model = Model(modelDirectory.absolutePath)
                speechRecognizer = Recognizer(model, 16000.0f)
                if (!destroyed) {
                    startCapture()
                    sendStatus("مدل ${selectedModel.label} آماده شد؛ دریافت و تبدیل صدای داخلی شروع شد.", true)
                }
            } catch (e: Exception) {
                if (!destroyed) {
                    Log.e("LiveCaption", "Vosk initialization failed", e)
                    sendStatus("آماده‌سازی مدل گفتار ناموفق بود: ${e.message ?: "خطا"}", false)
                    stopSelf()
                }
            }
        }
    }

    // Vosk's modern model layout needs conf/model.conf, but official model ZIPs
    // commonly omit it. Create the standard decoding configuration locally.
    private fun ensureModelConfig(directory: File) {
        val config = File(directory, "conf/model.conf")
        if (!config.isFile) {
            config.parentFile?.mkdirs()
            config.writeText(
                "--min-active=200\n" +
                "--max-active=3000\n" +
                "--beam=10.0\n" +
                "--lattice-beam=2.0\n" +
                "--acoustic-scale=1.0\n" +
                "--frame-subsampling-factor=3\n" +
                "--endpoint.silence-phones=1:2:3:4:5:6:7:8:9:10\n" +
                "--endpoint.rule2.min-trailing-silence=0.5\n" +
                "--endpoint.rule3.min-trailing-silence=1.0\n" +
                "--endpoint.rule4.min-trailing-silence=2.0\n",
                Charsets.UTF_8
            )
        }
    }

    private fun isModelComplete(directory: File): Boolean {
        val acousticModel = File(directory, "am/final.mdl").isFile
        val featureConfig = File(directory, "conf/mfcc.conf").isFile || File(directory, "conf/fbank.conf").isFile
        val graph = File(directory, "graph/HCLG.fst").isFile ||
            (File(directory, "graph/HCLr.fst").isFile && File(directory, "graph/Gr.fst").isFile)
        return acousticModel && featureConfig && graph
    }

    private fun downloadAndExtractModel(destination: File) {
        val archive = File(cacheDir, "vosk-${selectedModel.directory}.zip.part")
        archive.delete()
        val connection = (URL(selectedModel.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000; readTimeout = 60000; instanceFollowRedirects = true
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
            val expected = connection.contentLengthLong
            connection.inputStream.use { input -> FileOutputStream(archive).use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    if (destroyed) throw InterruptedException("Service stopped")
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n); total += n
                    if (expected > 0 && total % (5L * 1024 * 1024) < n) {
                        val percent = (total * 100 / expected).toInt().coerceIn(0, 99)
                        sendStatus("دانلود مدل ${selectedModel.label}: $percent٪")
                    }
                }
                output.fd.sync()
                if (expected > 0 && total != expected) throw IllegalStateException("دانلود مدل ناقص است")
            } }
        } finally { connection.disconnect() }
        val parent = destination.parentFile ?: filesDir
        parent.mkdirs()
        val canonicalRoot = parent.canonicalPath + File.separator
        var expanded = 0L
        var entries = 0
        try {
            ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (++entries > 3000) throw SecurityException("Too many model archive entries")
                    val out = File(parent, entry.name)
                    if (!out.canonicalPath.startsWith(canonicalRoot)) throw SecurityException("Invalid model archive path")
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = zip.read(buffer)
                                if (n < 0) break
                                expanded += n
                                if (expanded > selectedModel.maxExpandedBytes) throw SecurityException("Model archive is unexpectedly large")
                                output.write(buffer, 0, n)
                            }
                        }
                    }
                    zip.closeEntry(); entry = zip.nextEntry
                }
            }
            if (!isModelComplete(destination)) {
                val candidate = parent.listFiles()?.firstOrNull { it.isDirectory && it.name == selectedModel.directory && isModelComplete(it) }
                if (candidate != null && candidate != destination) {
                    if (destination.exists()) destination.deleteRecursively()
                    if (!candidate.renameTo(destination)) candidate.copyRecursively(destination, overwrite = true)
                    if (candidate.exists() && candidate != destination) candidate.deleteRecursively()
                }
            }
            if (!isModelComplete(destination)) throw IllegalStateException("فایل‌های مدل ${selectedModel.label} ناقص دانلود شدند")
        } finally { archive.delete() }
    }

    private fun startCapture() {
        val p = projection ?: throw IllegalStateException("MediaProjection در دسترس نیست")
        val sampleRate = 16000
        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
        val config = AudioPlaybackCaptureConfiguration.Builder(p)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) throw IllegalStateException("AudioRecord buffer unavailable: $min")
        recorder = AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(min * 2)
            .setAudioPlaybackCaptureConfig(config).build()
        if (recorder?.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("AudioRecord راه‌اندازی نشد")
        recorder?.startRecording()
        if (recorder?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            throw IllegalStateException("AudioRecord شروع به ضبط نکرد")
        }
        running = true
        captureThread = thread(name = "playback-asr") {
            val shortBuffer = ShortArray(2048)
            val byteBuffer = ByteArray(shortBuffer.size * 2)
            try {
                while (running) {
                    val count = recorder?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (count > 0) {
                        for (i in 0 until count) {
                            val value = shortBuffer[i].toInt()
                            byteBuffer[i * 2] = (value and 0xff).toByte()
                            byteBuffer[i * 2 + 1] = ((value shr 8) and 0xff).toByte()
                        }
                        val r = speechRecognizer ?: continue
                        if (r.acceptWaveForm(byteBuffer, count * 2)) {
                            val text = JSONObject(r.result).optString("text")
                            if (text.isNotBlank()) sendTranscript(text, true)
                        } else {
                            val partial = JSONObject(r.partialResult).optString("partial")
                            if (partial.isNotBlank()) sendTranscript(partial, false)
                        }
                    } else if (count < 0) {
                        Log.w("LiveCaption", "AudioRecord read error: $count")
                        if (count == AudioRecord.ERROR_DEAD_OBJECT ||
                            count == AudioRecord.ERROR_INVALID_OPERATION ||
                            count == AudioRecord.ERROR_BAD_VALUE) {
                            throw IllegalStateException("AudioRecord read failed: $count")
                        }
                        Thread.sleep(50)
                    }
                }
                if (!destroyed) {
                    val tail = try { JSONObject(speechRecognizer?.finalResult ?: "{}").optString("text") } catch (_: Exception) { "" }
                    if (tail.isNotBlank()) sendTranscript(tail, true)
                }
            } catch (e: Exception) {
                Log.e("LiveCaption", "Playback transcription failed", e)
                running = false
                sendStatus("خطا در تشخیص صدای داخلی: ${e.message ?: "خطا"}", false)
                stopSelf()
            }
        }
    }

    private fun sendTranscript(text: String, isFinal: Boolean) {
        sendBroadcast(Intent(ACTION_TRANSCRIPT).setPackage(packageName)
            .putExtra(EXTRA_TEXT, text).putExtra(EXTRA_FINAL, isFinal))
    }
    private fun sendStatus(message: String, isRunning: Boolean? = null) {
        val statusIntent = Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_TEXT, message)
        if (isRunning != null) statusIntent.putExtra(EXTRA_RUNNING, isRunning)
        sendBroadcast(statusIntent)
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("capture", "Live captions", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    override fun onDestroy() {
        destroyed = true
        running = false
        try { recorder?.stop() } catch (_: Exception) { }
        try { captureThread?.join(1200) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        try { recorder?.release() } catch (_: Exception) { }
        try { speechRecognizer?.close() } catch (_: Exception) { }
        try { model?.close() } catch (_: Exception) { }
        try { projectionCallback?.let { projection?.unregisterCallback(it) } } catch (_: Exception) { }
        try { projection?.stop() } catch (_: Exception) { }
        projectionCallback = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
