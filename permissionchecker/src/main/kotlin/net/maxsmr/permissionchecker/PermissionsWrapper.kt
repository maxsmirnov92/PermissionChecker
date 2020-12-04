package net.maxsmr.permissionchecker

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
import androidx.fragment.app.Fragment
import pub.devrel.easypermissions.EasyPermissions

/**
 * Дополнительный запрос на запись (через intent), начиная с 30-ого апи,
 * если в разрешение на запись считается получанным и manageAllFilesIfR = true
 *
 * Также исключение [WRITE_EXTERNAL_STORAGE] из списка запрашиваемых при необходимости
 *
 * @return объект, в который надо отчитаться о результате (опционально; если manageAllFilesIfR = true)
 * или null, если дальнейшая обработка не требуется - целевое действие выполнено
 */
@JvmOverloads
fun checkAndRequestPermissionsWriteStorage(
        obj: Any,
        rationale: String,
        requestCode: Int,
        perms: List<String>,
        manageAllFilesIfR: Boolean = false,
        rationaleAction: (RationaleActionParams) -> Unit,
        targetAction: (() -> Unit)? = null,
): PermissionHandle? {
    val handle = PermissionHandleImpl(obj, rationale, requestCode, perms, manageAllFilesIfR, rationaleAction, targetAction)
    return if (handle.checkAndRequestPermissionsWriteStorage()) {
        null
    } else {
        handle
    }
}

/**
 * @return true, если версия, на которой выполняется < 30
 * или для >= 30 (MANAGE_EXTERNAL_STORAGE прописан в манифесте)
 */
private fun isExternalStorageManager() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

/**
 * Вызвать onRequestPermissionsResult или onAfterPermissionGranted
 * в зав-ти от реализации в целевом фрагменте/активити;
 * Все методы возвращают true при выполнении целевого действия
 */
interface PermissionHandle {

    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean

    fun onAfterPermissionGranted(): Boolean
}

private class PermissionHandleImpl(
        val callObject: Any,
        val rationale: String,
        val requestCode: Int,
        val perms: List<String>,
        val manageAllFilesIfR: Boolean = false,
        val rationaleAction: (RationaleActionParams) -> Unit,
        val targetAction: (() -> Unit)?
) : PermissionHandle {

    override fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (this.requestCode == requestCode && resultCode == Activity.RESULT_OK) {
            return checkAndRequestPermissionsWriteStorage()
        }
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, callObject)
        if (requestCode == this.requestCode) {
            return onAfterPermissionGranted()
        }
        return false
    }

    override fun onAfterPermissionGranted(): Boolean =
            checkAndRequestPermissionsWriteStorage()

    @TargetApi(Build.VERSION_CODES.R)
    fun startManageAllFilesActivity() {
        with(Intent(ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) {
            if (callObject is Fragment) {
                callObject.startActivityForResult(this, requestCode)
            } else {
                callObject.asActivityOrThrow().startActivityForResult(this, requestCode)
            }
        }
    }

    fun checkAndRequestPermissionsWriteStorage(): Boolean {
        var result = false
        val filteredPermissions: List<String>
        // TODO проблема не решена на == 26-ом - hasPermissions на запись всегда false, а без него не работает (EasyPermissions также не юзаем, кидаем в настройки)
        val hasStoragePermissions =
        // для target >= 29: флаг requestLegacyExternalStorage="true" -> WRITE_EXTERNAL_STORAGE учитывается,
                // false - означает необходимость использования ScopedStorage и отсутствие необходимости запроса этого разрешения (has на write будет всегда true)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Environment.isExternalStorageLegacy()) { // isExternalStorageLegacy возвращает на 29-ом true даже если в манифесте флаг отсутствует
                    val storagePermissions = mutableListOf(WRITE_EXTERNAL_STORAGE)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        storagePermissions.add(READ_EXTERNAL_STORAGE)
                    }
                    filteredPermissions = perms.filter {
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                                || it != READ_EXTERNAL_STORAGE // для версий >= Q даже с legacy=true has будет возвращать false на это, отфильтровываем
                    }
                    EasyPermissions.hasPermissions(callObject.asContextOrThrow(), *storagePermissions.toTypedArray())
                } else {
                    filteredPermissions = perms.filter {
                        var accepted = it != WRITE_EXTERNAL_STORAGE
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            accepted = accepted and (it != READ_EXTERNAL_STORAGE) // для версий >= Q даже с legacy=true has будет возвращать false на это, отфильтровываем
                        }
                        return accepted
                    }
                    true
                }

        if (manageAllFilesIfR && hasStoragePermissions && !isExternalStorageManager()) { // обычный write для >= 30 здесь есть, но не является external manager'ом
            startManageAllFilesActivity()
        } else {
            result = checkAndRequestPermissions(filteredPermissions)
        }
        return result
    }

    fun checkAndRequestPermissions(perms: List<String>): Boolean = checkAndRequestPermissions(
            callObject,
            rationale,
            requestCode,
            perms,
            rationaleAction,
            targetAction,
    )
}