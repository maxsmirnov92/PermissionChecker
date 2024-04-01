package net.maxsmr.permissionchecker

class PermissionsCallbacks @JvmOverloads constructor(
    internal val onPermanentlyDeniedPermissions: ((permissions: Set<String>) -> Unit)? = null,
    internal val onDenied: ((permissions: Set<String>) -> Unit)? = null,
    internal val onAllGranted: () -> Unit
) {

    fun onAfterPermissionResult(denied: Set<String>): Boolean {
        return if (denied.isEmpty()) {
            onAllGranted()
            true
        } else {
            onDenied?.invoke(denied)
            false
        }
    }
}