package com.ishare.ttsengine

import android.media.AudioFormat
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
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
import java.io.File
import java.io.FileInputStream
import java.io.StringWriter
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CyclicBarrier
import java.util.logging.Logger
import kotlin.collections.HashMap


private const val SAMPLING_RATE_HZ = 16000

class MicroTtsService : TextToSpeechService() {

    lateinit var mCurrentLanguage: Array<String>
    lateinit var app: MoxiangApplication
    lateinit var callback: SynthesisCallback
    lateinit var audioDownloader: AudioDownloader
    lateinit var speechManager: SpeechManager
    private val logger: Logger = Logger.getLogger("TAG")
    lateinit var handler: Handler

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
        mStopRequested = true
        speechManager.stopSpeech()
        audioDownloader.stopBlockingDownloadTask()
    }

    private fun initEngine() {
        audioDownloader = AudioDownloader(app)
        audioDownloader.setCallback(object : AudioDownloader.DownloadCallback {
            override fun complete(fileUrl: String, requestData: RequestData) {
                speechManager.addSpeechTask(AudioData(fileUrl, requestData))
            }
        })
        audioDownloader.starBlockingDownloadTask()
        speechManager = SpeechManager()
        speechManager.setCallback(object : SpeechManager.SpeechCallback {
            override fun singleSpeechComplete(requestData: RequestData) {
                /*callback.start(SAMPLING_RATE_HZ, AudioFormat.ENCODING_PCM_16BIT, 1)
                callback.rangeStart(0, length, 0)*/
                handler.post {
                    requestData.callback.rangeStart(0, requestData.request.charSequenceText.length, 0)
                }
            }

            override fun startSpeech(requestData: RequestData) {
                /*callback.start(SAMPLING_RATE_HZ, AudioFormat.ENCODING_PCM_16BIT, 1)
                callback.rangeStart(0, length, 0)
*/
                /*     callback.start(
                             0,
                             AudioFormat.ENCODING_PCM_16BIT, 1)*/
                handler.post {
                    requestData.callback.start(0,
                            AudioFormat.ENCODING_PCM_16BIT, 1)
                }

            }

            override fun speechError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
                callback.error()
                //todo,回调有点问题
                return true
            }

        })
        speechManager.startSpeech()
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
        speechManager.stopSpeech()
        audioDownloader.stopBlockingDownloadTask()
    }

    inner class RequestData(val request: SynthesisRequest, val callback: SynthesisCallback)

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        this.callback = callback
        handler = Handler(Looper.myLooper())
        // Note that we call onLoadLanguage here since there is no guarantee
        // that there would have been a prior call to this function.
        // Note that we call onLoadLanguage here since there is no guarantee
        // that there would have been a prior call to this function.
        val load = onLoadLanguage(request.language, request.country,
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
        /*callback.start(SAMPLING_RATE_HZ,
                AudioFormat.ENCODING_PCM_16BIT, 1 *//* Number of channels. *//*)*/
        val text = request.charSequenceText.toString()
        logger.info("text=${text}")
        if (text.isEmpty()) {
            this.callback.error()
            return
        }
        if (!mStopRequested) {
            audioDownloader.addTask(RequestData(request, callback))
        }
    }

    class AudioDownloader constructor(val app: MoxiangApplication) {
        private var speechConfig: SpeechConfig = SpeechConfig.fromSubscription("9644ad9e4a40402a83462228bfeca076", "eastus")
        private var cfg: Configuration = Configuration(Configuration.VERSION_2_3_24)

        private lateinit var audioConfig: AudioConfig
        private lateinit var synthesizer: SpeechSynthesizer
        private val inQueue = ArrayBlockingQueue<RequestData>(100)
        private var downloadCallback: DownloadCallback? = null
        private var isStop = true
        private val logger: Logger = Logger.getLogger("AudioDownloader")

        init {
            cfg.setDirectoryForTemplateLoading(app.offlineDir)
            cfg.defaultEncoding = "UTF-8"
            cfg.templateExceptionHandler = TemplateExceptionHandler.DEBUG_HANDLER;
        }

        fun addTask(request: RequestData) {
            inQueue.put(request)
        }

        fun starBlockingDownloadTask() {
            logger.info("starBlockingDownloadTask")
            if (isStop) {
                Thread(object : Runnable {
                    override fun run() {
                        while (!isStop) {
                            val requestData = inQueue.take()
                            if (!isStop) {
                                logger.info("startDownload.text${requestData.request.charSequenceText}")
                                download(requestData.request.charSequenceText.toString()).apply {
                                    if (this.isNotEmpty()) {
                                        logger.info("downloadComplete.text${requestData.request.charSequenceText}")
                                        downloadCallback?.complete(this, requestData)
                                    }
                                }
                            }
                        }
                    }
                }).start()
            } else {
                throw RuntimeException("starBlockingDownloadTask already started!!")
            }
            isStop = false
        }

        fun stopBlockingDownloadTask() {
            isStop = true
        }

        fun download(text: String): String {
            val fileCachePath = app.getExternalFilesDir(null).absolutePath + "/${text.md5()}.wav"
            return if (File(fileCachePath).exists()) {
                fileCachePath
            } else {
                audioConfig = AudioConfig.fromWavFileOutput(fileCachePath)
                synthesizer = SpeechSynthesizer(speechConfig, audioConfig)
                val result = synthesizer.SpeakSsml(text.toSsml(cfg))
                if (result.reason == ResultReason.SynthesizingAudioCompleted) fileCachePath else ""
            }
        }

        fun setCallback(callback: DownloadCallback) {
            this.downloadCallback = callback
        }

        interface DownloadCallback {
            fun complete(fileUrl: String, requestData: RequestData)
        }

    }

    data class AudioData(val audioUrl: String, val requestData: RequestData)

    class SpeechManager : MediaPlayer.OnErrorListener,
            MediaPlayer.OnCompletionListener {
        private val speechQueue: ArrayBlockingQueue<AudioData> = ArrayBlockingQueue(100)
        private var isStopped = true
        private val player: MediaPlayer = MediaPlayer()
        private val cyclicBarrier = CyclicBarrier(2)
        private var callback: SpeechCallback? = null
        private lateinit var lastAudioData: AudioData
        private val logger: Logger = Logger.getLogger("SpeechManager")

        init {
            player.setOnErrorListener(this)
            player.setOnCompletionListener(this)
        }

        fun addSpeechTask(audioData: AudioData) {
            speechQueue.add(audioData)
        }

        fun startSpeech() {
            //todo,可能会有线程问题
            Thread {
                while (!isStopped) {
                    val audioData = speechQueue.take()
                    logger.info("start speech.")
                    player.setDataSource(FileInputStream(audioData.audioUrl).fd)
                    lastAudioData = audioData
                    player.prepare()
                    if (!isStopped) {
                        player.start()
                        callback?.startSpeech(audioData.requestData)
                        logger.info("start real speech.")
                        cyclicBarrier.await()
                    }
                }
            }.start()
            isStopped = false
        }

        fun stopSpeech() {
            isStopped = true
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
        }

        interface SpeechCallback {
            fun singleSpeechComplete(requestData: RequestData)
            fun startSpeech(requestData: RequestData)
            fun speechError(mp: MediaPlayer, what: Int, extra: Int): Boolean
        }

        override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
            return callback?.speechError(mp, what, extra) ?: true
        }

        override fun onCompletion(mp: MediaPlayer) {
            player.reset()
            cyclicBarrier.await()
            logger.info("speech complete.")
            callback?.singleSpeechComplete(lastAudioData.requestData)
        }

        fun setCallback(callback: SpeechCallback) {
            this.callback = callback
        }
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