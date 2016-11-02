package net.maxsmr.permissionchecker;

import android.app.Activity;
import android.app.Dialog;
import android.database.Observable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


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
//    private boolean mEnableExitOnDismiss = false;

    private Dialog mGrantedDialog;

    @NonNull
    private final OnDialogShowObservable dialogShowObservable = new OnDialogShowObservable();

    private final Set<String> mLastGrantedPermissions = new LinkedHashSet<>();
    private final Set<String> mLastDeniedPermissions = new LinkedHashSet<>();

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
        mLastGrantedPermissions.clear();
        mLastDeniedPermissions.clear();
        mPermissionsRequestCodes.clear();
        mActivity = null;
        mGrantedDialog = null;
        mDeniedDialog = null;
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

    public int getLastGrantedPermissionsCount() {
        return mLastGrantedPermissions.size();
    }

    public Set<String> getLastGrantedPermissions() {
        return Collections.unmodifiableSet(mLastGrantedPermissions);
    }

    public int getLastDeniedPermissionsCount() {
        return mLastDeniedPermissions.size();
    }

    public Set<String> getLastDeniedPermissions() {
        return Collections.unmodifiableSet(mLastDeniedPermissions);
    }

    public void setDeniedDialog(Dialog deniedDialog/*, boolean enableExitOnDismiss*/) {
        this.mDeniedDialog = deniedDialog;
//        this.mEnableExitOnDismiss = enableExitOnDismiss;
    }

    public void setGrantedDialog(Dialog grantedDialog) {
        this.mGrantedDialog = grantedDialog;
    }

    private void showDeniedDialog(String permission) {
        dialogShowObservable.dispatchBeforeDeniedDialogShow(permission);
        if (mDeniedDialog != null) {
//            if (mEnableExitOnDismiss) {
//                mDeniedDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
//                    @Override
//                    public void onDismiss(DialogInterface dialog) {
//                        mActivity.finish();
//                        System.exit(0);
//                    }
//                });
//            }
            mDeniedDialog.show();
        }
    }

    private void showGrantedDialog(String permission) {
        dialogShowObservable.dispatchBeforeGrantedDialogShow(permission);
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
//            int newCode = !codes.isEmpty() ? codes.toArray(new Integer[codes.size()])[codes.size() - 1] << codes.size() : 1;
            int newCode = PermissionUtils.generateRequestCode(codes);
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

        if (permissions.length == 0 || grantResults.length == 0) {
            return false;
        }

        if (permissions.length > 1) {
            throw new IllegalArgumentException("permissions must contain only one element");
        }

        if (grantResults.length > 1) {
            throw new IllegalArgumentException("grantResults must contain only one element");
        }

        String permission = getPermissionForRequestCode(requestCode);

        if (permission == null) {
            throw new RuntimeException("unregistered permission with requestCode: " + requestCode);
        }

        boolean granted = PermissionUtils.isPermissionGranted(requestCode, requestCode, grantResults, 0);
        if (!granted) {
            handlePermissionDenied(permission);
        } else {
            handlePermissionGranted(permission);
        }
        return granted;
    }

    /**
     * @return false if at least one permission is not granted, true otherwise
     */
    public boolean checkAppPermissions() {
        boolean has = true;
        mLastGrantedPermissions.clear();
        mLastDeniedPermissions.clear();
        for (String permission : mPermissionsRequestCodes.keySet()) {
            if (PermissionUtils.has(mActivity, permission)) {
                mLastGrantedPermissions.add(permission);
            } else {
                mLastDeniedPermissions.add(permission);
                has = false;
            }
        }
        return has;
    }

    /**
     * @return false if at least one system dialog was not shown on missing permission, true if all dialogs were shown
     */
    public boolean requestAppPermissions() {
        mLastGrantedPermissions.clear();
        mLastDeniedPermissions.clear();
        boolean result = true;
        for (Map.Entry<String, Integer> entry : mPermissionsRequestCodes.entrySet()) {
            PermissionUtils.PermissionResponse response = PermissionUtils.requestRuntimePermission(mActivity, entry.getKey(), entry.getValue());
            if (!response.hasPermission && !response.isDialogShown) {
                result = false;
                handlePermissionDenied(entry.getKey());
            } else {
                if (response.hasPermission) {
                    handlePermissionGranted(entry.getKey());
                }
            }
        }
        return result;
    }

    private void handlePermissionGranted(String permission) {
        if (!mPermissionsRequestCodes.containsKey(permission)) {
            throw new IllegalArgumentException("no such permission: " + permission);
        }
        mLastGrantedPermissions.add(permission);
        showGrantedDialog(permission);
    }

    private void handlePermissionDenied(String permission) {
        if (!mPermissionsRequestCodes.containsKey(permission)) {
            throw new IllegalArgumentException("no such permission: " + permission);
        }
        mLastDeniedPermissions.add(permission);
        showDeniedDialog(permission);
    }

    public interface OnDialogShowListener {

        void onBeforeGrantedDialogShow(@Nullable Dialog dialog, String permission);

        void onBeforeDeniedDialogShow(@Nullable Dialog dialog, String permission);
    }

    private class OnDialogShowObservable extends Observable<OnDialogShowListener> {

        void dispatchBeforeGrantedDialogShow(String permission) {
            for (OnDialogShowListener l : mObservers) {
                l.onBeforeGrantedDialogShow(mGrantedDialog, permission);
            }
        }

        void dispatchBeforeDeniedDialogShow(String permission) {
            for (OnDialogShowListener l : mObservers) {
                l.onBeforeDeniedDialogShow(mDeniedDialog, permission);
            }
        }
    }

}
