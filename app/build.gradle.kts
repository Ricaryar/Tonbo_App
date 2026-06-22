plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

import java.util.Properties
import java.io.File

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun localProp(key: String, default: String): String {
    return localProperties.getProperty(key, default)
        .replace("\\\\", File.separator)
        .replace("\\:", ":")
}

fun resolveProjectPath(path: String): String {
    val file = File(path)
    return if (file.isAbsolute) path else rootProject.file(path).absolutePath
}

val bundleGgufPath: String = resolveProjectPath(
    localProp("tonbo.gguf.path", "d:/FYP_USE/qwen-tonbo-q8_0.gguf")
)
val bundleSherpaDir: String = resolveProjectPath(
    localProp(
        "tonbo.sherpa.dir",
        "training/output/sherpa_asr/sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en"
    )
)
val bundleGgufName = "qwen-tonbo-q8_0.gguf"
val bundleSherpaAssetsDir =
    "src/main/assets/sherpa_asr/sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en"

android {
    namespace = "com.example.tonbo_app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.tonbo_app"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }

    androidResources {
        noCompress += listOf("gguf", "onnx")
    }

    // --- 原生库：Sherpa 自带 libonnxruntime.so，勿与 Microsoft ORT 混用 ---
    packaging {
        jniLibs {
            pickFirsts.add("lib/**/libonnxruntime.so")
            pickFirsts.add("lib/**/libonnxruntime4j_jni.so")
        }
        resources {
            // 解決 common.properties 衝突
            pickFirsts.add("common.properties")
            pickFirsts.add("META-INF/common.properties")

            // 解決你最新的報錯：META-INF/DEPENDENCIES 衝突
            pickFirsts.add("META-INF/DEPENDENCIES")

            // 預防性排除其他常見的元數據衝突文件
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/ASL2.0")
        }
    }
}

dependencies {
    // Kotlin標準庫
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // CameraX dependencies for camera functionality (使用更穩定的版本)
    implementation("androidx.camera:camera-core:1.1.0")
    implementation("androidx.camera:camera-camera2:1.1.0")
    implementation("androidx.camera:camera-lifecycle:1.1.0")
    implementation("androidx.camera:camera-view:1.1.0")

    // TensorFlow Lite for YOLO model inference
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")

    // ONNX Runtime：貨幣分類模型推理；與 Sherpa 共用 libonnxruntime.so（見 packaging pickFirst）
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.0")

    // Google ML Kit for OCR text recognition
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")

    // Google ML Kit Object Detection
    // implementation("com.google.mlkit:object-detection:17.0.1")
    // implementation("com.google.mlkit:object-detection-custom:17.0.1")

    // Google Location Services (僅用於緊急求助時發送位置)
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // 高德 Android 地圖 SDK（導航頁 MapView + 路線）
    implementation("com.amap.api:navi-3dmap:10.0.800_3dmap10.0.800")

    // Agora RTC SDK for video calling
    implementation("io.agora.rtc:full-sdk:4.3.0")

    // OkHttp for HTTP requests
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // llama.cpp GGUF 推理（加载用户自训练的 qwen-tonbo-q8_0.gguf）
    implementation("com.github.whyisitworking:llama-bro:1.2.3")

    // Sherpa-ONNX 离线语音识别（粤语/普通话/英文流式）
    implementation("com.github.k2-fsa:sherpa-onnx:v1.12.29")

    // 阿里云OSS SDK for file upload
    implementation("com.aliyun.oss:aliyun-sdk-oss:3.17.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

val copyBundledLlm by tasks.registering(Copy::class) {
    val gguf = File(bundleGgufPath)
    onlyIf { gguf.exists() }
    from(gguf)
    into(layout.projectDirectory.dir("src/main/assets/llm"))
    rename { bundleGgufName }
    doFirst {
        if (!gguf.exists()) {
            throw GradleException(
                "离线 LLM 未找到: $bundleGgufPath\n" +
                    "请在 local.properties 设置 tonbo.gguf.path，或运行 training/prepare_apk_assets.ps1"
            )
        }
        logger.lifecycle("打包 LLM 进 APK: ${gguf.absolutePath} (${gguf.length() / (1024 * 1024)} MB)")
    }
}

val copyBundledSherpa by tasks.registering(Copy::class) {
    val sherpa = File(bundleSherpaDir)
    onlyIf { sherpa.isDirectory }
    from(sherpa) {
        include("encoder.int8.onnx", "decoder.int8.onnx", "tokens.txt")
    }
    into(layout.projectDirectory.dir(bundleSherpaAssetsDir))
    doFirst {
        val encoder = File(sherpa, "encoder.int8.onnx")
        if (!encoder.exists()) {
            throw GradleException(
                "Sherpa ASR 未找到: $bundleSherpaDir\n" +
                    "请运行 training/download_sherpa_asr_model.ps1，或设置 tonbo.sherpa.dir"
            )
        }
        logger.lifecycle("打包 Sherpa ASR 进 APK: $bundleSherpaDir")
    }
}

tasks.named("preBuild").configure {
    dependsOn(copyBundledLlm, copyBundledSherpa)
}