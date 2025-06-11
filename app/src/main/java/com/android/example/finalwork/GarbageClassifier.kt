package com.android.example.finalwork

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.PriorityQueue

class GarbageClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var modelInputWidth: Int = 224  // 默认值
    private var modelInputHeight: Int = 224 // 默认值
    private var modelInputChannel: Int = 3
    private var classLabels: List<String> = emptyList()
    private var isModelLoaded: Boolean = false

    companion object {
        private const val TAG = "GarbageClassifier"
        private const val MODEL_NAME = "garbage_classification_model_quantized.tflite"
        private const val LABEL_FILE = "class_names.txt"
        private const val MAX_RESULTS = 3
        private const val BATCH_SIZE = 1
        private const val PIXEL_SIZE = 3
        private const val THRESHOLD = 0.1f
        
        // 默认类别，用于在加载标签失败时使用
        private val DEFAULT_LABELS = listOf(
            "Harmful", "Kitchen", "Other", "Recyclable"
        )
    }

    init {
        try {
            Log.d(TAG, "开始初始化分类器")
            loadModel()
            loadLabels()
            isModelLoaded = true
            Log.d(TAG, "分类器初始化成功")
        } catch (e: IOException) {
            Log.e(TAG, "初始化分类器失败: ${e.message}", e)
            e.printStackTrace()
            // 使用默认标签
            classLabels = DEFAULT_LABELS
        } catch (e: Exception) {
            Log.e(TAG, "发生未知错误: ${e.message}", e)
            e.printStackTrace()
            // 使用默认标签
            classLabels = DEFAULT_LABELS
        }
    }

    @Throws(IOException::class)
    private fun loadModel() {
        try {
            Log.d(TAG, "开始加载模型: $MODEL_NAME")
            val assetFileDescriptor = context.assets.openFd(MODEL_NAME)
            val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            Log.d(TAG, "模型文件大小: ${declaredLength / 1024} KB")
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            
            // 创建Interpreter
            val options = Interpreter.Options().apply {
                setNumThreads(4) // 使用多线程
                setUseNNAPI(false) // 暂时禁用神经网络API加速，可能导致兼容性问题
            }
            
            interpreter = Interpreter(modelBuffer, options)
            
            // 获取模型输入尺寸
            val inputShape = interpreter?.getInputTensor(0)?.shape()
            if (inputShape != null && inputShape.size >= 3) {
                modelInputHeight = inputShape[1]
                modelInputWidth = inputShape[2]
                Log.d(TAG, "模型输入尺寸: ${modelInputWidth}x${modelInputHeight}")
            } else {
                Log.w(TAG, "无法获取模型输入尺寸，使用默认值")
            }
        } catch (e: IOException) {
            Log.e(TAG, "加载模型失败", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "加载模型时发生未知错误", e)
            throw IOException("加载模型失败", e)
        }
    }

    @Throws(IOException::class)
    private fun loadLabels() {
        try {
            context.assets.open(LABEL_FILE).bufferedReader().useLines { lines ->
                classLabels = lines.toList()
            }
            
            if (classLabels.isEmpty()) {
                Log.w(TAG, "标签文件为空，使用默认标签")
                classLabels = DEFAULT_LABELS
            } else {
                Log.d(TAG, "已加载 ${classLabels.size} 个标签")
            }
        } catch (e: IOException) {
            Log.e(TAG, "加载标签失败", e)
            classLabels = DEFAULT_LABELS
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "加载标签时发生未知错误", e)
            classLabels = DEFAULT_LABELS
            throw IOException("加载标签失败", e)
        }
    }

    fun recognizeImage(bitmap: Bitmap): List<ClassificationResult> {
        if (!isModelLoaded || interpreter == null) {
            Log.e(TAG, "模型未加载，无法识别图像")
            return listOf(ClassificationResult("错误：模型未加载", 1.0f))
        }
        
        try {
            // 调整图像尺寸以适应模型输入
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, modelInputWidth, modelInputHeight, true)
            
            // 创建输入数据
            val byteBuffer = convertBitmapToByteBuffer(resizedBitmap)
            
            // 创建输出数据
            val outputBuffer = Array(1) { FloatArray(classLabels.size) }
            
            // 运行推理
            if (interpreter == null) {
                Log.e(TAG, "解释器为空，无法进行推理")
                throw RuntimeException("解释器为空")
            }
            interpreter!!.run(byteBuffer, outputBuffer)
            
            // 处理结果
            return getTopKProbability(outputBuffer[0])
        } catch (e: Exception) {
            Log.e(TAG, "识别图像时发生错误", e)
            return listOf(ClassificationResult("错误：识别失败", 1.0f))
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        try {
            val byteBuffer = ByteBuffer.allocateDirect(
                BATCH_SIZE * modelInputWidth * modelInputHeight * PIXEL_SIZE * 4
            ).apply {
                order(ByteOrder.nativeOrder())
            }

            val pixels = IntArray(modelInputWidth * modelInputHeight)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            for (pixel in pixels) {
                val r = (pixel shr 16 and 0xFF) / 255.0f
                val g = (pixel shr 8 and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                byteBuffer.putFloat(r)
                byteBuffer.putFloat(g)
                byteBuffer.putFloat(b)
            }
            
            byteBuffer.rewind() // 重置缓冲区位置
            return byteBuffer
        } catch (e: Exception) {
            Log.e(TAG, "转换Bitmap到ByteBuffer时发生错误", e)
            
            // 创建一个空的ByteBuffer作为后备
            val fallbackBuffer = ByteBuffer.allocateDirect(
                BATCH_SIZE * modelInputWidth * modelInputHeight * PIXEL_SIZE * 4
            ).apply {
                order(ByteOrder.nativeOrder())
            }
            
            // 用零填充
            for (i in 0 until BATCH_SIZE * modelInputWidth * modelInputHeight * PIXEL_SIZE) {
                fallbackBuffer.putFloat(0.0f)
            }
            
            fallbackBuffer.rewind()
            return fallbackBuffer
        }
    }

    private fun getTopKProbability(labelProbArray: FloatArray): List<ClassificationResult> {
        try {
            // 处理空标签数组
            if (labelProbArray.isEmpty()) {
                Log.e(TAG, "概率数组为空")
                return listOf(ClassificationResult("错误：无结果", 1.0f))
            }
            
            // 确保概率数组和标签列表长度匹配
            val validSize = minOf(labelProbArray.size, classLabels.size)
            
            val pq = PriorityQueue<ClassificationResult>(MAX_RESULTS) { o1, o2 ->
                o1.confidence.compareTo(o2.confidence)
            }

            for (i in 0 until validSize) {
                val confidence = labelProbArray[i]
                if (confidence >= THRESHOLD) {
                    pq.add(ClassificationResult(classLabels[i], confidence))
                    
                    // 保持队列大小不超过MAX_RESULTS
                    if (pq.size > MAX_RESULTS) {
                        pq.poll()
                    }
                }
            }
            
            // 如果没有结果超过阈值，返回最高的一个
            if (pq.isEmpty() && validSize > 0) {
                var maxIdx = 0
                var maxProb = labelProbArray[0]
                
                for (i in 1 until validSize) {
                    if (labelProbArray[i] > maxProb) {
                        maxProb = labelProbArray[i]
                        maxIdx = i
                    }
                }
                
                return listOf(ClassificationResult(classLabels[maxIdx], maxProb))
            }

            val results = mutableListOf<ClassificationResult>()
            while (!pq.isEmpty()) {
                results.add(0, pq.poll())
            }
            
            return results
        } catch (e: Exception) {
            Log.e(TAG, "获取概率时发生错误", e)
            return listOf(ClassificationResult("错误：处理结果失败", 1.0f))
        }
    }

    fun close() {
        try {
            interpreter?.close()
        } catch (e: Exception) {
            Log.e(TAG, "关闭解释器时发生错误", e)
        } finally {
            interpreter = null
            isModelLoaded = false
        }
    }

    data class ClassificationResult(val label: String, val confidence: Float)
} 