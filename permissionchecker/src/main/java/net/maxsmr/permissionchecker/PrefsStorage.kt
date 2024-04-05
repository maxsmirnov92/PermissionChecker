package net.maxsmr.permissionchecker

interface PrefsStorage {

    val allKeys: Set<String>

    fun containsKey(key: String): Boolean

    fun removeKeys(keys: Collection<String>)

    fun putBoolean(keys: Map<String, Boolean>)
}