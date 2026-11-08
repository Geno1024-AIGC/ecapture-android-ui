package com.geno1024.aigc.ecaptureui.gradle

import org.gradle.api.Project
import java.io.File

data class Stamp(
    val env: String,
    val pack: String,
    val sha1: String,
) {
    val versionName: String get() = "$BASE_VERSION.$env.$pack.$sha1"
    val versionCode: Int get() = (env.toIntOrNull() ?: 0).coerceAtLeast(1)

    companion object {
        const val BASE_VERSION = "0.1"
    }
}

object Versioning {
    private val NO_HEAD = "00000000"

    private val ANDROID_PACKAGING = setOf(
        "assembleDebug", "assembleRelease", "bundleDebug", "bundleRelease",
    )
    private val AGGREGATE = setOf("build", "assemble")

    fun stamp(root: File, moduleDir: File): Stamp = Stamp(
        env = env(root),
        pack = packOf(moduleDir),
        sha1 = sha1(root),
    )

    fun env(root: File): String =
        System.getenv("GITHUB_RUN_NUMBER")
            ?: git(root, "rev-list", "--count", "HEAD")
            ?: "0"

    fun sha1(root: File): String =
        git(root, "rev-parse", "--short=8", "HEAD") ?: NO_HEAD

    fun packFile(moduleDir: File) = File(moduleDir, "count.pack")

    private fun packOf(moduleDir: File): String =
        packFile(moduleDir).takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { "0" } ?: "0"

    fun bumpOnPackaging(project: Project, moduleDir: File) {
        val requested = project.gradle.startParameter.taskNames.map { it.substringAfterLast(':') }
        if (requested.any { ANDROID_PACKAGING.contains(it) || AGGREGATE.contains(it) }) {
            incrementPack(moduleDir)
        }
    }

    private fun incrementPack(moduleDir: File) {
        val file = packFile(moduleDir)
        val current = file.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: 0
        file.writeText("${current + 1}\n")
    }

    private fun git(root: File, vararg args: String): String? = try {
        val process = ProcessBuilder(listOf("git", "-C", root.absolutePath) + args)
            .redirectErrorStream(false)
            .start()
        val out = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0 && out.isNotEmpty()) out else null
    } catch (_: Exception) {
        null
    }
}
