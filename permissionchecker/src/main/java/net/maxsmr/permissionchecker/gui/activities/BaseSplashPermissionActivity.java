package net.maxsmr.permissionchecker.gui.activities;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;

import net.maxsmr.permissionchecker.PackageHelper;
import net.maxsmr.permissionchecker.PermissionChecker;
import net.maxsmr.permissionchecker.R;

import java.util.Collection;
import java.util.Stack;


public abstract class BaseSplashPermissionActivity extends BaseSplashActivity implements PermissionChecker.OnDialogShowListener {

    private static final String ARG_IS_SETTINGS_SCREEN_SHOWED = BaseSplashPermissionActivity.class.getName() + ".ARG_IS_SETTINGS_SCREEN_SHOWED";

    private final Stack<Dialog> grantedDialogs = new Stack<>();
    private final Stack<Dialog> deniedDialogs = new Stack<>();

    private boolean isCheckingPermissionsEnabled;
    private boolean isShowingSplashEnabled;

    private boolean isExitOnPositiveClickSet = false;
    private boolean isSettingsScreenShowedOnce = false;

    private boolean isSplashTimeouted = false;

    protected abstract boolean isShowingSplashEnabled();

    protected abstract boolean isCheckingPermissionsEnabled();

    protected abstract boolean isShowingAllSystemDialogsEnabled();

    protected abstract boolean isShowingGrantedDialogEnabled(String permission);

    protected abstract Collection<String> getPermissionsToIgnore();

    protected boolean isFinalActionAllowed() {
        return grantedDialogs.isEmpty() && isSplashTimeouted && (!isCheckingPermissionsEnabled || PermissionChecker.getInstance().isAllPermissionsGranted());
    }

    @NonNull
    private Dialog createPermissionAlertDialog(String permission, final boolean granted, DialogInterface.OnClickListener positiveClickListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(
                String.format(granted ? getString(R.string.dialog_message_permission_granted) :
                                getString(R.string.dialog_message_permission_denied),
                        permission))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, positiveClickListener)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @SuppressWarnings("SuspiciousMethodCalls")
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        if (granted) {
                            if (grantedDialogs.contains(dialog)) {
                                grantedDialogs.remove(dialog);
                            }
                            if (isFinalActionAllowed()) {
                                doFinalAction();
                            }
                        } else {
                            if (deniedDialogs.contains(dialog)) {
                                deniedDialogs.remove(dialog);
                            }
                            if (deniedDialogs.isEmpty()) {
                                isExitOnPositiveClickSet = false;
                            }
                        }
                    }
                });
        return builder.create();
    }


    private void dismissAndClearGrantedDialogs() {
        dismissDialogs(grantedDialogs);
        grantedDialogs.clear();
    }

    private void dismissAndClearDeniedDialogs() {
        dismissDialogs(deniedDialogs);
        deniedDialogs.clear();
        isExitOnPositiveClickSet = false;
    }

    private void dismissDialogs(@NonNull Stack<Dialog> dialogs) {
        while (!dialogs.isEmpty()) {
            Dialog d = dialogs.pop();
            if (d != null && d.isShowing()) {
                d.dismiss();
            }
        }
    }


    protected boolean isAllPermissionsChecked() {
        return !isCheckingPermissionsEnabled() || PermissionChecker.getInstance().isAllPermissionsChecked();
    }

    protected boolean isAllPermissionsGranted() {
        return PermissionChecker.getInstance().isAllPermissionsGranted();
    }

    private void initPermissionChecker() {
        if (isCheckingPermissionsEnabled) {
            PermissionChecker.initInstance(this, isShowingAllSystemDialogsEnabled(), getPermissionsToIgnore());
            if (PermissionChecker.getInstance().hasPermissions() || PermissionChecker.getInstance().hasSpecialPermissions()) {
                PermissionChecker.getInstance().getDialogShowObservable().registerObserver(this);
            }
        }
    }

    private void requestPermissions() {
        if (isCheckingPermissionsEnabled) {
            dismissAndClearGrantedDialogs();
            dismissAndClearDeniedDialogs();
            if (!PermissionChecker.getInstance().checkAppPermissions()) {
                PermissionChecker.getInstance().requestAppPermissions();
            } else if (isSplashTimeouted) {
                doFinalAction();
            }
        }
    }

    /** @return 0 if splash not needed */
    protected abstract long getBaseSplashTimeout();

    @Override
    protected final long getSplashTimeout() {
        return isShowingSplashEnabled /*|| (isCheckingPermissionsEnabled && !PermissionChecker.getInstance().isAllPermissionsGranted())*/? getBaseSplashTimeout() : 0;
    }

    @Override
    protected void onSplashTimeout() {
        isSplashTimeouted = true;
        if ((!isCheckingPermissionsEnabled || PermissionChecker.getInstance().isAllPermissionsGranted() && grantedDialogs.isEmpty())) {
            doFinalAction();
        }
    }

    @MainThread
    protected abstract void doFinalAction();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isShowingSplashEnabled = isShowingSplashEnabled();
        isCheckingPermissionsEnabled = isCheckingPermissionsEnabled();
        if (savedInstanceState != null) {
            isSettingsScreenShowedOnce = savedInstanceState.getBoolean(ARG_IS_SETTINGS_SCREEN_SHOWED);
        }
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        initPermissionChecker();
        requestPermissions();
    }

    @Override
    @CallSuper
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (isCheckingPermissionsEnabled) {
            if (PermissionChecker.getInstance().onRequestPermissionsResult(requestCode, permissions, grantResults)) {
                if (isFinalActionAllowed()) {
                    doFinalAction();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isCheckingPermissionsEnabled) {
            dismissAndClearGrantedDialogs();
            dismissAndClearDeniedDialogs();
            if (PermissionChecker.getInstance().hasPermissions() || PermissionChecker.getInstance().hasSpecialPermissions()) {
                PermissionChecker.getInstance().getDialogShowObservable().unregisterObserver(this);
            }
//            PermissionChecker.releaseInstance();
        }
    }

    private void openAppSettingsScreen() {
        if (isCheckingPermissionsEnabled) {
            if (!isSettingsScreenShowedOnce && isAllPermissionsChecked() && !PermissionChecker.getInstance().isAllPermissionsGranted()) {
                PackageHelper.openAppSettingsScreen(this);
                isSettingsScreenShowedOnce = true;
            }
        }
    }

    @Override
    public void onDismissAllDialogs() {
        dismissAndClearGrantedDialogs();
        dismissAndClearDeniedDialogs();
    }

    @Override
    @CallSuper
    public void onBeforeGrantedDialogShow(@Nullable Dialog dialog, String permission) {
        if (isShowingGrantedDialogEnabled(permission)) {
            grantedDialogs.push(createPermissionAlertDialog(permission, true, null));
            PermissionChecker.getInstance().setGrantedDialog(grantedDialogs.peek());
        }
    }

    @Override
    @CallSuper
    public void onBeforeDeniedDialogShow(@Nullable Dialog dialog, String permission) {
        deniedDialogs.push(createPermissionAlertDialog(permission, false, !isExitOnPositiveClickSet ? new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (deniedDialogs.size() == 1) {
                    openAppSettingsScreen();
                    finish();
                    System.exit(0);
                }

            }
        } : null));
        PermissionChecker.getInstance().setDeniedDialog(deniedDialogs.peek());
        isExitOnPositiveClickSet |= true;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(ARG_IS_SETTINGS_SCREEN_SHOWED, isSettingsScreenShowedOnce);
    }

}
