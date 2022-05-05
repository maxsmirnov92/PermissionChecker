package net.maxsmr.android.build.tasks

import net.maxsmr.android.build.tasks.misc.checkFilePathValid
import net.maxsmr.android.build.tasks.misc.checkFileValid
import net.maxsmr.android.build.tasks.misc.checkNotEmpty
import net.maxsmr.android.build.tasks.misc.shell.ShellWrapper
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import java.io.File

abstract class BaseSignArchiveTask : DefaultTask() {

    @Input
    var keystoreAlias: String = ""

    @Input
    var keystorePassword: String = ""

    @Input
    var archivePath: String = ""

    @Input
    var keystoreFileName: String = "release.keystore"

    @Input
    var enableLogging: Boolean = false

    open fun checkArgs() {
        checkNotEmpty(keystoreAlias, "keystoreAlias")
        checkNotEmpty(keystorePassword, "keystorePassword")
        checkNotEmpty(keystoreFileName, "keystoreFileName")
        checkFilePathValid(archivePath, "archive")
    }

    // not using String to split because of spaces in paths
    protected fun runScript(commands: List<String>) {
        println("Executing script: $commands")
        with(ShellWrapper(enableLogging = enableLogging)) {
            var jarSignerPath = System.getenv("JARSIGNER_PATH") ?: ""
            if (jarSignerPath.isEmpty()) {
                var jreHome = System.getenv("JRE_HOME") ?: ""
                if (jreHome.isNotEmpty()) {
                    jreHome = jreHome.appendSeparator()
                    jreHome += "bin"
                    jarSignerPath = jreHome
                }
            } else {
                jarSignerPath = jarSignerPath.removeSeparator()
            }
            this.workingDir = jarSignerPath
            val mutableCommands = commands.toMutableList()
            if (commands.isNotEmpty() && !mutableCommands[0].contains(jarSignerPath)) {
                mutableCommands[0] = jarSignerPath + File.separator + mutableCommands[0]
            }
            println("commands: $mutableCommands")
            executeCommand(mutableCommands, false)
        }
    }

    @Internal
    protected fun getKeystoreFile(): File {
        var keystoreFile: File? = null
        val homeDir = System.getenv("ANDROID_HOME") ?: ""
        if (homeDir.isNotEmpty()) {
            keystoreFile = File(homeDir, keystoreFileName)
        }
        checkFileValid(keystoreFile, "Keystore")
        return keystoreFile!!
    }

    private fun String.appendSeparator(): String {
        if (!endsWith("/") && !endsWith("\\")) {
            return this + File.separator
        }
        return this
    }

    private fun String.removeSeparator(): String {
        if (endsWith("/") || endsWith("\\")) {
            return substring(0, length - 1)
        }
        return this
    }
}