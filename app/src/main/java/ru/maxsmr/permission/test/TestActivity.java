package ru.maxsmr.permission.test;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.util.Arrays;

import ru.maxsmr.permissionchecker.PermissionChecker;


public class TestActivity extends AppCompatActivity {

    @NonNull
    private Dialog createAlertDeniedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_message_permission_denied)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, null);
        return builder.create();
    }

    @NonNull
    private Dialog createAlertGrantedDialog(String permission) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(String.format(getString(R.string.dialog_message_permission_granted), permission))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, null);
        return builder.create();
    }

    @NonNull
    private Dialog createAlertNoPermissionsDialog() {
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

    boolean isSettingsScreenShowed = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PermissionChecker.initInstance(this);
        if (PermissionChecker.getInstance().getPermissionsCount() > 0) {
            PermissionChecker.getInstance().setDeniedDialog(createAlertDeniedDialog(), true);
            PermissionChecker.getInstance().setGrantedDialog(createAlertGrantedDialog(null));
            PermissionChecker.getInstance().requestAppPermissions();
        } else {
            createAlertNoPermissionsDialog().show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TestActivity.class.getSimpleName(), "onRequestPermissionsResult: requestCode=" + requestCode + ", permissions=" + Arrays.toString(permissions) + ", grantResults=" + Arrays.toString(grantResults));
        PermissionChecker.getInstance().setDeniedDialog(null, false);
        if (!PermissionChecker.getInstance().onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            if (!isSettingsScreenShowed) {
                openAppSettingScreen();
                isSettingsScreenShowed = true;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PermissionChecker.releaseInstance();
    }

    private void openAppSettingScreen() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(Uri.fromParts("package", this.getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(intent);
    }
}
