package net.maxsmr.permissionchecker

import android.Manifest
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
import android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import pub.devrel.easypermissions.EasyPermissions


/**
 * Мапа <Имя разрешения, Признак того, что разрешение предоставлено>
 */
typealias PermissionResult = Map<String, Boolean>

class PermissionsHelper(private val permanentlyDeniedPrefs: SharedPreferences) {

    val lastPermissionsResult = MutableLiveData<PermissionResult>()

    val permanentlyDeniedPermissions: Set<String>
        get() = permanentlyDeniedPrefs.all.keys

    /**
     * @return true, если версия, на которой выполняется < 30
     * или для >= 30 (MANAGE_EXTERNAL_STORAGE прописан в манифесте)
     */
    val isExternalStorageManager get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    fun doOnPermissionsResult(
        activity: Activity,
        rationale: String,
        requestCode: Int,
        permissions: Collection<String>,
        permanentlyDeniedPermissionsHandler: BaseDeniedPermissionsHandler? = null,
        onDenied: ((Set<String>) -> Unit)? = null,
        onNegativePermanentlyDeniedAction: ((Set<String>) -> Unit)? = onDenied,
        onAllGranted: () -> Unit,
    ): ResultListener? {
        val handler = PermissionsCallbacks(
            onPermanentlyDeniedPermissions = if (permanentlyDeniedPermissionsHandler != null) { denied ->
                permanentlyDeniedPermissionsHandler.showMessage(requestCode, rationale, denied, onNegativePermanentlyDeniedAction)
            } else {
                null
            },
            onDenied = onDenied,
            onAllGranted = onAllGranted
        )
        return doOnPermissionsResult(
            activity,
            rationale,
            requestCode,
            permissions.toSet(),
            handler,
        )
    }

    /**
     * Логика метода следующая:
     * 1. если все [perms] получены, сразу выполняет [PermissionsCallbacks.onAllGranted]. Возвращает null, т.к.
     * запроса разрешений не производится
     * 1. иначе если среди [perms] есть разрешения, которые ранее юзер запретил с опцией "Больше не спрашивать",
     * показывает диалог перехода в настройки для дачи недостающих разрешений. Возвращает [PermissionsHelper.ResultListener]
     * для обработки результата после возврата назад в приложение из настроек.
     * 1. иначе запрашивает недостающие разрешения, возвращает [PermissionsHelper.ResultListener] для обработки
     * результата предоставления разрешений
     *
     * @return объект, в который надо отчитаться о результате или null, если запрос разрешений не требуется
     */
    fun doOnPermissionsResult(
        activity: Activity,
        rationale: String,
        requestCode: Int,
        perms: Collection<String>,
        callbacks: PermissionsCallbacks,
    ): ResultListener? {
        val filtered = filterPermissionsByApiVersion(perms)
        if (filtered.isEmpty() || hasPermissions(activity, false, filtered)) {
            callbacks.onAllGranted()
            return null
        }

        val deniedNotAskAgain = filterDeniedNotAskAgain(activity, filtered)
        if (deniedNotAskAgain.isNotEmpty()) {
            // В кейсе наличия ходя бы одного отклоненного с опцией "Больше не спрашивать" разрешения,
            // вызов YesNo диалога с переходом в настройки (в дефолтной реализации)
            // В диалог передаем не только permanentlyDenied, но и просто denied разрешения, т.к. после возврата
            // из настроек они также учитываются в полном перечне необходимых для выполнения действия разрешений
            val notGranted = filtered.filter { !hasPermissions(activity, false, listOf(it)) }.toSet()
            callbacks.onPermanentlyDeniedPermissions?.invoke(PermissionsCallbacks.DeniedPermissions(notGranted, deniedNotAskAgain))
        } else {
            requestPermissions(activity, rationale, requestCode, filtered)
        }
        return ResultListener(activity, filtered.toSet(), callbacks)
    }

    fun doOnStoragePermissionsResult(
        activity: Activity,
        rationale: String,
        requestCode: Int,
        callbacks: PermissionsCallbacks,
        manageAllFilesIfR: Boolean,
        applicationId: String
    ) {
        // write включаем для всех, но пригодится для некоторых
        val storagePermissions = mutableListOf(WRITE_EXTERNAL_STORAGE)
        // для target == 29: флаг requestLegacyExternalStorage:
        // 1. false - означает необходимость использования ScopedStorage и отсутствие необходимости запроса этого разрешения (has на write будет всегда true)
        // 2. true - не сработает на > 29 (Android 11 и выше) -> isExternalStorageLegacy будет всегда false

        // для версий >= Q даже с legacy=true has будет возвращать false на это, не включаем
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            storagePermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        doOnPermissionsResult(activity, rationale, requestCode, storagePermissions, PermissionsCallbacks(
            callbacks.onPermanentlyDeniedPermissions,
            callbacks.onDenied
        ) {
            if (manageAllFilesIfR && !isExternalStorageManager) {
                startManageAllFilesActivity(activity, requestCode, applicationId)
            } else {
                callbacks.onAllGranted.invoke()
            }
        })
    }

    fun filterDeniedNotAskAgain(context: Context, permission: Collection<String>): Set<String> {
        val result = mutableSetOf<String>()
        for (perm in permission) {
            if (isDeniedNotAskAgain(context, perm)) {
                result.add(perm)
            }
        }
        return result
    }

