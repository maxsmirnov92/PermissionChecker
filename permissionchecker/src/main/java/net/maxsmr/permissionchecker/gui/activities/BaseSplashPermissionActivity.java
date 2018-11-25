package net.maxsmr.permissionchecker.gui.activities;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.MainThread;
import android.support.v7.app.AlertDialog;

import net.maxsmr.permissionchecker.PackageHelper;
import net.maxsmr.permissionchecker.PermissionChecker;
import net.maxsmr.permissionchecker.R;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;


public abstract class BaseSplashPermissionActivity extends BaseSplashActivity implements PermissionChecker.OnDialogShowListener {

    private static final String ARG_IS_SETTINGS_SCREEN_SHOWED = BaseSplashPermissionActivity.class.getName() + ".ARG_IS_SETTINGS_SCREEN_SHOWED";

    private final Stack<Dialog> grantedDialogs = new Stack<>();
    private final Stack<Dialog> deniedDialogs = new Stack<>();

    private boolean isCheckingPermissionsEnabled;
    private boolean isShowingSplashEnabled;

    private boolean isSettingsScreenShowedOnce = false;

    private boolean isSplashTimeouted = false;

    public List<Dialog> getGrantedDialogs() {
        return new ArrayList<>(grantedDialogs);
    }

    public List<Dialog> getDeniedDialogs() {
        return new ArrayList<>(deniedDialogs);
    }

    protected abstract boolean isShowingSplashEnabled();

    protected abstract boolean isCheckingPermissionsEnabled();

    protected abstract boolean isShowingAllSystemDialogsEnabled();

    protected abstract boolean isShowingGrantedDialogEnabled(String permission);

    @Nullable
    protected abstract Collection<String> getPermissionsToIgnore();

    @Nullable
    protected abstract Collection<String> getPermissionsToIgnoreAfterCheck();

    protected boolean isFinalActionAllowed() {
        return grantedDialogs.isEmpty() && isSplashTimeouted && (!isCheckingPermissionsEnabled || PermissionChecker.getInstance().isAllPermissionsGranted());
    }

    @NotNull
    private Dialog createPermissionAlertDialog(final String permission, final boolean granted, DialogInterface.OnClickListener positiveClickListener) {
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
                            onGrantedDialogDismiss(permission, dialog);
                        } else {
                            if (deniedDialogs.contains(dialog)) {
                                deniedDialogs.remove(dialog);
                            }
                            onDeniedDialogDismiss(permission, dialog);
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
    }

    private void dismissDialogs(@NotNull Stack<Dialog> dialogs) {
        while (!dialogs.isEmpty()) {
            Dialog d = dialogs.pop();
            if (d != null && d.isShowing()) {
                d.dismiss();
            }
        }
    }


    protected boolean isAllPermissionsChecked() {
        return !isCheckingPermissionsEnabled || PermissionChecker.getInstance().isAllPermissionsChecked();
    }

    protected boolean isAllPermissionsGranted() {
        return !isCheckingPermissionsEnabled || PermissionChecker.getInstance().isAllPermissionsGranted();
    }

    private void initPermissionChecker() {
        if (isCheckingPermissionsEnabled) {
            PermissionChecker.initInstance(this, isShowingAllSystemDialogsEnabled(), getPermissionsToIgnore(), getPermissionsToIgnoreAfterCheck());
            if (PermissionChecker.getInstance().hasPermissions() || PermissionChecker.getInstance().hasSpecialPermissions()) {
                PermissionChecker.getInstance().getDialogShowObservable().registerObserver(this);
            }
        }
    }

    protected void requestPermissions() {
        if (isCheckingPermissionsEnabled) {
            dismissAndClearGrantedDialogs();
            dismissAndClearDeniedDialogs();
            if (!PermissionChecker.getInstance().checkAppPermissions()) {
                PermissionChecker.getInstance().requestAppPermissions();
            } else if (isFinalActionAllowed()) {
                doFinalAction();
            }
        }
    }

    /**
     * @return 0 if splash not needed
     */
    protected abstract long getBaseSplashTimeout();

    @Override
    protected final long getSplashTimeout() {
        return isShowingSplashEnabled /*|| (isCheckingPermissionsEnabled && !PermissionChecker.getInstance().isAllPermissionsGranted())*/ ? getBaseSplashTimeout() : 0;
    }

    public boolean isSplashTimeouted() {
        return isSplashTimeouted;
    }

    @Override
    protected void onSplashTimeout() {
        isSplashTimeouted = true;
        if (isFinalActionAllowed()) {
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
    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions, @NotNull int[] grantResults) {
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
            if (!isSettingsScreenShowedOnce && isAllPermissionsChecked() && !isAllPermissionsGranted()) {
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
    public void onBeforeGrantedDialogShow(@Nullable Dialog dialog, final String permission) {
        if (isShowingGrantedDialogEnabled(permission)) {
            dialog = createPermissionAlertDialog(permission, true, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    onGrantedDialogPositiveClick(permission, dialog);
                }
            });
            grantedDialogs.push(dialog);
            PermissionChecker.getInstance().setGrantedDialog(dialog);
        }
    }

    @Override
    @CallSuper
    public void onBeforeDeniedDialogShow(@Nullable Dialog dialog, final String permission) {
        dialog = createPermissionAlertDialog(permission, false, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onDeniedDialogPositiveClick(permission, dialog);
            }
        });
        deniedDialogs.push(dialog);
        PermissionChecker.getInstance().setDeniedDialog(dialog);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(ARG_IS_SETTINGS_SCREEN_SHOWED, isSettingsScreenShowedOnce);
    }

    protected void onGrantedDialogPositiveClick(String permission, DialogInterface dialog) {

    }

    protected void onDeniedDialogPositiveClick(String permission, DialogInterface dialog) {

    }

    protected void onGrantedDialogDismiss(String permission, DialogInterface dialog) {
        if (grantedDialogs.isEmpty()) {
            if (isFinalActionAllowed()) {
                doFinalAction();
            }
        }
    }

    protected void onDeniedDialogDismiss(String permission, DialogInterface dialog) {
        if (deniedDialogs.isEmpty()) {
            openAppSettingsScreen();
            finish();
            System.exit(0);
        }
    }
}
