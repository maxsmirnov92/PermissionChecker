package net.maxsmr.android.build.tasks

import net.maxsmr.android.build.tasks.misc.checkNotEmpty
import org.gradle.api.tasks.TaskAction

open class SignReleaseAarTask : BaseSignAarTask() {

    @TaskAction
    fun sign() {
        checkArgs()

        var newAarPath = ""
        val extIndex = aarPath.lastIndexOf(".aar")
        if (extIndex != -1 && extIndex < aarPath.length - 1) {
            newAarPath = aarPath.replaceRange(extIndex, aarPath.length, "-signed")
            newAarPath += ".aar"
        }

        checkNotEmpty(newAarPath, "New AAR path")

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
                        newAarPath,
                        "-verbose",
                        aarPath,
                        keystoreAlias
                )
        )
    }
}