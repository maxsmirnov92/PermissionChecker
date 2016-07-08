package ru.maxsmr.permissionchecker;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class PermissionChecker {

    public static final int NO_REQUEST_CODE = -1;

    private static PermissionChecker sInstance;

    public static void initInstance(Activity activity) {
        if (sInstance == null) {
            synchronized (PermissionChecker.class) {
                sInstance = new PermissionChecker(activity);
            }
        }
    }

    public static PermissionChecker getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("initInstance() was not called");
        }
        return sInstance;
    }

    public static void releaseInstance() {
        if (sInstance != null) {
            sInstance.release();
            sInstance = null;
        }
    }

    private final Map<String, Integer> requestCodes = new LinkedHashMap<>();

    private Activity activity;

    private Dialog deniedDialog;
    private boolean enableExit;

    private Dialog grantedDialog;

    private PermissionChecker(@NonNull Activity activity) {
        this.activity = activity;
        this.init();
    }

    private void init() {
        List<String> permissions = PackageHelper.getPermissionsForPackage(activity, activity.getPackageName());
        for (String permission : permissions) {
            generateRequestCodeForPermission(permission);
        }
    }

    private void release() {
        activity = null;
        deniedDialog = null;
        requestCodes.clear();
    }

    public int getPermissionsCount() {
        return requestCodes.size();
    }

    public Map<String, Integer> getPermissions() {
        return new LinkedHashMap<>(requestCodes);
    }

    public void setDeniedDialog(Dialog deniedDialog, boolean enableExit) {
        this.deniedDialog = deniedDialog;
        this.enableExit = enableExit;
    }

    public void setGrantedDialog(Dialog grantedDialog) {
        this.grantedDialog = grantedDialog;
    }

    private void showFailDialog() {
        if (deniedDialog != null) {
            if (enableExit) {
                deniedDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        activity.finish();
                        System.exit(0);
                    }
                });
            }
            deniedDialog.show();
        }
    }

    private void showGrantedDialog() {
        if (grantedDialog != null) {
            grantedDialog.show();
        }
    }

    private int generateRequestCodeForPermission(String permission) {
        Integer requestCode = requestCodes.get(permission);
        if (requestCode != null) {
            return requestCode;
        }
        Collection<Integer> codes = requestCodes.values();
        int newCode = !codes.isEmpty() ? codes.toArray(new Integer[codes.size()])[codes.size() - 1] << codes.size() : 1;
        requestCodes.put(permission, newCode);
        return newCode;
    }

    public int getRequestCodeForPermission(String permission) {
        Integer code = requestCodes.get(permission);
        return code != null ? code : NO_REQUEST_CODE;
    }

    @Nullable
    public String getPermissionForRequestCode(int code) {
        for (Map.Entry<String, Integer> entry : requestCodes.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == code) {
                return entry.getKey();
            }
        }
        return null;
    }

    public boolean onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (permissions.length != 1) {
            throw new IllegalArgumentException("permissions must contain only one element");
        }

        if (grantResults.length != 1) {
            throw new IllegalArgumentException("grantResults must contain only one element");
        }

        String permission = getPermissionForRequestCode(requestCode);

        if (permission == null) {
            throw new RuntimeException("unregistered permission with requestCode: " + requestCode);
        }

        boolean granted = PermissionUtils.isPermissionGranted(requestCode, requestCode, grantResults, 0);
        if (!granted) {
            showFailDialog();
        } else {
            showGrantedDialog();
        }
        return granted;
    }

    public void requestAppPermissions() {
        for (Map.Entry<String, Integer> entry : requestCodes.entrySet()) {
            PermissionUtils.PermissionResponse response = PermissionUtils.requestRuntimePermission(activity, entry.getKey(), entry.getValue());
            if (!response.hasPermission && !response.isDialogShown) {
                showFailDialog();
            } else {
                showGrantedDialog();
            }
        }
    }

}
