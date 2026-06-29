package com.fenix.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fenix.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var clipboardManager: ClipboardManager
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var tts: TextToSpeech? = null
    private var isActive = true
    private var lastOriginalText: String = ""

    private val languageMap = mapOf(
        "Francés" to "fr",
        "Inglés" to "en",
        "Italiano" to "it",
        "Portugués" to "pt",
        "Alemán" to "de"
    )

    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Spinner
        val adapter = androidx.appcompat.widget.ArrayAdapter.createFromResource(
            this,
            R.array.source_languages,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter

        // Initialize TextToSpeech
        tts = TextToSpeech(this, this)

        // Button listener
        binding.btnPauseResume.setOnClickListener {
            isActive = !isActive
            updateStatusUI()
        }

        updateStatusUI()
    }

    private fun updateStatusUI() {
        if (isActive) {
            binding.btnPauseResume.text = getString(R.string.pause)
            binding.viewStatus.setBackgroundColor(getColor(android.R.color.holo_green_dark))
        } else {
            binding.btnPauseResume.text = getString(R.string.resume)
            binding.viewStatus.setBackgroundColor(getColor(android.R.color.holo_red_dark))
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale("es", "ES")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts?.setLanguage(locale)
            } else {
                @Suppress("DEPRECATION")
                tts?.setLanguage(locale)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            if (!isActive) return@OnPrimaryClipChangedListener
            val clip: ClipData = clipboardManager.primaryClip ?: return@OnPrimaryClipChangedListener
            val item = clip.getItemAt(0)
            var text = item.text?.toString() ?: return@OnPrimaryClipChangedListener
            if (text == lastOriginalText) return@OnPrimaryClipChangedListener
            if (text.length > 500) {
                text = text.substring(0, 500)
            }
            lastOriginalText = text
            val sourceLang = languageMap[binding.spinnerLanguage.selectedItem.toString()] ?: "fr"
            lifecycleScope.launch {
                val translated = translateText(text, sourceLang)
                if (translated != null) {
                    withContext(Dispatchers.Main) {
                        binding.tvOriginal.text = text
                        binding.tvTranslated.text = translated
                        tts?.speak(translated, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_ID")
                    }
                }
            }
        }
        clipboardManager.addPrimaryClipChangedListener(clipboardListener!!)
    }

    override fun onPause() {
        super.onPause()
        clipboardListener?.let { clipboardManager.removePrimaryClipChangedListener(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
    }

    private suspend fun translateText(text: String, sourceLang: String): String? {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = "https://api.mymemory.translated.net/get?q=$encodedText&langpair=$sourceLang|es"
        val request = Request.Builder().url(url).build()
        return withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.string() ?: return@withContext null
                    val json = JSONObject(body)
                    val responseData = json.getJSONObject("responseData")
                    responseData.getString("translatedText")
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}