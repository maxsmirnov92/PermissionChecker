package net.maxsmr.permissionchecker

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.CallSuper

/**
 * Используется для обработки запроса разрешений для SDK_INT = 28
 */
@TargetApi(Build.VERSION_CODES.O)
abstract class BasePermissionsRationaleHandler {

    protected var lastCallObj: Any? = null
    protected var lastFinishOnReject: Boolean = false
    protected var lastNegativeAction: (() -> Unit)? = null

    @CallSuper
    open fun displayRationalePermissionDialog(
            callObj: Any,
            rationale: String,
            perms: List<String>,
            finishOnReject: Boolean = false,
            negativeAction: (() -> Unit)? = null,
    ) {
        lastCallObj = callObj
        lastFinishOnReject = finishOnReject
        lastNegativeAction = negativeAction
    }

    protected abstract fun createAppSettingsIntent(context: Context): Intent

    protected open fun onRationalePositiveClick() {
        lastCallObj?.let {
            val context = it.asContextOrThrow()
            context.startActivity(createAppSettingsIntent(context))
        }
    }

    protected open fun onRationaleNegativeClick() {
        lastCallObj?.let {
            lastNegativeAction?.invoke()
            if (lastFinishOnReject) {
                finish(it)
            }
        }
    }

    protected open fun finish(callObj: Any) {
        callObj.asActivityOrThrow().finish()
    }
}