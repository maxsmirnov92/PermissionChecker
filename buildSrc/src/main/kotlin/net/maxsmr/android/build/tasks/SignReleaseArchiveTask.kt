package net.maxsmr.android.build.tasks

import net.maxsmr.android.build.tasks.misc.checkNotEmpty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

open class SignReleaseArchiveTask : BaseSignArchiveTask() {

    @Input
    var archiveExtension: String = ""

    override fun checkArgs() {
        super.checkArgs()
        require(archiveExtension.length > 1) { "Argument 'archiveExtension' is null or empty" }
    }

    @TaskAction
    fun sign() {
        if (!archiveExtension.startsWith(".")) {
            archiveExtension = ".$archiveExtension"
        }
        checkArgs()

        var newArchivePath = ""
        val extIndex = archivePath.lastIndexOf(archiveExtension)
        if (extIndex != -1 && extIndex < archivePath.length - 1) {
            newArchivePath = archivePath.replaceRange(extIndex, archivePath.length, "-signed")
            newArchivePath += archiveExtension
        }
        checkNotEmpty(newArchivePath, "newArchivePath")

        runScript(
                listOf(
                        "jarsigner",
                        "-keystore",
                        getKeystoreFile().absolutePath,
                        "-storepass",
                        keystorePassword,
                        "-keypass",
                        keystorePassword,
                        "-signedjar",
                        newArchivePath,
                        "-verbose",
                        archivePath,
                        keystoreAlias
                )
        )
    }
}