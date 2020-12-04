package net.maxsmr.android.build.tasks

import org.gradle.api.tasks.TaskAction

open class CheckSignedAarTask : BaseSignAarTask() {


    @TaskAction
    fun check() {
        checkArgs()
        runScript(
                listOf(
                        "jarsigner",
                        "-keystore",
                        getKeystoreFile().absolutePath,
                        "-storepass",
                        keystorePassword,
                        "-verify",
                        "-verbose",
                        "-certs",
                        aarPath,
                        keystoreAlias
                )
        )
    }
}