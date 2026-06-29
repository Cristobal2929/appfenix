package com.fenix.app

import android.view.View
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
import android.widget.ImageView
import android.widget.Toast
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
        private var layoutParams: WindowManager.LayoutParams? = null

        override fun onBind(intent: Intent?): IBinder? = null

        override fun onCreate() {
            super.onCreate()
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            bubbleView = ImageView(this).apply {
                setImageResource(android.R.drawable.ic_dialog_info)
                setBackgroundColor(Color.parseColor("#66000000"))
                setOnTouchListener(BubbleTouchListener())
                setOnClickListener {
                    val intent = Intent(this@MainActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            }

            layoutParams = WindowManager.LayoutParams(
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

            windowManager.addView(bubbleView, layoutParams)
        }

        override fun onDestroy() {
            super.onDestroy()
            if (::bubbleView.isInitialized) {
                windowManager.removeView(bubbleView)
            }
        }

        private inner class BubbleTouchListener : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams?.x ?: 0
                        initialY = layoutParams?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        layoutParams?.x = initialX + dx
                        layoutParams?.y = initialY + dy
                        windowManager.updateViewLayout(bubbleView, layoutParams)
                        return true
                    }
                }
                return false
            }
        }
    }
}