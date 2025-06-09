package com.android.example.finalwork

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.android.example.finalwork.ui.theme.FinalworkTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var garbageClassifier: GarbageClassifier
    private lateinit var cameraExecutor: ExecutorService
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            Log.d("MainActivity", "所有权限已授予")
        } else {
            Log.d("MainActivity", "部分权限被拒绝")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化分类器
        garbageClassifier = GarbageClassifier(this)
        
        // 初始化相机执行器
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // 请求必要权限
        requestCameraPermission()
        
        enableEdgeToEdge()
        setContent {
            FinalworkTheme {
                GarbageClassificationApp(garbageClassifier, cameraExecutor)
            }
        }
    }
    
    private fun requestCameraPermission() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!allGranted) {
            requestPermissionLauncher.launch(permissions)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        garbageClassifier.close()
        cameraExecutor.shutdown()
    }
}

@Composable
fun GarbageClassificationApp(classifier: GarbageClassifier, cameraExecutor: ExecutorService) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var classificationResults by remember { mutableStateOf<List<GarbageClassifier.ClassificationResult>>(emptyList()) }
    var isCameraMode by remember { mutableStateOf(true) }
    
    val imageCapture = remember { ImageCapture.Builder().build() }
    
    // 用于从相册选择图片的启动器
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            try {
                // 从URI获取Bitmap
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                capturedImage = bitmap
                
                // 进行分类
                classificationResults = classifier.recognizeImage(bitmap)
                
                // 切换到图像显示模式
                isCameraMode = false
            } catch (e: Exception) {
                Log.e("MainActivity", "从相册加载图片失败", e)
            }
        }
    }
    
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "垃圾分类识别",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            
            if (isCameraMode) {
                Text(
                    text = "请拍照或从相册选择图片进行垃圾分类识别",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                )
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (isCameraMode) {
                    // 相机预览
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            }
                            
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
                                } catch (e: Exception) {
                                    Log.e("CameraX", "绑定失败", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (capturedImage != null) {
                    // 显示捕获的图像
                    Image(
                        bitmap = capturedImage!!.asImageBitmap(),
                        contentDescription = "捕获的图像",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            // 分类结果显示
            if (!isCameraMode && classificationResults.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "识别结果",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        classificationResults.forEach { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = result.label,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "${(result.confidence * 100).roundToInt()}%",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // 进度条
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .background(Color.LightGray, RoundedCornerShape(4.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(result.confidence)
                                        .height(8.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
            
            // 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (isCameraMode) {
                    // 拍照按钮
                    Button(
                        onClick = {
                            try {
                                imageCapture.takePicture(
                                    cameraExecutor,
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            try {
                                                val bitmap = image.toBitmap()
                                                capturedImage = bitmap
                                                
                                                // 进行分类
                                                classificationResults = classifier.recognizeImage(bitmap)
                                                
                                                isCameraMode = false
                                            } catch (e: Exception) {
                                                Log.e("CameraX", "处理图像失败", e)
                                            } finally {
                                                image.close()
                                            }
                                        }
                                        
                                        override fun onError(exception: ImageCaptureException) {
                                            Log.e("CameraX", "拍照失败", exception)
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                Log.e("CameraX", "拍照时发生异常", e)
                            }
                        },
                        modifier = Modifier.size(width = 120.dp, height = 48.dp)
                    ) {
                        Text("拍照")
                    }
                    
                    // 从相册选择按钮
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            pickImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.size(width = 120.dp, height = 48.dp)
                    ) {
                        Text("相册")
                    }
                } else {
                    Button(
                        onClick = {
                            isCameraMode = true
                            capturedImage = null
                            classificationResults = emptyList()
                        },
                        modifier = Modifier.size(width = 120.dp, height = 48.dp)
                    ) {
                        Text("重新拍照")
                    }
                    
                    // 从相册选择按钮
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            pickImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.size(width = 120.dp, height = 48.dp)
                    ) {
                        Text("相册")
                    }
                }
            }
        }
    }
}

// 改进的ImageProxy转Bitmap方法，增加错误处理
fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.capacity())
    buffer.get(bytes)
    
    // 检查图像格式，如果不是NV21，尝试其他处理方式
    return if (format == ImageFormat.JPEG) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } else {
        // 使用YuvImage转换
        try {
            val yuvImage = android.graphics.YuvImage(
                bytes,
                ImageFormat.NV21,
                width,
                height,
                null
            )
            
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
            val imageBytes = out.toByteArray()
            
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e("ImageConversion", "YuvImage转换失败", e)
            
            // 后备方案：创建一个空的位图
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // 尝试使用其他方式填充位图数据
            try {
                val rgbaBytes = ByteArray(width * height * 4)
                // 这里可以添加YUV到RGBA的转换代码
                // 简单起见，我们先返回一个空位图
                bitmap
            } catch (e2: Exception) {
                Log.e("ImageConversion", "备用转换也失败", e2)
                bitmap
            }
        }
    }
}