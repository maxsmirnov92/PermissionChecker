package net.maxsmr.android.build.tasks

import org.gradle.api.tasks.TaskAction

open class CheckSignedArchiveTask : BaseSignArchiveTask() {


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
                        archivePath,
                        keystoreAlias
                )
        )
    }
}