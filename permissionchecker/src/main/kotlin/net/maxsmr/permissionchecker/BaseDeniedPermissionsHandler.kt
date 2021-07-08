package net.maxsmr.permissionchecker

/**
 * Используется для вывода пользовательского сообщения по отклонённым разрешениям
 */
abstract class BaseDeniedPermissionsHandler {

    /**
     * @param deniedPerms все отклонённые разрешения
     * @param permanentlyDeniedPerms отклонённые разрешения по "don't ask again" (включены в [deniedPerms])
     */
    fun showMessage(
            requestCode: Int,
            messageIfEmpty: String,
            deniedPerms: PermissionsCallbacks.DeniedPermissions,
            negativeAction: ((Set<String>) -> Unit)? = null,
    ) {
        val targetMessage = if (deniedPerms.isEmpty) {
            messageIfEmpty
        } else {
            formatDeniedPermissionsMessage(deniedPerms)
        }
        doShowMessage(requestCode, targetMessage, deniedPerms, negativeAction)
    }

    protected abstract fun doShowMessage(
            requestCode: Int,
            message: String,
            deniedPerms: PermissionsCallbacks.DeniedPermissions,
            negativeAction: ((Set<String>) -> Unit)?,
    )

    protected abstract fun formatDeniedPermissionsMessage(deniedPerms: PermissionsCallbacks.DeniedPermissions): String
}