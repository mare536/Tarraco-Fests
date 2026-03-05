plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

val verifyNoMojibake by tasks.registering {
    group = "verification"
    description = "Fails build if suspicious mojibake text is found in XML resources."

    doLast {
        val suspiciousPattern = Regex("(Ã.|Â.|â€|ã.|ç®|Ã£|Ã§|�|[\\u0080-\\u009F])")
        val offenders = mutableListOf<String>()

        fileTree("src/main/res") {
            include("**/*.xml")
        }.files
            .sortedBy { it.path }
            .forEach { file ->
                file.readLines(Charsets.UTF_8).forEachIndexed { index, line ->
                    if (suspiciousPattern.containsMatchIn(line)) {
                        offenders.add("${file.path.replace('\\', '/')}:${index + 1}")
                    }
                }
            }

        if (offenders.isNotEmpty()) {
            val preview = offenders.take(20).joinToString("\n - ", prefix = " - ")
            val extra = if (offenders.size > 20) "\n... and ${offenders.size - 20} more." else ""
            throw GradleException(
                "Detected suspicious mojibake in resources:\n$preview$extra"
            )
        }
    }
}

android {
    namespace = "com.example.tarraco_fest"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.tarraco_fest"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("long", "EVENTS_API_CACHE_MS", "300000L")

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
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.named("preBuild") {
    dependsOn(verifyNoMojibake)
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation ("com.google.firebase:firebase-auth")
    implementation ("com.google.firebase:firebase-firestore")
    implementation ("com.google.firebase:firebase-messaging")
    implementation ("com.google.firebase:firebase-storage")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation("org.jsoup:jsoup:1.18.1")
}
