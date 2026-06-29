package com.fenix.app

import android.view.View
import android.widget.TextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import android.content.Intent

import android.Manifest
import android.app.*
import android.content.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.fenix.app.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val client = OkHttpClient()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        private const val REQUEST_CODE_OVERLAY = 1001
        private const val REQUEST_CODE_AUDIO = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkOverlayPermission()
        checkAudioPermission()

        setupTabs()
        setupVoiceButtons()
        setupTextButtons()
        startFloatingBubble()
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_CODE_OVERLAY)
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_CODE_AUDIO
            )
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.tab_voice)))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.tab_text)))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        binding.voiceLayout.isVisible = true
                        binding.textLayout.isVisible = false
                    }
                    1 -> {
                        binding.voiceLayout.isVisible = false
                        binding.textLayout.isVisible = true
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        // Default to voice tab
        binding.tabLayout.getTabAt(0)?.select()
    }

    private fun setupVoiceButtons() {
        binding.btnStartVoice.setOnClickListener {
            if (!isListening) startListening()
        }
        binding.btnStopVoice.setOnClickListener {
            if (isListening) stopListening()
        }
    }

    private fun setupTextButtons() {
        binding.btnTranslate.setOnClickListener {
            val text = binding.editTextInput.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                translateText(text)
            } else {
                Snackbar.make(binding.root, getString(R.string.error_empty), Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.btnCopy.setOnClickListener {
            val result = binding.textViewResult.text?.toString()
            if (!result.isNullOrEmpty()) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("translation", result)
                clipboard.setPrimaryClip(clip)
                Snackbar.make(binding.root, getString(R.string.copied), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun startListening() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    isListening = false
                    binding.textViewVoiceResult.text = getString(R.string.error_speech)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spokenText = matches?.firstOrNull() ?: ""
                    binding.textViewVoiceResult.text = spokenText
                    translateText(spokenText)
                    // Restart listening for continuous mode
                    if (isListening) startListening()
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
        }
        speechRecognizer?.startListening(intent)
        isListening = true
        binding.textViewVoiceResult.text = getString(R.string.listening)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        binding.textViewVoiceResult.text = getString(R.string.stopped)
    }

    private fun translateText(text: String) {
        coroutineScope.launch {
            binding.progressBar.isVisible = true
            val url = "https://api.mymemory.translated.net/get?q=${Uri.encode(text)}&langpair=fr|es"
            val request = Request.Builder().url(url).build()
            try {
                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                val body = response.body?.string()
                val json = JSONObject(body ?: "")
                val translation = json.getJSONObject("responseData").getString("translatedText")
                binding.textViewResult.text = translation
                binding.textViewVoiceResult.text = translation // also show in voice tab
            } catch (e: Exception) {
                Snackbar.make(binding.root, getString(R.string.error_translation), Snackbar.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun startFloatingBubble() {
        val intent = Intent(this, FloatingService::class.java)
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        coroutineScope.cancel()
        stopService(Intent(this, FloatingService::class.java))
    }

    // ------------------- Floating Service -------------------
    inner class FloatingService : Service() {

        private lateinit var windowManager: WindowManager
        private lateinit var bubbleView: ImageView
        private var bubbleParams: WindowManager.LayoutParams? = null

        private var panelView: View? = null
        private var panelParams: WindowManager.LayoutParams? = null

        // Resources for panel
        private lateinit var panelTabLayout: TabLayout
        private lateinit var panelVoiceLayout: LinearLayout
        private lateinit var panelTextLayout: LinearLayout
        private lateinit var panelEditText: EditText
        private lateinit var panelBtnTranslate: MaterialButton
        private lateinit var panelBtnCopy: MaterialButton
        private lateinit var panelBtnStartVoice: MaterialButton
        private lateinit var panelBtnStopVoice: MaterialButton
        private lateinit var panelVoiceResult: TextView
        private lateinit var panelTextResult: TextView
        private lateinit var panelProgressBar: ProgressBar
        private lateinit var panelCloseBtn: ImageView

        private var panelSpeechRecognizer: SpeechRecognizer? = null
        private var panelIsListening = false
        private val panelClient = OkHttpClient()
        private val panelScope = CoroutineScope(Dispatchers.Main + Job())

        override fun onBind(intent: Intent?): IBinder? = null

        override fun onCreate() {
            super.onCreate()
            startForegroundService()
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            // --- Bubble ---
            bubbleView = ImageView(this).apply {
                setImageResource(android.R.drawable.ic_dialog_info)
                setBackgroundColor(Color.parseColor("#66000000"))
                setOnTouchListener(BubbleTouchListener())
                setOnClickListener { togglePanel() }
            }

            bubbleParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 100
            }

            windowManager.addView(bubbleView, bubbleParams)
        }

        private fun startForegroundService() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelId = "floating_service_channel"
                val channel = NotificationChannel(
                    channelId,
                    "Floating Service",
                    NotificationManager.IMPORTANCE_LOW
                )
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)

                val notification = Notification.Builder(this, channelId)
                    .setContentTitle("Floating Service")
                    .setContentText("Bubble active")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build()
                startForeground(1, notification)
            } else {
                val notification = Notification.Builder(this)
                    .setContentTitle("Floating Service")
                    .setContentText("Bubble active")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build()
                startForeground(1, notification)
            }
        }

        private fun togglePanel() {
            if (panelView == null) {
                createPanel()
            } else {
                removePanel()
            }
        }

        private fun createPanel() {
            // Root layout
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(16, 16, 16, 16)
                elevation = 8f
            }

            // Close button
            panelCloseBtn = ImageView(this).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setColorFilter(Color.BLACK)
                val size = 48
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.END
                }
                setOnClickListener { removePanel() }
            }
            root.addView(panelCloseBtn)

            // TabLayout
            panelTabLayout = TabLayout(this).apply {
                addTab(newTab().setText(getString(R.string.tab_voice)))
                addTab(newTab().setText(getString(R.string.tab_text)))
                setSelectedTabIndicatorColor(Color.parseColor("#FF6200EE"))
                setTabTextColors(Color.BLACK, Color.parseColor("#FF6200EE"))
            }
            root.addView(panelTabLayout)

            // Voice Layout
            panelVoiceLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.VISIBLE
            }

            panelVoiceResult = TextView(this).apply {
                text = getString(R.string.voice_result_placeholder)
                setBackgroundColor(Color.WHITE)
                setPadding(8, 8, 8, 8)
            }
            panelVoiceLayout.addView(panelVoiceResult)

            panelBtnStartVoice = MaterialButton(this).apply {
                text = getString(R.string.start)
                setStyle()
            }
            panelVoiceLayout.addView(panelBtnStartVoice)

            panelBtnStopVoice = MaterialButton(this).apply {
                text = getString(R.string.stop)
                setStyle()
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = 8
                layoutParams = lp
            }
            panelVoiceLayout.addView(panelBtnStopVoice)

            root.addView(panelVoiceLayout)

            // Text Layout
            panelTextLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }

            panelEditText = EditText(this).apply {
                hint = getString(R.string.hint_input)
                minLines = 3
                setBackgroundResource(android.R.drawable.edit_text)
                setPadding(8, 8, 8, 8)
            }
            panelTextLayout.addView(panelEditText)

            panelBtnTranslate = MaterialButton(this).apply {
                text = getString(R.string.translate)
                setStyle()
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = 8
                layoutParams = lp
            }
            panelTextLayout.addView(panelBtnTranslate)

            panelTextResult = TextView(this).apply {
                text = getString(R.string.translation_placeholder)
                setBackgroundColor(Color.WHITE)
                setPadding(8, 8, 8, 8)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = 8
                layoutParams = lp
            }
            panelTextLayout.addView(panelTextResult)

            panelBtnCopy = MaterialButton(this).apply {
                text = getString(R.string.copy)
                setStyle()
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = 8
                layoutParams = lp
            }
            panelTextLayout.addView(panelBtnCopy)

            root.addView(panelTextLayout)

            // ProgressBar
            panelProgressBar = ProgressBar(this).apply {
                isIndeterminate = true
                visibility = View.GONE
            }
            root.addView(panelProgressBar)

            // Tab switching
            panelTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    when (tab.position) {
                        0 -> {
                            panelVoiceLayout.visibility = View.VISIBLE
                            panelTextLayout.visibility = View.GONE
                        }
                        1 -> {
                            panelVoiceLayout.visibility = View.GONE
                            panelTextLayout.visibility = View.VISIBLE
                        }
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
            panelTabLayout.getTabAt(0)?.select()

            // Button actions
            panelBtnStartVoice.setOnClickListener {
                if (!panelIsListening) panelStartListening()
            }
            panelBtnStopVoice.setOnClickListener {
                if (panelIsListening) panelStopListening()
            }
            panelBtnTranslate.setOnClickListener {
                val txt = panelEditText.text?.toString()?.trim()
                if (!txt.isNullOrEmpty()) {
                    panelTranslate(txt)
                } else {
                    Toast.makeText(this, getString(R.string.error_empty), Toast.LENGTH_SHORT).show()
                }
            }
            panelBtnCopy.setOnClickListener {
                val res = panelTextResult.text?.toString()
                if (!res.isNullOrEmpty()) {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("translation", res)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
                }
            }

            // LayoutParams for panel
            panelParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = bubbleParams?.x ?: 0
                y = (bubbleParams?.y ?: 0) + 150
            }

            panelView = root
            windowManager.addView(panelView, panelParams)
        }

        private fun removePanel() {
            panelView?.let {
                windowManager.removeView(it)
                panelView = null
                panelParams = null
                panelSpeechRecognizer?.destroy()
                panelScope.cancel()
            }
        }

        private fun panelStartListening() {
            if (panelSpeechRecognizer == null) {
                panelSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                panelSpeechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        panelIsListening = false
                        panelVoiceResult.text = getString(R.string.error_speech)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spoken = matches?.firstOrNull() ?: ""
                        panelVoiceResult.text = spoken
                        panelTranslate(spoken)
                        if (panelIsListening) panelStartListening()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            }
            panelSpeechRecognizer?.startListening(intent)
            panelIsListening = true
            panelVoiceResult.text = getString(R.string.listening)
        }

        private fun panelStopListening() {
            panelSpeechRecognizer?.stopListening()
            panelIsListening = false
            panelVoiceResult.text = getString(R.string.stopped)
        }

        private fun panelTranslate(text: String) {
            panelScope.launch {
                panelProgressBar.isVisible = true
                val url = "https://api.mymemory.translated.net/get?q=${Uri.encode(text)}&langpair=fr|es"
                val request = Request.Builder().url(url).build()
                try {
                    val response = withContext(Dispatchers.IO) { panelClient.newCall(request).execute() }
                    val body = response.body?.string()
                    val json = JSONObject(body ?: "")
                    val translation = json.getJSONObject("responseData").getString("translatedText")
                    panelTextResult.text = translation
                    panelVoiceResult.text = translation
                } catch (e: Exception) {
                    Toast.makeText(this@FloatingService, getString(R.string.error_translation), Toast.LENGTH_SHORT).show()
                } finally {
                    panelProgressBar.isVisible = false
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            if (::bubbleView.isInitialized) {
                windowManager.removeView(bubbleView)
            }
            removePanel()
        }

        private inner class BubbleTouchListener : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = bubbleParams?.x ?: 0
                        initialY = bubbleParams?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        bubbleParams?.x = initialX + dx
                        bubbleParams?.y = initialY + dy
                        windowManager.updateViewLayout(bubbleView, bubbleParams)
                        return true
                    }
                }
                return false
            }
        }

        // Extension function to apply a simple style to MaterialButton
        private fun MaterialButton.setStyle() {
            setBackgroundColor(Color.parseColor("#FF6200EE"))
            setTextColor(Color.WHITE)
        }
    }
}