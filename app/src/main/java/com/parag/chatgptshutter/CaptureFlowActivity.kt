package com.parag.chatgptshutter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.parag.chatgptshutter.api.ApiClient
import com.parag.chatgptshutter.ui.theme.ChatGPTShutterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

enum class AppState {
    READY,
    CAPTURING,
    UPLOADING,
    REVIEW,
    ERROR
}

class CaptureFlowActivity : ComponentActivity() {

    // Emits an event every time the shutter button is pressed
    private val shutterEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle the intent that launched the activity
        if (intent.getBooleanExtra("AUTO_CAPTURE", false)) {
            shutterEvents.tryEmit(Unit)
        }

        setContent {
            ChatGPTShutterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CaptureScreen(shutterEvents = shutterEvents)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle subsequent Bluetooth clicks routed to this singleTop activity
        if (intent.getBooleanExtra("AUTO_CAPTURE", false)) {
            Log.d("CaptureFlow", "Received AUTO_CAPTURE from onNewIntent")
            shutterEvents.tryEmit(Unit)
        }
    }
}

@Composable
fun CaptureScreen(shutterEvents: SharedFlow<Unit>) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    var permissionGranted by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        ) 
    }
    
    var state by remember { mutableStateOf(AppState.READY) }
    var photoNumber by remember { mutableIntStateOf(1) }
    
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var aiResponse by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var isCameraBound by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionGranted = isGranted
    }
    
    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Shutter Event State Machine handler
    LaunchedEffect(isCameraBound) {
        shutterEvents.collect {
            when (state) {
                AppState.READY -> {
                    if (isCameraBound) {
                        state = AppState.CAPTURING
                        takePhoto(context, imageCapture) { file, uri ->
                            capturedFile = file
                            capturedImageUri = uri
                            state = AppState.UPLOADING
                        }
                    } else {
                        Toast.makeText(context, "Camera not ready yet.", Toast.LENGTH_SHORT).show()
                    }
                }
                AppState.REVIEW -> {
                    // Manual confirm via Bluetooth remote
                    capturedFile = null
                    capturedImageUri = null
                    aiResponse = null
                    photoNumber++
                    state = AppState.READY
                }
                AppState.CAPTURING, AppState.UPLOADING, AppState.ERROR -> {
                    Log.d("CaptureFlow", "Shutter click ignored in state: $state")
                    // Do nothing - ignore redundant clicks to prevent duplicate captures/uploads
                }
            }
        }
    }

    // API upload side-effect
    LaunchedEffect(state) {
        if (state == AppState.UPLOADING) {
            
            if (ApiClient.apiService == null) {
                errorMessage = "Backend URL is not configured. Please open ApiClient.kt and set BACKEND_URL to your secure server address."
                state = AppState.ERROR
                return@LaunchedEffect
            }
            
            val file = capturedFile
            if (file == null) {
                errorMessage = "Image file is missing."
                state = AppState.ERROR
                return@LaunchedEffect
            }

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("image", file.name, requestFile)
                    val promptPart = "Please analyze this image and provide a brief description.".toRequestBody("text/plain".toMediaTypeOrNull())

                    val response = ApiClient.apiService!!.analyzePhoto(body, promptPart)
                    
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful && response.body() != null) {
                            val aiRes = response.body()!!
                            if (aiRes.error != null) {
                                errorMessage = aiRes.error
                                state = AppState.ERROR
                            } else {
                                aiResponse = aiRes.response ?: "Received empty response from AI."
                                state = AppState.REVIEW
                            }
                        } else {
                            errorMessage = "HTTP Error: ${response.code()} - ${response.message()}"
                            state = AppState.ERROR
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CaptureFlow", "API Upload failed", e)
                    withContext(Dispatchers.Main) {
                        errorMessage = "Network Error: ${e.message ?: "Unknown error"}"
                        state = AppState.ERROR
                    }
                }
            }
        }
    }

    if (!permissionGranted) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Camera permission is required to take photos.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant Permission")
            }
        }
        return
    }

    // UI Rendering based on State
    Box(modifier = Modifier.fillMaxSize()) {
        
        // Always render the camera in the background if READY or CAPTURING
        if (state == AppState.READY || state == AppState.CAPTURING) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner, 
                                cameraSelector, 
                                preview, 
                                imageCapture
                            )
                            isCameraBound = true
                        } catch(exc: Exception) {
                            Log.e("CameraX", "Use case binding failed", exc)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    
                    previewView
                }
            )
            
            // Overlays for Camera mode
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Photo #$photoNumber", color = Color.White, fontWeight = FontWeight.Bold)
                if (state == AppState.READY) {
                    Text("Press Bluetooth button to capture", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
            
            if (state == AppState.CAPTURING) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                // Manual capture fallback
                Button(
                    onClick = {
                        if (isCameraBound) {
                            state = AppState.CAPTURING
                            takePhoto(context, imageCapture) { file, uri ->
                                capturedFile = file
                                capturedImageUri = uri
                                state = AppState.UPLOADING
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
                ) {
                    Text("Manual Capture")
                }
            }
        } else {
            // UPLOADING, REVIEW, or ERROR State UI (shows captured image + text)
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                val bitmap = remember(capturedFile) { 
                    capturedFile?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
                }
                
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Captured Photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.4f)
                    )
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Photo #$photoNumber",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    when (state) {
                        AppState.UPLOADING -> {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Uploading & Waiting for AI response...")
                        }
                        AppState.REVIEW -> {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = aiResponse ?: "",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                "Press Bluetooth button or click Next",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Button(
                                onClick = {
                                    capturedFile = null
                                    capturedImageUri = null
                                    aiResponse = null
                                    photoNumber++
                                    state = AppState.READY
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Next Photo")
                            }
                        }
                        AppState.ERROR -> {
                            Text(
                                text = "Error: $errorMessage",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(onClick = {
                                    // Reset state and discard photo
                                    capturedFile = null
                                    capturedImageUri = null
                                    state = AppState.READY
                                }) {
                                    Text("Cancel")
                                }
                                Button(onClick = {
                                    // Retry upload with existing photo
                                    state = AppState.UPLOADING
                                }) {
                                    Text("Retry")
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

private fun takePhoto(
    context: android.content.Context, 
    imageCapture: ImageCapture, 
    onImageCaptured: (File, Uri) -> Unit
) {
    val imagesDir = File(context.filesDir, "images")
    if (!imagesDir.exists()) {
        imagesDir.mkdirs()
    }
    
    val photoFile = File(imagesDir, "capture_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions, 
        ContextCompat.getMainExecutor(context), 
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraX", "Photo capture failed: ${exc.message}", exc)
                Toast.makeText(context, "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    photoFile
                )
                onImageCaptured(photoFile, uri)
            }
        }
    )
}
