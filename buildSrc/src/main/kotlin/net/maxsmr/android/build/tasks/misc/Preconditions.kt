package net.maxsmr.android.build.tasks.misc

import java.io.File

fun checkNotEmpty(str: String?, argName: String) {
    require(!str.isNullOrEmpty()) { "Argument '$argName' is null or empty" }
}

fun checkFilePathValid(path: String?, fileDescription: String) {
    require(!path.isNullOrEmpty()) { "'$fileDescription' path is null or empty" }
    checkFileValid(File(path), fileDescription)
}

fun checkFileValid(file: File?, fileDescription: String) {
    require(!(file == null || !file.exists() || !file.isFile || file.length() <= 0)) { "'$fileDescription' file $file is not valid" }
}