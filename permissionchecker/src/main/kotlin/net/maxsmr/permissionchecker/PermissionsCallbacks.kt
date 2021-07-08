package net.maxsmr.permissionchecker

class PermissionsCallbacks @JvmOverloads constructor(
        internal val onPermanentlyDeniedPermissions: ((DeniedPermissions) -> Unit)? = null,
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

    data class DeniedPermissions(
            val allDenied: Set<String>,
            val permanentlyDenied: Set<String>
    ) {

        val isEmpty get() = allDenied.isEmpty() && permanentlyDenied.isEmpty()
    }
}