    fun isDeniedNotAskAgain(context: Context, permission: String): Boolean {
        if (permanentlyDeniedPrefs == null || !permanentlyDeniedPrefs.contains(permission)) return false
        return !hasPermissions(context, permission)
    }

    fun hasPermissions(context: Context, vararg perms: String) =
        hasPermissions(context, true, perms.toSet())

    fun hasPermissions(context: Context, perms: Collection<String>) =
        hasPermissions(context, true, perms)

    private fun hasPermissions(context: Context, filter: Boolean, perms: Collection<String>): Boolean {
        val target = if (filter) filterPermissionsByApiVersion(perms) else perms
        val granted = target.filter { EasyPermissions.hasPermissions(context, it) }
        removeFromDenied(granted)
        return target.size == granted.size
    }

    private fun requestPermissions(
        obj: Any?,
        rationale: String,
        requestCode: Int,
        perms: Set<String>
    ) {
        when (obj) {
            is View -> {
                requestPermissions(obj.context as? Activity, rationale, requestCode, perms)
            }
            is Fragment -> {
                EasyPermissions.requestPermissions(obj, rationale, requestCode, *perms.toTypedArray())
            }
            is Activity -> {
                EasyPermissions.requestPermissions(obj, rationale, requestCode, *perms.toTypedArray())
            }
            else -> {
                throw IllegalArgumentException("Incompatible type for $obj to requestPermissions")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun shouldShowRequestPermissionRationale(obj: Any, perm: String): Boolean {
        return when (obj) {
            is Activity -> {
                ActivityCompat.shouldShowRequestPermissionRationale(obj, perm)
            }
            is Fragment -> {
                obj.shouldShowRequestPermissionRationale(perm)
            }
            is View -> {
                shouldShowRequestPermissionRationale(obj.context as Activity, perm)
            }
            is android.app.Fragment -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    obj.shouldShowRequestPermissionRationale(perm)
                } else {
                    shouldShowRequestPermissionRationale(obj.activity, perm)
                }
            }
            else -> {
                throw IllegalArgumentException("Incompatible type for $obj to shouldShowRequestPermissionRationale")
            }
        }
    }

    /**
     * Фильтрует разрешения, которые не надо запрашивать для определенных версий апи (см. флаги в манифесте приложения)
     */
    private fun filterPermissionsByApiVersion(perms: Collection<String>): Set<String> {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            || Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && Environment.isExternalStorageLegacy()
        ) {
            // ниже Q или равно Q с requestLegacyExternalStorage=true в манифесте
            // write всегда, если есть (уже включает read)
            perms.toSet()
        } else {
            // > Q (или Q и не legacy) - форсированное использование scoped storage, разрешение на запись не требуется. На чтение нужно для
            // чтения чужих файлов или своих файлов после переустановки приложения
            perms.filter {
                // read возможно нужен для scoped, не убираем
                it != WRITE_EXTERNAL_STORAGE
            }.toSet()
        }
    }

    private fun removeFromDenied(perms: Collection<String>) {
        if (permanentlyDeniedPrefs == null) return
        perms.filter { permanentlyDeniedPrefs.contains(it) }.takeIf { it.isNotEmpty() }?.let {
            val editor = permanentlyDeniedPrefs.edit()
            it.forEach(editor::remove)
            editor.apply()
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun startManageAllFilesActivity(activity: Activity, requestCode: Int, applicationId: String) {
        try {
            val uri: Uri = Uri.parse("package:$applicationId")
            val intent = Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
            activity.startActivityForResult(intent, requestCode)
        } catch (e: Exception) {
            val intent = Intent()
            intent.action = ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
            activity.startActivityForResult(intent, requestCode)
        }
    }

    /**
     * Вызвать onRequestPermissionsResult или onAfterPermissionGranted
     * в зав-ти от реализации в целевом фрагменте/активити;
     */
    inner class ResultListener(
        val activity: Activity,
        val allPermissions: Set<String>,
        val callbacks: PermissionsCallbacks
    ) {

        init {
            check(allPermissions.isNotEmpty()) {
                "Instantiation of PermissionHandle without permissions is useless"
            }
        }

        /**
         * Предполагается один вызов из хостовой BaseActivity при возврате с экрана настроек
         * (любой результат)
         * @return true, если заранее известные [allPermissions] были предоставлены
         */
        fun onActivityResult(): Boolean {
            val denied = allPermissions.filter { !hasPermissions(activity, false, listOf(it)) }
            return callbacks.onAfterPermissionResult(denied.toSet())
        }

        /**
         * Предполагается один вызов из хостовой BaseActivity
         * @return true, если все [permissions] были предоставлены
         */
        fun onRequestPermissionsResult(permissions: Array<out String>, grantResults: IntArray): Boolean {
            val deniedNotAskAgain = mutableListOf<String>()
            val permissionResults = mutableMapOf<String, Boolean>()
            permissions.forEachIndexed { i, perm ->
                val isGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                if (!isGranted && !shouldShowRequestPermissionRationale(activity, perm)) {
                    deniedNotAskAgain.add(perm)
                }
                permissionResults[perm] = isGranted
            }
            if (deniedNotAskAgain.isNotEmpty() && permanentlyDeniedPrefs != null) {
                val editor = permanentlyDeniedPrefs.edit()
                for (perm in deniedNotAskAgain) {
                    editor.putBoolean(perm, true)
                }
                editor.apply()
            }
            lastPermissionsResult.value = permissionResults

            val denied = permissionResults.filterValues { !it }.keys
            return callbacks.onAfterPermissionResult(denied)
        }
    }
}
