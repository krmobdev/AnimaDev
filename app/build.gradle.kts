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

if (providers.gradleProperty("animadevDeviceFixture").orNull == "true") {
    layout.buildDirectory.set(layout.projectDirectory.dir("build/device-fixture"))
}
val supportAssets = layout.buildDirectory.dir("generated/animadev-support-assets")
val prepareSupportDocs by tasks.registering(Copy::class) {
    from(rootProject.file("CHANGELOG.md"))
    from(rootProject.file("docs/USER_GUIDE.md"))
    from(rootProject.file("docs/USER_GUIDE_EN.md"))
    from(rootProject.file("docs/TERMUX_SETUP.md"))
    from(rootProject.file("docs/TERMUX_SETUP_EN.md"))
    into(supportAssets.map {it.dir("support")})
}

android {
    namespace = "com.animacode.animadev"
    compileSdk = 37
    defaultConfig {
        applicationId = if (providers.gradleProperty("animadevDeviceFixture").orNull == "true")
            "com.animacode.animadev.devicefixture" else "com.animacode.animadev"
        minSdk = 33
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = if (providers.gradleProperty("animadevDeviceFixture").orNull == "true") 82 else 81
        versionName = "0.4.0-beta.10-dev.57"
    }
    sourceSets.getByName("main").assets.srcDir(releaseAssetRoot.get().asFile)
    sourceSets.getByName("main").assets.srcDir(supportAssets.get().asFile)
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
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
    // Espresso 3.7.0 replaces reflection into removed InputManager.getInstance.
    // Required for Compose instrumentation on current Pixel system images.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.matching { it.name == "mergeDebugAssets" || it.name == "mergeReleaseAssets" }
    .configureEach { dependsOn(prepareReleaseScripts) }

// Both Debug and Release Lint read generated assets as model inputs.
// Gradle 9 requires this dependency in addition to asset merging.
tasks.matching { it.name.contains("lint", ignoreCase = true) }
    .configureEach { dependsOn(prepareReleaseScripts, prepareSupportDocs) }

// In-app documentation and change log are copied from the same tracked GitHub files.
tasks.matching { it.name == "mergeDebugAssets" || it.name == "mergeReleaseAssets" }
    .configureEach { dependsOn(prepareSupportDocs) }
