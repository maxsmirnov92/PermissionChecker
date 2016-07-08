package ru.maxsmr.permissionchecker;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.database.Observable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

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

    private final Map<String, Integer> mPermissionsRequestCodes = new LinkedHashMap<>();

    private Activity mActivity;

    private Dialog mDeniedDialog;
    private boolean mEnableExit;

    private Dialog mGrantedDialog;

    private final OnDialogShowObservable dialogShowObservable = new OnDialogShowObservable();

    private PermissionChecker(@NonNull Activity activity) {
        this.mActivity = activity;
        this.init();
    }

    private void init() {
        List<String> permissions = PackageHelper.getPermissionsForPackage(mActivity, mActivity.getPackageName());
        for (String permission : permissions) {
            generateRequestCodeForPermission(permission);
        }
    }

    private void release() {
        mActivity = null;
        mDeniedDialog = null;
        mPermissionsRequestCodes.clear();
    }

    public Observable<OnDialogShowListener> getDialogShowObservable() {
        return dialogShowObservable;
    }

    public int getPermissionsCount() {
        return mPermissionsRequestCodes.size();
    }

    public Map<String, Integer> getPermissions() {
        return new LinkedHashMap<>(mPermissionsRequestCodes);
    }

    public void setDeniedDialog(Dialog deniedDialog, boolean enableExit) {
        this.mDeniedDialog = deniedDialog;
        this.mEnableExit = enableExit;
    }

    public void setGrantedDialog(Dialog grantedDialog) {
        this.mGrantedDialog = grantedDialog;
    }

    private void showDeniedDialog(String permission) {
        dialogShowObservable.dispatchDeniedDialogShow(permission);
        if (mDeniedDialog != null) {
            if (mEnableExit) {
                mDeniedDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        mActivity.finish();
                        System.exit(0);
                    }
                });
            }
            mDeniedDialog.show();
        }
    }

    private void showGrantedDialog(String permission) {
        dialogShowObservable.dispatchGrantedDialogShow(permission);
        if (mGrantedDialog != null) {
            mGrantedDialog.show();
        }
    }

    private int generateRequestCodeForPermission(String permission) {
        if (!TextUtils.isEmpty(permission)) {
            Integer requestCode = mPermissionsRequestCodes.get(permission);
            if (requestCode != null) {
                return requestCode;
            }
            Collection<Integer> codes = mPermissionsRequestCodes.values();
            int newCode = !codes.isEmpty() ? codes.toArray(new Integer[codes.size()])[codes.size() - 1] << codes.size() : 1;
            mPermissionsRequestCodes.put(permission, newCode);
            return newCode;
        }
        return NO_REQUEST_CODE;
    }

    public int getRequestCodeForPermission(String permission) {
        Integer code = mPermissionsRequestCodes.get(permission);
        return code != null ? code : NO_REQUEST_CODE;
    }

    @Nullable
    public String getPermissionForRequestCode(int code) {
        for (Map.Entry<String, Integer> entry : mPermissionsRequestCodes.entrySet()) {
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
            showDeniedDialog(permission);
        } else {
            showGrantedDialog(permission);
        }
        return granted;
    }

    public void requestAppPermissions() {
        for (Map.Entry<String, Integer> entry : mPermissionsRequestCodes.entrySet()) {
            PermissionUtils.PermissionResponse response = PermissionUtils.requestRuntimePermission(mActivity, entry.getKey(), entry.getValue());
            if (!response.hasPermission && !response.isDialogShown) {
                showDeniedDialog(entry.getKey());
            } else {
                showGrantedDialog(entry.getKey());
            }
        }
    }

    public interface OnDialogShowListener {
        void onGrantedDialogShow(@Nullable  Dialog dialog, String permission);
        void onDeniedDialogShow(@Nullable Dialog dialog, String permission);
    }

    private class OnDialogShowObservable extends Observable<OnDialogShowListener> {

        void dispatchGrantedDialogShow(String permission) {
            for (OnDialogShowListener l : mObservers) {
                l.onGrantedDialogShow(mGrantedDialog, permission);
            }
        }

        void dispatchDeniedDialogShow(String permission) {
            for (OnDialogShowListener l : mObservers) {
                l.onDeniedDialogShow(mDeniedDialog, permission);
            }
        }
    }

}
