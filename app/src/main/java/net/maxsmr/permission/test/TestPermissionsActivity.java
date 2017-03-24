package net.maxsmr.permission.test;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import net.maxsmr.permissionchecker.PackageHelper;
import net.maxsmr.permissionchecker.PermissionChecker;

import java.util.Arrays;
import java.util.Stack;


@Deprecated
public class TestPermissionsActivity extends AppCompatActivity implements PermissionChecker.OnDialogShowListener {

    private static final String ARG_IS_SETTINGS_SCREEN_SHOWED = TestPermissionsActivity.class.getName() + ".ARG_IS_SETTINGS_SCREEN_SHOWED";

    private TextView messageView;

    private final Stack<Dialog> grantedDialogs = new Stack<>();
    private final Stack<Dialog> deniedDialogs = new Stack<>();

    private boolean isExitOnPositiveClickSet = false;
    private boolean isSettingsScreenShowedOnce = false;

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
                        if (granted && grantedDialogs.contains(dialog)) {
                            grantedDialogs.remove(dialog);
                        } else {
                            deniedDialogs.remove(dialog);
                            if (deniedDialogs.isEmpty()) {
                                isExitOnPositiveClickSet = false;
                            }
                        }
                    }
                });
        return builder.create();
    }

    @NonNull
    private Dialog createNoPermissionsAlertDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_message_permissions_empty)
                .setCancelable(true)
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        dialog.dismiss();
                        finish();
                        System.exit(0);
                    }
                });
        return builder.create();
    }

    private void dismissGrantedDialogs() {
        dismissDialogs(grantedDialogs);
    }

    private void dismissDeniedDialogs() {
        dismissDialogs(deniedDialogs);
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

    private boolean isAllPermissionsChecked() {
        return (PermissionChecker.getInstance().getLastGrantedPermissionsCount() + PermissionChecker.getInstance().getLastDeniedPermissionsCount())
                == PermissionChecker.getInstance().getPermissionsCount();
    }

    private void initPermissionChecker() {
        PermissionChecker.initInstance(this, false);
        if (PermissionChecker.getInstance().getPermissionsCount() > 0) {
            PermissionChecker.getInstance().getDialogShowObservable().registerObserver(this);
        } else {
            createNoPermissionsAlertDialog().show();
        }
    }

    private void requestPermissions() {
        dismissGrantedDialogs();
        dismissDeniedDialogs();
        if (!PermissionChecker.getInstance().checkAppPermissions()) {
            /*if (!*/PermissionChecker.getInstance().requestAppPermissions();/*) {*/
//                openAppSettingsScreen();
//            }
        }
        invalidateMessageView();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_test);
        messageView = (TextView) findViewById(R.id.tvMessage);

        initPermissionChecker();
        if (savedInstanceState != null) {
            isSettingsScreenShowedOnce = savedInstanceState.getBoolean(ARG_IS_SETTINGS_SCREEN_SHOWED);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestPermissions();
    }

    private void invalidateMessageView() {
        if (PermissionChecker.getInstance().checkAppPermissions()) {
            messageView.setText(R.string.text_all_permissions_granted);
        } else {
            messageView.setText(null);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TestPermissionsActivity.class.getSimpleName(), "onRequestPermissionsResult: requestCode=" + requestCode + ", permissions=" + Arrays.toString(permissions) + ", grantResults=" + Arrays.toString(grantResults));
        /*boolean result = */PermissionChecker.getInstance().onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (!result) {
//            openAppSettingsScreen();
//        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissGrantedDialogs();
        dismissDeniedDialogs();
        PermissionChecker.getInstance().getDialogShowObservable().unregisterObserver(this);
        PermissionChecker.releaseInstance();
    }

    private void openAppSettingsScreen() {
        if (!isSettingsScreenShowedOnce && isAllPermissionsChecked() && !PermissionChecker.getInstance().checkAppPermissions()) {
            PackageHelper.openAppSettingsScreen(this);
            isSettingsScreenShowedOnce = true;
        }
    }

    @Override
    public void onDismissAllDialogs() {
        dismissGrantedDialogs();
        dismissDeniedDialogs();
    }

    @Override
    public void onBeforeGrantedDialogShow(@Nullable Dialog dialog, String permission) {
        grantedDialogs.push(createPermissionAlertDialog(permission, true, null));
        PermissionChecker.getInstance().setGrantedDialog(grantedDialogs.peek());
    }

    @Override
    public void onBeforeDeniedDialogShow(@Nullable Dialog dialog, String permission) {
        deniedDialogs.push(createPermissionAlertDialog(permission, false, !isExitOnPositiveClickSet ? new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (deniedDialogs.size() == 1) {
                    openAppSettingsScreen();
                }
                TestPermissionsActivity.this.finish();
                System.exit(0);
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
