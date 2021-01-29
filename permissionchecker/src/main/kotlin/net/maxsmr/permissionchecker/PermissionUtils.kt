package net.maxsmr.permissionchecker

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import pub.devrel.easypermissions.EasyPermissions

@JvmOverloads
fun checkAndRequestPermissions(
        obj: Activity,
        rationale: String,
        requestCode: Int,
        perms: Set<String>,
        rationaleAction: (RationaleActionParams) -> Unit,
        targetAction: (() -> Unit)? = null
) = checkAndRequestPermissions(obj as Any, rationale, requestCode, perms, rationaleAction, targetAction)

@JvmOverloads
fun checkAndRequestPermissions(
        obj: Fragment,
        rationale: String,
        requestCode: Int,
        perms: Set<String>,
        rationaleAction: (RationaleActionParams) -> Unit,
        targetAction: (() -> Unit)? = null
) = checkAndRequestPermissions(obj as Any, rationale, requestCode, perms, rationaleAction, targetAction)

fun checkAndRequestPermissions(
        obj: Any,
        rationale: String,
        requestCode: Int,
        perms: Set<String>,
        rationaleAction: (RationaleActionParams) -> Unit,
        targetAction: (() -> Unit)? = null
): Boolean = if (perms.isEmpty() || EasyPermissions.hasPermissions(obj.asContextOrThrow(), *perms.toTypedArray())) {
    targetAction?.invoke()
    true
} else {
    requestPermissions(
            obj,
            rationale,
            requestCode,
            perms,
            rationaleAction
    )
    false
}

fun requestPermissions(
        activity: Activity,
        rationale: String,
        requestCode: Int,
        perms: Set<String>,
        rationaleAction: (RationaleActionParams) -> Unit
) {
    requestPermissions(activity as Any, rationale, requestCode, perms, rationaleAction)
}

fun requestPermissions(
        fragment: Fragment,
        rationale: String,
        requestCode: Int,
        perms: Set<String>,
        rationaleAction: (RationaleActionParams) -> Unit
) {
    requestPermissions(fragment as Any, rationale, requestCode, perms, rationaleAction)
}

fun requestPermissions(
        obj: Any,
        rationale: String,
        requestCode: Int,
        perms: Set<String>,
        rationaleAction: (RationaleActionParams) -> Unit
) {
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O) {
        val rationalePermissions = filterRationalePermissions(obj.asContextOrThrow(), perms.toList())
//         В Android 8.0 при запросе разрешения [ActivityCompat.requestPermissions] и,
//         как следствие, [EasyPermissions.requestPermissions] возникает ошибка.
        rationaleAction(RationaleActionParams(obj,
                rationale,
                rationalePermissions))
    } else {
        if (obj is Fragment) {
            EasyPermissions.requestPermissions(obj, rationale, requestCode, *perms.toTypedArray())
        } else if (obj is Activity) {
            EasyPermissions.requestPermissions(obj, rationale, requestCode, *perms.toTypedArray())
        }
    }
}

fun filterRationalePermissions(context: Context, perms: List<String>): List<String> = perms.filter {
    !EasyPermissions.hasPermissions(context, it)
            || Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(context, it)
}


@Suppress("DEPRECATION")
@TargetApi(Build.VERSION_CODES.M)
fun shouldShowRequestPermissionRationale(obj: Any, perm: String): Boolean {
    return when (obj) {
        is Activity -> {
            ActivityCompat.shouldShowRequestPermissionRationale(obj, perm)
        }
        is Fragment -> {
            obj.shouldShowRequestPermissionRationale(perm)
        }
        is android.app.Fragment -> {
            obj.shouldShowRequestPermissionRationale(perm)
        }
        else -> {
            false
        }
    }
}

internal fun Any.asContext(): Context? = try {
    asContextOrThrow()
} catch (e: ClassCastException) {
    null
}

@Throws(ClassCastException::class)
internal fun Any.asContextOrThrow(): Context {
    return when (this) {
        is Context -> {
            this
        }
        is Fragment -> {
            this.requireContext()
        }
        else -> {
            throw ClassCastException("Cannot cast to Context")
        }
    }
}

internal fun Any.asActivity(): Activity? = try {
    asActivityOrThrow()
} catch (e: ClassCastException) {
    null
}

@Throws(ClassCastException::class)
internal fun Any.asActivityOrThrow(): Activity {
    return when (this) {
        is Activity -> {
            this
        }
        is Fragment -> {
            this.requireActivity()
        }
        else -> {
            throw ClassCastException("Cannot cast to Activity")
        }
    }
}

class RationaleActionParams(
        val callObj: Any,
        val rationaleMessage: String,
        val perms: List<String>
)