import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.AssertionError

val tempDirectory = Files.createTempDirectory("gradle-profiler-wrapper-test").toFile().also { it.mkdirs() }
val gradleHome = File(tempDirectory, "gradle-user-home").also { it.mkdirs() }
val outputFile = File("output.txt")

Files.copy(File("./gradle-profilerw.kts").toPath(), File(tempDirectory, "gradle-profilerw.kts").toPath())

fun run(command: String): String {
    ProcessBuilder()
            .directory(tempDirectory)
            .redirectOutput(outputFile)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .also { it.environment().put("GRADLE_USER_HOME", gradleHome.absolutePath) }
            .command("kscript", "gradle-profilerw.kts", command)
            .start()
            .waitFor()

    return outputFile.readText().also { outputFile.writeText("") }
}

val helpOutput = run("--help")
if (!helpOutput.contains("Updating or installing gradle profiler") && !helpOutput.contains("help is not a recognized option")) {
    throw AssertionError("Error running for the first time \n $helpOutput")
}

val versionOutput = run("--version")
if (!versionOutput.contains("gradle-profilerw version is 1.1")) {
    throw AssertionError("Error running --version \n $versionOutput")
}

val v = run("-v")
if (!v.contains("gradle-profilerw version is 1.1")) {
    throw AssertionError("Error running --version \n $v")
}

val forceUpdate = run("--force-update")
if (!forceUpdate.contains("Updating or installing gradle profiler") && !forceUpdate.contains("force-update is not a recognized option")) {
    throw AssertionError("Error running --force-update \n $forceUpdate")
}

val helpOutputAgain = run("--help")
if (helpOutputAgain.contains("Updating or installing gradle profiler") && !helpOutputAgain.contains("help is not a recognized option")) {
    throw AssertionError("Error running again \n $helpOutputAgain")
}

File(gradleHome, "gradle-profiler/sha.txt").writeText("asdf")
val helpOutputAgainAgain = run("--help")
if (!helpOutputAgainAgain.contains("Updating or installing gradle profiler") && !helpOutputAgainAgain.contains("help is not a recognized option")) {
    throw AssertionError("Error running when sha is out of date \n $helpOutputAgainAgain")
}

File(gradleHome, "gradle-profiler/lastUpdated.txt").writeText("10")
val helpOutput3 = run("--help")
if (!helpOutput3.contains("Updating or installing gradle profiler") && !helpOutput3.contains("help is not a recognized option")) {
    throw AssertionError("Error running when lastUpdatedTime is over a day \n $helpOutput3")
}

outputFile.delete()
