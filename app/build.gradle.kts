plugins {
   alias(libs.plugins.android.application)
   alias(libs.plugins.kotlin.android)
}

android {
   namespace = "mx.motion.documentospersonalesai"
   compileSdk = 36

   defaultConfig {
      applicationId = "mx.motion.documentospersonalesai"
      minSdk = 34
      targetSdk = 36
      versionCode = 1
      versionName = "1.0"

      testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

      ndk {
         abiFilters.add("arm64-v8a")
      }
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
      sourceCompatibility = JavaVersion.VERSION_11
      targetCompatibility = JavaVersion.VERSION_11
   }
   kotlinOptions {
      jvmTarget = "11"
   }
   buildFeatures {
      viewBinding = true
   }
   // Configuración para que el modelo local AI no se comprima
   // Al agregar estas extensiones, Android las dejará "planas" en el disco
   // permitiendo que el motor de IA haga mmap (memory mapping) correctamente.
   androidResources {
        noCompress += listOf("onnx", "bin", "data", "onnx_data", "litertlm")
   }

   packaging {
      resources {
         // Esto evita que archivos comunes de IA se compriman
         excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
      jniLibs {
         // Esto alinea las librerías nativas para que sean compatibles con Android 16
         useLegacyPackaging = false
      }
   }
}

dependencies {
   implementation(libs.androidx.core.ktx)
   implementation(libs.androidx.appcompat)
   implementation(libs.material)
   implementation(libs.androidx.constraintlayout)
   implementation(libs.androidx.lifecycle.livedata.ktx)
   implementation(libs.androidx.lifecycle.viewmodel.ktx)
   implementation(libs.androidx.navigation.fragment.ktx)
   implementation(libs.androidx.navigation.ui.ktx)
   implementation(libs.litert.lm)
   testImplementation(libs.junit)
   androidTestImplementation(libs.androidx.junit)
   androidTestImplementation(libs.androidx.espresso.core)
}