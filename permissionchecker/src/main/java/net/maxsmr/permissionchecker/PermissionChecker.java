package net.maxsmr.permissionchecker;

import android.app.Activity;
import android.app.Dialog;
import android.database.Observable;
import android.os.Build;
import android.text.TextUtils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public final class PermissionChecker {

    public static final int NO_REQUEST_CODE = -1;

    private static PermissionChecker sInstance;

    public static void initInstance(Activity activity) {
        initInstance(activity, false);
    }

    public static void initInstance(Activity activity, boolean showAllSystemDialogs) {
        initInstance(activity, showAllSystemDialogs, null, null);
    }

    public static void initInstance(Activity activity, boolean showAllSystemDialogs, @Nullable Collection<String> permissionsToIgnore, @Nullable Collection<String> permissionsToIgnoreAfterCheck) {
        synchronized (PermissionChecker.class) {
            if (sInstance == null) {
                sInstance = new PermissionChecker(activity, showAllSystemDialogs, permissionsToIgnore, permissionsToIgnoreAfterCheck);
            }
        }
    }

    public static PermissionChecker getInstance() {
        synchronized (PermissionChecker.class) {
            if (sInstance == null) {
                throw new IllegalStateException("initInstance() was not called");
            }
            return sInstance;
        }
    }

    public static void releaseInstance() {
        synchronized (PermissionChecker.class) {
            if (sInstance != null) {
                sInstance.release();
                sInstance = null;
            }
        }
    }

    private static Set<String> sSpecialSystemPermissions = new LinkedHashSet<>();

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            sSpecialSystemPermissions.add(PermissionUtils.PERMISSION_WRITE_SETTINGS);
        }
    }

    private boolean isReleased = false;

    private final boolean mShowAllSystemDialogs;

    private final Map<String, Integer> mPermissionsRequestCodes = new LinkedHashMap<>();

    private Activity mActivity;

    private Dialog mDeniedDialog;

    private Dialog mGrantedDialog;

    @NotNull
    private final OnDialogShowObservable mDialogShowObservable = new OnDialogShowObservable();

    @NotNull
    private final Set<String> mSpecialPermissions = new HashSet<>();

    @NotNull
    private final Set<String> mPermissionsToIgnore = new HashSet<>();

    @NotNull
    private final Set<String> mPermissionsToIgnoreAfterCheck = new HashSet<>();

    /**
     * all last granted permissions (including {@link PermissionChecker#mSpecialPermissions} set)
     */
    private Set<String> mLastGrantedPermissions = new LinkedHashSet<>();
    /**
     * all last denied permissions (including {@link PermissionChecker#mSpecialPermissions} set)
     */
    private Set<String> mLastDeniedPermissions = new LinkedHashSet<>();

    private Set<String> mCheckedPermissions = new LinkedHashSet<>();

    private PermissionChecker(@NotNull Activity activity, boolean showAllSystemDialogs, @Nullable Collection<String> permissionsToIgnore, @Nullable Collection<String> permissionsToIgnoreAfterCheck) {
        mActivity = activity;
        mShowAllSystemDialogs = showAllSystemDialogs;
        if (permissionsToIgnore != null) {
            mPermissionsToIgnore.addAll(permissionsToIgnore);
        }
        if (permissionsToIgnoreAfterCheck != null) {
            mPermissionsToIgnoreAfterCheck.addAll(permissionsToIgnoreAfterCheck);
        }
        init();
    }

    private void init() {
        List<String> permissions = PackageHelper.getPermissionsForPackage(mActivity, mActivity.getPackageName());
        for (String permission : permissions) {
            if (!shouldIgnorePermission(permission, false)) {
                if (!sSpecialSystemPermissions.contains(permission)) {
                    generateRequestCodeForPermission(permission);
                } else {
                    mSpecialPermissions.add(permission);
                }
            }
        }
    }

    private synchronized void checkReleased() {
        if (isReleased) {
            throw new IllegalStateException(PermissionChecker.class.getSimpleName() + " was released");
        }
    }

    private synchronized void release() {
        checkReleased();
        clearCheckedPermissions();
        mLastGrantedPermissions = null;
        mLastDeniedPermissions = null;
        mCheckedPermissions = null;
        mPermissionsRequestCodes.clear();
        mActivity = null;
        mGrantedDialog = null;
        mDeniedDialog = null;
        isReleased = true;
    }

    @NotNull
    public Observable<OnDialogShowListener> getDialogShowObservable() {
        return mDialogShowObservable;
    }

    private boolean shouldIgnorePermission(String permission, boolean afterCheck) {
        if (TextUtils.isEmpty(permission)) {
            return false;
        }
        boolean ignore = false;
        for (String ignorePermission : (!afterCheck ? mPermissionsToIgnore : mPermissionsToIgnoreAfterCheck)) {
            if (permission.equalsIgnoreCase(ignorePermission)) {
                ignore = true;
                break;
            }
        }
        return ignore;
    }

    public boolean isAllPermissionsChecked() {
        return getLastGrantedPermissionsCount() + getLastDeniedPermissionsCount() == getPermissionsCount() + getSpecialPermissionsCount();
    }

    public boolean isAllPermissionsGranted() {
        if (isAllPermissionsChecked()) {
            int ignoreCount = 0;
            for (String ignorePermission : mPermissionsToIgnoreAfterCheck) {
                if (getLastDeniedPermissions().contains(ignorePermission)) {
                    ignoreCount++;
                }
            }
            return getLastGrantedPermissionsCount() + ignoreCount == getPermissionsCount() + getSpecialPermissionsCount();
        }
        return false;
    }

    public boolean hasPermissionsToIgnore() {
        return !mPermissionsToIgnore.isEmpty();
    }

    public boolean hasPermissionsToIgnoreAfterCheck() {
        return !mPermissionsToIgnoreAfterCheck.isEmpty();
    }

    public boolean hasPermissions() {
        return getPermissionsCount() > 0;
    }

    public synchronized int getPermissionsCount() {
        return mPermissionsRequestCodes.size();
    }

    public synchronized Set<String> getPermissions() {
        return Collections.unmodifiableSet(mPermissionsRequestCodes.keySet());
    }

    public synchronized Map<String, Integer> getPermissionsWithCodes() {
        return Collections.unmodifiableMap(mPermissionsRequestCodes);
    }

    public boolean hasLastGrantedPermissions() {
        return getLastGrantedPermissionsCount() > 0;
    }

    public synchronized int getLastGrantedPermissionsCount() {
        return mLastGrantedPermissions.size();
    }

    public synchronized Set<String> getLastGrantedPermissions() {
        return Collections.unmodifiableSet(mLastGrantedPermissions);
    }

    public boolean hasLastDeniedPermissions() {
        return getLastDeniedPermissionsCount() > 0;
    }

    public synchronized int getLastDeniedPermissionsCount() {
        return mLastDeniedPermissions.size();
    }

    public synchronized Set<String> getLastDeniedPermissions() {
        return Collections.unmodifiableSet(mLastDeniedPermissions);
    }

    public boolean hasSpecialPermissions() {
        return getSpecialPermissionsCount() > 0;
    }

    public synchronized int getSpecialPermissionsCount() {
        return mSpecialPermissions.size();
    }

    public synchronized Set<String> getSpecialPermissions() {
        return Collections.unmodifiableSet(mSpecialPermissions);
    }

    public void setDeniedDialog(Dialog deniedDialog) {
        this.mDeniedDialog = deniedDialog;
    }

    public void setGrantedDialog(Dialog grantedDialog) {
        this.mGrantedDialog = grantedDialog;
    }

    private void showDeniedDialog(String permission) {
        checkReleased();
        mDialogShowObservable.dispatchBeforeDeniedDialogShow(permission);
        if (mDeniedDialog != null) {
            mDeniedDialog.show();
        }
    }

    private void showGrantedDialog(String permission) {
        checkReleased();
        mDialogShowObservable.dispatchBeforeGrantedDialogShow(permission);
        if (mGrantedDialog != null) {
            mGrantedDialog.show();
        }
    }

    private synchronized int generateRequestCodeForPermission(String permission) {
        if (!TextUtils.isEmpty(permission)) {
            Integer requestCode = mPermissionsRequestCodes.get(permission);
            if (requestCode != null) {
                return requestCode;
            }
            Collection<Integer> codes = mPermissionsRequestCodes.values();
            int newCode = PermissionUtils.generateRequestCode(codes);
            mPermissionsRequestCodes.put(permission, newCode);
            return newCode;
        }
        return NO_REQUEST_CODE;
    }

    @NotNull
    private synchronized Map<String, Integer> permissionsRequestCodes(boolean isChecked) {
        Map<String, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : mPermissionsRequestCodes.entrySet()) {
            if (!isChecked && !mCheckedPermissions.contains(entry.getKey())
                    || (isChecked && (mCheckedPermissions.contains(entry.getKey())))) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }

    public synchronized int getRequestCodeForPermission(String permission) {
        Integer code = mPermissionsRequestCodes.get(permission);
        return code != null ? code : NO_REQUEST_CODE;
    }

    @Nullable
    public synchronized String getPermissionForRequestCode(int code) {
        for (Map.Entry<String, Integer> entry : mPermissionsRequestCodes.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == code) {
                return entry.getKey();
            }
        }
        return null;
    }

    public synchronized boolean onRequestPermissionsResult(int requestCode, @NotNull String[] permissions, @NotNull int[] grantResults) {


        checkReleased();

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
            handlePermissionDenied(permission, !shouldIgnorePermission(permission, true));
        } else {
            handlePermissionGranted(permission, !shouldIgnorePermission(permission, true));
        }

        return granted && (mShowAllSystemDialogs ||
                (!isAllPermissionsChecked() ? requestAppPermissions(false, permissionsRequestCodes(false).entrySet()) : isAllPermissionsGranted())); // remove already checked permissions
    }

    public synchronized void clearCheckedPermissions() {
        mLastGrantedPermissions = new LinkedHashSet<>();
        mLastDeniedPermissions = new LinkedHashSet<>();
        mCheckedPermissions = new LinkedHashSet<>();
    }

    /**
     * @return false if at least one permission is not granted, true otherwise
     */
    public synchronized boolean checkAppPermissions() {
        checkReleased();
        boolean has = true;
        clearCheckedPermissions();
        for (String permission : mPermissionsRequestCodes.keySet()) {
            if (PermissionUtils.has(mActivity, permission)) {
                handlePermissionGranted(permission, false);
            } else {
                handlePermissionDenied(permission, false);
                has = false;
            }
            mCheckedPermissions.add(permission);
        }
        for (String special : mSpecialPermissions) {
            if (isSpecial(special)) {
                if (PermissionUtils.PERMISSION_WRITE_SETTINGS.equals(special)) {
                    if (PermissionUtils.hasCanWriteSettingsPermission(mActivity)) {
                        handlePermissionGranted(special, false);
                    } else {
                        handlePermissionDenied(special, false);
                        has = false;
                    }
                }
            } else {
                throw new RuntimeException("unknown special permission: " + special);
            }
        }
        return has;
    }

    /**
     * @return false if at least one system dialog was not shown on missing permission, true if all dialogs were shown
     */
    public boolean requestAppPermissions() {
        return requestAppPermissions(true, mPermissionsRequestCodes.entrySet());
    }


    private synchronized boolean requestAppPermissions(boolean clear, @NotNull Set<Map.Entry<String, Integer>> permissionsRequestCodes) {
        checkReleased();
        if (clear) {
            clearCheckedPermissions();
            mDialogShowObservable.dispatchDismissAllDialogs();
        }
        boolean result = false;
        boolean systemDialogShowed = false;
        for (Map.Entry<String, Integer> entry : permissionsRequestCodes) {
            String permission = entry.getKey();
            boolean has = PermissionUtils.has(mActivity, permission);
            PermissionUtils.PermissionResponse response = !has && (!systemDialogShowed || mShowAllSystemDialogs) ?
                    PermissionUtils.requestRuntimePermission(mActivity, permission, entry.getValue()) :
                    (has ? new PermissionUtils.PermissionResponse(permission, entry.getValue(), true, true) : null);
            if (response != null) {
                result = true;
                if (!response.hasPermission && !response.isDialogShown) {
                    result = false;
                    handlePermissionDenied(permission, !shouldIgnorePermission(permission, true));
                } else {
                    if (!response.hasPermission) {
                        systemDialogShowed = true;
                        result = false;
                    } else {
                        handlePermissionGranted(permission, !shouldIgnorePermission(permission, true));
                    }
                }
                mCheckedPermissions.add(permission);
            }
        }
        for (String special : mSpecialPermissions) {
            if (isSpecial(special)) {
                PermissionUtils.requestCanWriteSettingsPermission(mActivity);
            } else {
                throw new RuntimeException("unknown special permission: " + special);
            }
        }
        return result;
    }

    private synchronized void handlePermissionGranted(String permission, boolean showDialog) {
        if (!mPermissionsRequestCodes.containsKey(permission)) {
            throw new IllegalArgumentException("no such permission: " + permission);
        }
        mLastGrantedPermissions.add(permission);
        mLastDeniedPermissions.remove(permission);
        if (showDialog) {
            showGrantedDialog(permission);
        }
    }

    private synchronized void handlePermissionDenied(String permission, boolean showDialog) {
        if (!mPermissionsRequestCodes.containsKey(permission)) {
            throw new IllegalArgumentException("no such permission: " + permission);
        }
        mLastDeniedPermissions.add(permission);
        mLastGrantedPermissions.remove(permission);
        if (showDialog) {
            showDeniedDialog(permission);
        }
    }

    private static boolean isSpecial(String permission) {
        return sSpecialSystemPermissions.contains(permission);
    }

    private static void clearSet(@Nullable Set<?> set) {
        if (set != null) {
            Iterator<?> it = set.iterator();
            while (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
    }

    public interface OnDialogShowListener {

        void onDismissAllDialogs();

        void onBeforeGrantedDialogShow(@Nullable Dialog dialog, String permission);

        void onBeforeDeniedDialogShow(@Nullable Dialog dialog, String permission);
    }

    private class OnDialogShowObservable extends Observable<OnDialogShowListener> {

        void dispatchDismissAllDialogs() {
            synchronized (mObservers) {
                for (OnDialogShowListener l : mObservers) {
                    l.onDismissAllDialogs();
                }
            }
        }

        void dispatchBeforeGrantedDialogShow(String permission) {
            synchronized (mObservers) {
                for (OnDialogShowListener l : mObservers) {
                    l.onBeforeGrantedDialogShow(mGrantedDialog, permission);
                }
            }
        }

        void dispatchBeforeDeniedDialogShow(String permission) {
            synchronized (mObservers) {
                for (OnDialogShowListener l : mObservers) {
                    l.onBeforeDeniedDialogShow(mDeniedDialog, permission);
                }
            }
        }
    }

}
