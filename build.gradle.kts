plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}

tasks.register("printVersion") {
    val stamp = com.geno1024.aigc.ecaptureui.gradle.Versioning.stamp(rootDir, file("app"))
    doLast { println(stamp.versionName) }
}
