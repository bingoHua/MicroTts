package com.ishare.ttsengine

import android.media.AudioFormat
import android.media.MediaPlayer
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import com.ishare.MoxiangApplication
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import freemarker.template.Configuration
import java.io.File
import java.io.FileInputStream
import java.io.OutputStreamWriter
import java.util.*
import kotlin.collections.HashMap

private const val SAMPLING_RATE_HZ = 16000

class MicroTtsService : TextToSpeechService(), MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener {
    lateinit var speechConfig: SpeechConfig
    lateinit var audioConfig: AudioConfig
    lateinit var synthesizer: SpeechSynthesizer

    lateinit var mCurrentLanguage: Array<String>
    lateinit var cfg: Configuration
    private val player by lazy { MediaPlayer() }
    lateinit var app: MoxiangApplication

    @Volatile
    private var mStopRequested = false

    /*
     * We multiply by a factor of two since each sample contains 16 bits (2 bytes).
     */
    private val mAudioBuffer = ByteArray(SAMPLING_RATE_HZ * 2)

    lateinit var fileCachePath: String
    override fun onCreate() {
        super.onCreate()
        app = application as MoxiangApplication
        fileCachePath = this.cacheDir.absolutePath + "/file.wav"
        initEngine()
    }

    fun initEngine() {
        speechConfig = SpeechConfig.fromSubscription("94d6710439d04b7cb402fabcffd3b558", "eastus")
        audioConfig = AudioConfig.fromWavFileOutput(fileCachePath)
        synthesizer = SpeechSynthesizer(speechConfig, audioConfig)
        cfg = Configuration(Configuration.VERSION_2_3_31)

        cfg.setDirectoryForTemplateLoading(app.offlineDir)
        cfg.defaultEncoding = "UTF-8"

        player.setOnErrorListener(this)
        player.setOnPreparedListener(this)
        player.setOnCompletionListener(this)
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {


//        Log.i(TAG, "onIsLanguageAvailable lang=" + lang + " country=" + country + " variant=" + variant);
        return if (Locale.SIMPLIFIED_CHINESE.isO3Language == lang || Locale.US.isO3Language == lang) {
            if (Locale.SIMPLIFIED_CHINESE.isO3Country == country || Locale.US.isO3Country == country)
                TextToSpeech.LANG_COUNTRY_AVAILABLE
            else
                TextToSpeech.LANG_AVAILABLE
        } else
            TextToSpeech.LANG_NOT_SUPPORTED
    }

    override fun onGetLanguage(): Array<String> {
        return mCurrentLanguage
    }

    override fun onLoadLanguage(lang: String, country: String, variant: String): Int {
        mCurrentLanguage = arrayOf(lang, country, "")
        return onIsLanguageAvailable(lang, country, variant)
    }

    override fun onStop() {
        mStopRequested = true
        player.release()
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        // Note that we call onLoadLanguage here since there is no guarantee
        // that there would have been a prior call to this function.
        // Note that we call onLoadLanguage here since there is no guarantee
        // that there would have been a prior call to this function.
        val load = onLoadLanguage(request!!.language, request.country,
                request.variant)
        // We might get requests for a language we don't support - in which case
        // we error out early before wasting too much time.
        // We might get requests for a language we don't support - in which case
        // we error out early before wasting too much time.
        if (load == TextToSpeech.LANG_NOT_SUPPORTED) {
            callback!!.error()
            return
        }
        // At this point, we have loaded the language we need for synthesis and
        // it is guaranteed that we support it so we proceed with synthesis.
        // We denote that we are ready to start sending audio across to the
        // framework. We use a fixed sampling rate (16khz), and send data across
        // in 16bit PCM mono.
        // At this point, we have loaded the language we need for synthesis and
        // it is guaranteed that we support it so we proceed with synthesis.
        // We denote that we are ready to start sending audio across to the
        // framework. We use a fixed sampling rate (16khz), and send data across
        // in 16bit PCM mono.
        callback!!.start(SAMPLING_RATE_HZ,
                AudioFormat.ENCODING_PCM_16BIT, 1 /* Number of channels. */)
        // We then scan through each character of the request string and
        // generate audio for it.
        // We then scan through each character of the request string and
        // generate audio for it.
        val text = request.charSequenceText.toString()
        val result = synthesizer.SpeakText(
                "<speak version=\"1.0\" xml:lang=\"zh-CN\"><voice xml:lang=\"zh-CN\" xml:gender=\"Female\"" +
                        "    name=\"zh-CN-XiaoxiaoNeural\">" +
                        "${text}" +
                        "</voice></speak>")
        test(text)
        val file = fileCachePath
        try {
            player.setDataSource(FileInputStream(file).fd)
            player.prepareAsync()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Alright, we're done with our synthesis - yay!
        // Alright, we're done with our synthesis - yay!
        //callback.done()
    }

    override fun onPrepared(mp: MediaPlayer?) {
        mp?.start()
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        return true
    }

    override fun onCompletion(mp: MediaPlayer?) {
    }

    fun test(text: String) {
        val root = HashMap<String, Any>()
        root["text"] = text
        val temp = cfg.getTemplate("ssml.ftl")
        val out = OutputStreamWriter(System.out)
        temp.process(root, out)
    }
}