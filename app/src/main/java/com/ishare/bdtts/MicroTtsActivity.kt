package com.ishare.bdtts

import android.app.Activity
import android.os.Bundle
import com.ishare.MoxiangApplication
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import java.io.StringWriter


class MicroTtsActivity : Activity() {

    lateinit var speechConfig: SpeechConfig
    lateinit var audioConfig: AudioConfig
    lateinit var synthesizer: SpeechSynthesizer
    lateinit var cfg: Configuration
    lateinit var fileCachePath: String
    lateinit var app: MoxiangApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_micro_tts)
        app = application as MoxiangApplication
        fileCachePath = getExternalFilesDir(null).absolutePath + "/file.wav"
        initEngine()
    }

    private fun initEngine() {
        speechConfig = SpeechConfig.fromSubscription("9644ad9e4a40402a83462228bfeca076", "eastus")
        audioConfig = AudioConfig.fromWavFileOutput(fileCachePath)
        synthesizer = SpeechSynthesizer(speechConfig, audioConfig)
        cfg = Configuration(Configuration.VERSION_2_3_24)
        cfg.setDirectoryForTemplateLoading(app.offlineDir)
        cfg.defaultEncoding = "UTF-8"
        cfg.templateExceptionHandler = TemplateExceptionHandler.DEBUG_HANDLER;
        val root = HashMap<String, Any>()
        val text = "测试 测试"
        root["text"] = text
        val temp = cfg.getTemplate("ssml.ftlx")
        val out = StringWriter()
        temp.process(root, out)
        val result = synthesizer.SpeakSsml(out.toString())
    }

}