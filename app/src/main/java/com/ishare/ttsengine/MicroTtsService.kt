package com.ishare.ttsengine

import android.media.AudioFormat
import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.media.MediaPlayer
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import com.ishare.MoxiangApplication
import com.microsoft.cognitiveservices.speech.ResultReason
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import org.apache.commons.codec.digest.DigestUtils
import java.io.FileInputStream
import java.io.StringWriter
import java.util.*
import java.util.logging.Logger
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
    lateinit var app: MoxiangApplication
    lateinit var fileCachePath: String
    lateinit var callback: SynthesisCallback
    private val player by lazy { MediaPlayer() }
    val logger = Logger.getLogger("TAG")

    @Volatile
    private var mStopRequested = false

    /*
     * We multiply by a factor of two since each sample contains 16 bits (2 bytes).
     */
    private val mAudioBuffer = ByteArray(SAMPLING_RATE_HZ * 2)

    override fun onCreate() {
        super.onCreate()
        app = application as MoxiangApplication
        initEngine()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }

    fun initEngine() {
        speechConfig = SpeechConfig.fromSubscription("9644ad9e4a40402a83462228bfeca076", "eastus")
        cfg = Configuration(Configuration.VERSION_2_3_24)
        cfg.setDirectoryForTemplateLoading(app.offlineDir)
        cfg.defaultEncoding = "UTF-8"
        cfg.templateExceptionHandler = TemplateExceptionHandler.DEBUG_HANDLER;
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
        player.stop()
        player.release()
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback) {
        this.callback = callback
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
            callback.error()
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
        callback.start(SAMPLING_RATE_HZ,
                AudioFormat.ENCODING_PCM_16BIT, 1 /* Number of channels. */)
        val text = request.charSequenceText.toString()
        logger.info("text=${text}")
        if (text.isEmpty()) {
            callback.error()
            return
        }
        fileCachePath = getExternalFilesDir(null).absolutePath + "/${text.md5()}.wav"
        audioConfig = AudioConfig.fromWavFileOutput(fileCachePath)
        synthesizer = SpeechSynthesizer(speechConfig, audioConfig)
        val result = synthesizer.SpeakSsml(text.toSsml(cfg))
        if (result.reason == ResultReason.SynthesizingAudioCompleted) {
            val file = fileCachePath
            try {
                player.setDataSource(FileInputStream(file).fd)
                player.prepareAsync()
            } catch (e: Exception) {
                e.printStackTrace()
                callback.done()
            }
        }
    }

    override fun onPrepared(mp: MediaPlayer?) {
        if (!mStopRequested) {
            logger.info("prepared.")
            mp?.start()
            callback.start(0, ENCODING_PCM_16BIT, 1)
        }
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        logger.info("onError.${what}")
        player.reset()
        callback.error()
        return true
    }

    override fun onCompletion(mp: MediaPlayer?) {
        logger.info("completion.")
        player.reset()
        callback.done()
    }


}

fun String.toSsml(cfg: Configuration): String {
    val root = HashMap<String, Any>()
    root["text"] = this
    val temp = cfg.getTemplate("ssml.ftlx")
    val out = StringWriter()
    temp.process(root, out)
    return out.toString()
}

fun String.md5(): String {
    return DigestUtils.md5Hex(this)
}