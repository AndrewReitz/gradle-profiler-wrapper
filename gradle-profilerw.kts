@file:DependsOn("com.squareup.okhttp3:okhttp:3.14.1")
@file:DependsOn("com.squareup.okio:okio:2.2.2")
@file:DependsOn("com.squareup.retrofit2:retrofit:2.5.0")
@file:DependsOn("com.squareup.moshi:moshi:1.8.0")
@file:DependsOn("com.squareup.retrofit2:converter-moshi:2.5.0")

import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Okio.buffer
import okio.Okio.sink
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.io.File
import java.nio.file.Paths

fun quit() = System.exit(1)

val currentVersion = "1.2"

if (args.contains("--version") || args.contains("-v")) {
    println("gradle-profilerw version is $currentVersion")
    quit()
}

fun File.run(
        vararg args: String,
        errorOutput: ProcessBuilder.Redirect = ProcessBuilder.Redirect.INHERIT,
        standardOutput: ProcessBuilder.Redirect = ProcessBuilder.Redirect.to(File("/dev/null"))
) {
    ProcessBuilder()
            .directory(this)
            .command(*args)
            .redirectError(errorOutput)
            .redirectOutput(standardOutput)
            .start()
            .waitFor()
}

fun runProfiler() {
    val input = if (args.contains("--output-dir")) args else args.plus("--output-dir").plus(File(".").absolutePath)
    
    Paths.get("").toAbsolutePath().toFile().run(
            File(gradleProfilerSrc, "build/install/gradle-profiler/bin/gradle-profiler").absolutePath,
            *input,
            standardOutput = ProcessBuilder.Redirect.INHERIT
    )
}

val gradleUserHome: String = System.getenv("GRADLE_USER_HOME")
        ?: File(System.getProperty("user.home"), ".gradle").absolutePath
val gradleProfilerHome = File(gradleUserHome, "gradle-profiler").apply { mkdirs() }

val gradleProfilerSrcZip = File(gradleProfilerHome, "source.zip")
val gradleProfilerSrc = File(gradleProfilerHome, "gradle-profiler-master")

val lastUpdatedFile = File(gradleProfilerHome, "lastUpdated.txt")
val lastUpdated = lastUpdatedFile.takeIf { it.exists() }?.readText()?.toLong() ?: 0
val currentTime = System.currentTimeMillis()

val forceUpdate = args.contains("--force-update")

@Suppress("PropertyName")
val ONE_DAY_IN_MILLIS = 1000 * 60 * 60 * 24

if (currentTime < lastUpdated + ONE_DAY_IN_MILLIS && !forceUpdate) {
    lastUpdatedFile.writeText(currentTime.toString())
    runProfiler()
    quit()
}

data class MasterResponse(val `object`: Obj)
data class Obj(val sha: String)

interface GithubService {
    @get:GET("repos/gradle/gradle-profiler/git/refs/heads/master")
    val master: Call<MasterResponse>
}

val githubService: GithubService = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(GithubService::class.java)

val sha = githubService.master.execute().body()?.`object`?.sha
val storedShaFile = File(gradleProfilerHome, "sha.txt")
val storedSha = storedShaFile.takeIf { it.exists() }?.readText()

fun updateGradleProfiler() {
    println("Updating or installing gradle profiler")
    gradleProfilerSrcZip.delete()
    gradleProfilerSrc.deleteRecursively()

    sha?.let { storedShaFile.writeText(sha) }

    val client = OkHttpClient()
    val request = Request.Builder()
            .url("https://github.com/gradle/gradle-profiler/archive/master.zip")
            .build()

    val response = client.newCall(request).execute()

    val source = response.body()?.source()!!
    val sink = buffer(sink(gradleProfilerSrcZip))
    sink.writeAll(source)
    sink.flush()

    gradleProfilerHome.run("unzip", gradleProfilerSrcZip.absolutePath)
    gradleProfilerSrc.run("./gradlew", "installDist")
}

if (forceUpdate) {
    updateGradleProfiler()
    quit()
}

if (sha != storedSha || !gradleProfilerSrcZip.exists()) {
    updateGradleProfiler()
}

runProfiler()
