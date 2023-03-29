package com.example.lensetranslator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.lensetranslator.ui.theme.LenseTranslatorTheme
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.regex.Pattern
import kotlin.Exception

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestCameraPermission(this)
        setContent {
            LenseTranslatorTheme {
                Surface(color = MaterialTheme.colors.background) {
                    TextRecognizer()
                }
            }
        }
    }
    fun requestCameraPermission(context: Context){
        if(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, Array<String>(1){Manifest.permission.CAMERA}, 100)
        }
    }
}

@Composable
fun TextRecognizer(){
    val extractedText = remember {
        mutableStateOf("")
    }
    val translatedText = remember {
        mutableStateOf(mutableListOf<String>())
    }
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        CameraPreview(extractedText)
        Button(onClick = {
            translatedText.value = translateRequest(transformToJson(textToSentences(extractedText.value)))
        },
        modifier = Modifier
            .fillMaxWidth(0.3f)
            .fillMaxHeight(0.2f),
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
            colors = ButtonDefaults.buttonColors(MaterialTheme.colors.background)
            ) {
            Text("Перевести")
        }
        ScrollBoxes(translatedText.value)
    }
}

@Composable
fun CameraPreview(extractedText: MutableState<String>){
    val lifeCycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember {
        ProcessCameraProvider.getInstance(context)
    }
    val cameraExecutor = remember {
        Executors.newSingleThreadExecutor()
    }
    val textRecognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    val previewView = remember {
        PreviewView(context).apply { id = R.id.previewView }
    }
    AndroidView(factory = {ctx -> previewView},
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f)
    ) {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, ObjectDetectorImageAnalyzer(textRecognizer, extractedText)) }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifeCycleOwner, cameraSelector, preview, imageAnalyzer)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
fun translateRequest(toTranslate: JSONArray): MutableList<String> {

    val jsonText = toTranslate.toString()
    Log.d("HTTP", jsonText)
    val executor = Executors.newSingleThreadExecutor()
    val future = executor.submit(Callable {
        val url =
            URL("https://microsoft-translator-text.p.rapidapi.com/translate?to=ru&api-version=3.0&profanityAction=NoAction&textType=plain")
        val httpURLConnection = url.openConnection() as HttpURLConnection
        httpURLConnection.requestMethod = "POST"
        httpURLConnection.setRequestProperty("Content-Type", "application/json")
        httpURLConnection.setRequestProperty(
            "X-RapidAPI-Key",
            "1852c42de6mshda6d2726e353f17p196209jsn1b69d6c6b58a"
        )
        httpURLConnection.setRequestProperty(
            "X-RapidAPI-Host",
            "microsoft-translator-text.p.rapidapi.com"
        )

        httpURLConnection.doInput = true
        httpURLConnection.doOutput = true
        val outputStreamWriter = OutputStreamWriter(httpURLConnection.outputStream)
        outputStreamWriter.write(jsonText)
        outputStreamWriter.flush()
        val responseCode = httpURLConnection.responseCode
        Log.d("HTTP", httpURLConnection.requestMethod + " " + httpURLConnection.responseMessage)
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = httpURLConnection.inputStream.bufferedReader()
                .use { it.readText() }
            val gson = GsonBuilder().setPrettyPrinting().create()
            Log.d("GSON", gson.toJson(JsonParser.parseString(response)))
            return@Callable gson.toJson(JsonParser.parseString(response))
        } else {
            Log.d("HTTP", responseCode.toString())
            return@Callable """[{"translations": [{"text": ""}]}]"""
        }
    })
    val translated = JSONArray(future.get().toString())
    executor.shutdown()
    val list = mutableListOf<String>()
    for(i in 0 until translated.length()){
        list.add(
            translated.getJSONObject(i)
                .getJSONArray("translations")
                .getJSONObject(0)
                .getString("text"))
    }
    return list
}

fun textToSentences(text: String): MutableList<String>{
    val edited = text
        .replace('\n', ' ')
        .replace('"', ' ')
        .replace('\r', ' ')
    val pattern = Pattern.compile("[.!?]")
    var sentences = pattern.split(text)
    val list = mutableListOf<String>()
    for (i in sentences.indices){
        list.add(sentences[i].replace('\n', ' ')+'.')
    }
    return list
}

fun retrofitTranslation(toTranslate: String): String {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://microsoft-translator-text.p.rapidapi.com/")
        .build()

    val service = retrofit.create(APIService::class.java)
    val json = JSONArray()
    val text = JSONObject().put("text", toTranslate
        .replace('\n', ' ')
        .replace('"', ' ')
        .replace('[', ' ')
        .replace(']', ' ')
        .replace('\r', ' ')
    )
    json.put(text)

    val jsonString = json.toString()

    val requestBody = jsonString.toRequestBody("application/json".toMediaTypeOrNull())

    NetworkSingleton.get()
        .create(APIService::class.java).getTranslation(
            "ru",
            "3.0",
            "NoAction",
            "plain",
            "1852c42de6mshda6d2726e353f17p196209jsn1b69d6c6b58a",
            "microsoft-translator-text.p.rapidapi.com",
            requestBody
        ).enqueue(object : retrofit2.Callback<ResponseBody>{
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                Log.d("HTTP", response.body().toString())
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.d("HTTP", t.message.toString())
            }
        })
    return toTranslate
}

fun transformToJson(list: MutableList<String>):JSONArray{
    val array = JSONArray()
    val max = 2
    if(list.size > max) {
        for(i in 0 until max){
            array.put(JSONObject().put("text", list[i]))
        }
    } else {
        for(i in 0 until list.size){
            array.put(JSONObject().put("text", list[i]))
        }
    }
    Log.d("JSON", array.toString())
    Log.d("JSON", array.length().toString())
    return array
}

@Composable
private fun ScrollBoxes(list: MutableList<String>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .shadow(
                4.dp,
                shape = RoundedCornerShape(topEnd = 12.dp, topStart = 12.dp),
                clip = true
            )
            .background(color = Color.White)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        list.forEach {
            Text(it, modifier = Modifier.padding(2.dp))
        }
        /*repeat(list.size) {
            Text("Item $it", modifier = Modifier.padding(2.dp))
        }*/
    }
}
