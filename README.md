# 垃圾分类识别应用

这是一个基于TensorFlow Lite的Android垃圾分类应用，可以识别6种不同类型的垃圾：纸板(cardboard)、玻璃(glass)、金属(metal)、纸张(paper)、塑料(plastic)和其他垃圾(trash)。

## 项目结构

- `garbage_classification_model.ipynb`: 用于训练垃圾分类模型的Jupyter Notebook
- `app/`: Android应用源代码
  - `src/main/java/com/android/example/finalwork/GarbageClassifier.kt`: TensorFlow Lite模型加载和推理类
  - `src/main/java/com/android/example/finalwork/MainActivity.kt`: 应用主界面和相机功能

## 功能特点

- 支持实时拍照识别垃圾类型
- 支持从相册选择图片进行识别
- 显示多个可能的垃圾类别及其置信度
- 适配小米14等新型号手机
- 增强的错误处理机制，防止应用崩溃

## 使用方法

### 1. 训练模型

1. 确保已安装必要的Python库：
   ```
   pip install tensorflow numpy matplotlib
   ```

2. 运行`garbage_classification_model.ipynb`笔记本，训练垃圾分类模型。

3. 训练完成后，将生成以下文件：
   - `garbage_classification_model.h5`: Keras模型文件
   - `garbage_classification_model.tflite`: TensorFlow Lite模型文件
   - `garbage_classification_model_quantized.tflite`: 量化后的TensorFlow Lite模型文件（用于Android应用）
   - `class_names.txt`: 类别名称文件

### 2. 配置Android应用

1. 将`garbage_classification_model_quantized.tflite`和`class_names.txt`文件复制到Android项目的`app/src/main/assets/`目录下。

2. 如果`assets`目录不存在，请创建它：
   ```
   mkdir -p app/src/main/assets
   ```

### 3. 运行Android应用

1. 在Android Studio中打开项目。

2. 构建并运行应用。

3. 授予应用相机和存储权限。

4. 使用应用拍照或从相册选择图片进行垃圾类型识别。

## 数据集

本项目使用的是Garbage classification数据集，包含6个类别：
- cardboard (纸板)
- glass (玻璃)
- metal (金属)
- paper (纸张)
- plastic (塑料)
- trash (其他垃圾)

## 技术栈

- TensorFlow/Keras: 用于模型训练
- TensorFlow Lite: 用于Android设备上的模型推理
- CameraX: 用于相机功能
- Jetpack Compose: 用于UI构建
- Android Photo Picker API: 用于从相册选择图片

## 注意事项

- 模型精度取决于训练数据和训练参数，可以通过调整模型结构和训练参数来提高准确率。
- 在低光照条件下，识别效果可能不佳，请尽量在光线充足的环境下使用。
- 量化模型可能会略微降低准确率，但能显著减小模型大小，提高在移动设备上的性能。
- 如果应用依然出现闪退，请确保已授予所有必要权限。
- 对于小米14等新型号手机，可能需要在设置中允许应用使用相册和相机权限。