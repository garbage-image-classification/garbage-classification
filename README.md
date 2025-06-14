# 垃圾分类识别应用

这是一个基于TensorFlow Lite的Android垃圾分类应用，可以识别4种不同类型的垃圾：harmful,recycle,kitchen,other。

## 项目结构

- `garbage_classification_model.ipynb`: 用于训练垃圾分类模型的Jupyter Notebook
- `app/`: Android应用源代码
  - `src/main/java/com/android/example/finalwork/GarbageClassifier.kt`: TensorFlow Lite模型加载和推理类
  - `src/main/java/com/android/example/finalwork/MainActivity.kt`: 应用主界面和相机功能

## 功能特点

- 支持实时拍照识别垃圾类型
- 支持从相册选择图片进行识别
- 显示多个可能的垃圾类别及其置信度
- 增强的错误处理机制，防止应用崩溃



## 数据集

本项目使用的是Garbage classification数据集，包含4个类别：harmful,recycle,kitchen,other。

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
11111111111
