val releaseAssetRoot = layout.buildDirectory.dir("generated/animadev-release-assets")
val prepareReleaseScripts by tasks.registering(Copy::class) {
    from(rootProject.file("runner/release_signer.py"))
    from(rootProject.file("runner/release_worker.py"))
    from(rootProject.file("runner/release_cancel.py"))
    from(rootProject.file("runner/ReleaseTransport.java"))
    into(releaseAssetRoot.map { it.dir("release") })
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.animacode.animadev"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.animacode.animadev"
        minSdk = 33
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 35
        versionName = "0.4.0-beta.8-dev.21"
    }
    sourceSets.getByName("main").assets.srcDir(releaseAssetRoot.get().asFile)
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.savedstate:savedstate:1.4.0")
    implementation("androidx.savedstate:savedstate-compose:1.4.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.matching { it.name == "mergeDebugAssets" || it.name == "mergeReleaseAssets" }
    .configureEach { dependsOn(prepareReleaseScripts) }

// Release Lint reads generated assets as model inputs; Gradle 9 requires
// an explicit dependency in addition to mergeReleaseAssets.
tasks.matching { it.name.contains("Release") && it.name.contains("lint", ignoreCase = true) }
    .configureEach { dependsOn(prepareReleaseScripts) }
