package net.maxsmr.permissionchecker;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class PermissionUtils {

    public static final String PERMISSION_WRITE_SETTINGS = "android.permission.WRITE_SETTINGS";

    public PermissionUtils() {
        throw new AssertionError("no instances.");
    }

    public static boolean has(@NotNull Context context, @Nullable String permission) {
        return permission != null && ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasPermissions(@NotNull Context context, @Nullable Set<String> permissions, boolean has) {
        return hasPermissionsWithNames(context, permissions, has).size() == (permissions != null? permissions.size() : 0);
    }

    @NotNull
    public static Set<String> hasPermissionsWithNames(@NotNull Context context, @Nullable Set<String> permissions, boolean has) {
        Set<String> result = new LinkedHashSet<>();
        if (permissions != null) {
            for (String p : permissions) {
                if (!TextUtils.isEmpty(p)) {
                    if (PermissionUtils.has(context, p) == has) {
                        result.add(p);
                    }
                }
            }
        }
        return result;
    }

    public static boolean hasCanWriteSettingsPermission(@NotNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return android.provider.Settings.System.canWrite(context);
        }
        return true;
    }

    @NotNull
    public static Set<String> getPermissionNames(@Nullable Collection<PermissionResponse> responses) {
        Set<String> permissions = new LinkedHashSet<>();
        if (responses != null) {
            for (PermissionResponse r : responses) {
                if (r != null) {
                    permissions.add(r.permission);
                }
            }
        }
        return permissions;
    }

    @NotNull
    public static Set<PermissionResponse> filterPermissions(@Nullable Collection<PermissionResponse> responses, boolean isGranted, boolean isDialogShown) {
        Set<PermissionResponse> filteredPermissions = new LinkedHashSet<>();
        if (responses != null) {
            for (PermissionResponse response : responses) {
                if (response != null && response.hasPermission == isGranted && response.isDialogShown == isDialogShown) {
                    filteredPermissions.add(response);
                }
            }
        }
        return filteredPermissions;
    }

    public static boolean hasNonGrantedPermissions(@Nullable Collection<PermissionResponse> responses) {
        return !getNonGrantedPermissions(responses).isEmpty();
    }

    @NotNull
    public static Set<PermissionResponse> getNonGrantedPermissions(@Nullable Collection<PermissionResponse> responses) {
        return filterPermissions(responses, false, false);
    }

    public static boolean hasUnhandledPermissions(@Nullable Collection<PermissionResponse> responses) {
        return !getUnhandledPermissions(responses).isEmpty();
    }

    @NotNull
    public static Set<PermissionResponse> getUnhandledPermissions(@Nullable Collection<PermissionResponse> responses) {
        return filterPermissions(responses, false, true);
    }

    public static boolean isPermissionGranted(int requestCode, int requiringRequestCode, int[] grantResults, int permissionIndex) {

        if (permissionIndex < 0) {
            throw new IndexOutOfBoundsException("incorrect permissionIndex: " + permissionIndex);
        }

        boolean result = false;
        if (requestCode == requiringRequestCode) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                result = true;
            }
        }
        return result;
    }

    /** one request code - single permission */
    @NotNull
    public static PermissionResponse requestRuntimePermission(@NotNull Activity activity, String permission, int requestCode) {
        if (!has(activity, permission)) {
            if (!TextUtils.isEmpty(permission) && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{permission},
                        requestCode);
                return new PermissionResponse(permission, requestCode, false, true);
            } else {
                return new PermissionResponse(permission, requestCode, false, false);
            }
        } else {
            return new PermissionResponse(permission, requestCode, true, false);
        }
    }

    /** one request code - multiple permissions */
    @NotNull
    public static Map<String, PermissionResponse> requestRuntimePermissions(@NotNull Activity activity, Collection<String> permissions, int requestCode) {
        Map<String, PermissionUtils.PermissionResponse> responseMap = new LinkedHashMap<>();
        if (permissions != null) {
            for (String permission : permissions) {
                if (!TextUtils.isEmpty(permission)) {
                    if (!has(activity, permission)) {
                        if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                            responseMap.put(permission, new PermissionUtils.PermissionResponse(permission, requestCode, false, true));
                        } else {
                            responseMap.put(permission, new PermissionUtils.PermissionResponse(permission, requestCode, false, false));
                        }
                    } else {
                        responseMap.put(permission, new PermissionUtils.PermissionResponse(permission, requestCode, true, false));
                    }
                }
            }
        }
        Set<String> permissionsToRequest = new LinkedHashSet<>();
        for (PermissionUtils.PermissionResponse response : responseMap.values()) {
            if (!response.hasPermission && response.isDialogShown) {
                permissionsToRequest.add(response.permission);
            }
        }
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(activity,
                    permissionsToRequest.toArray(new String[0]),
                    requestCode);
        }
        return responseMap;
    }

    public static void requestCanWriteSettingsPermission(@NotNull Context context) {
        if (!hasCanWriteSettingsPermission(context)) {
            PackageHelper.openAppManageSettingsScreen(context);
        }
    }
    public static boolean requestPermissions(@NotNull Activity activity, int requestCode, @Nullable PermissionsRequestCallback callback, String... permissions) {
        final Set<String> permissionsSet = permissions != null? new LinkedHashSet<>(Arrays.asList(permissions)) : Collections.emptySet();
        final Map<String, PermissionResponse> responses = requestRuntimePermissions(activity, permissionsSet, requestCode);
        final Set<PermissionResponse> notHandledPermissions = getUnhandledPermissions(responses.values());
        final boolean hasNotHandledPermissions = !notHandledPermissions.isEmpty();
        final Set<PermissionResponse> notGrantedPermissions = getNonGrantedPermissions(responses.values());
        if ((!hasNotHandledPermissions && notGrantedPermissions.isEmpty())) {
            if (callback != null) {
                return callback.onPermissionsGranted(permissionsSet);
            }
            return true;
        } else if (!hasNotHandledPermissions) {
            if (callback != null) {
                callback.onPermissionsNotGranted(PermissionUtils.getPermissionNames(notGrantedPermissions));
            }
        } else {
            if (callback != null) {
                callback.onPermissionsNotHandled(PermissionUtils.getPermissionNames(notHandledPermissions));
            }
        }
        return false;
    }

    public interface PermissionsRequestCallback {

        boolean onPermissionsGranted(@NotNull Set<String> permissions);

        void onPermissionsNotGranted(@NotNull Set<String> permissions);

        void onPermissionsNotHandled(@NotNull Set<String> permissions);
    }

    static int generateRequestCode(Collection<Integer> usedCodes) {
        int newCode = 1;
        if (usedCodes != null && !usedCodes.isEmpty()) {
            List<Integer> usedCodesCopy = new ArrayList<>(usedCodes);
            for (int i = 0; i < usedCodesCopy.size(); i++) {
                Integer code = usedCodesCopy.get(i);
                if (code != null && code == newCode) {
                    newCode = randInt(1, Byte.MAX_VALUE * 2);
                    i = 0;
                }
            }
        }
        return newCode;
    }

    private static int randInt(int min, int max) {
        Random rand = new Random();
        return rand.nextInt((max - min) + 1) + min;
    }

    public static class PermissionResponse {

        public final String permission;
        public final int requestCode;

        public final boolean hasPermission, isDialogShown;

        public PermissionResponse(String permission, int requestCode, boolean hasPermission, boolean isDialogShown) {
            this.permission = permission;
            this.requestCode = requestCode;
            this.hasPermission = hasPermission;
            this.isDialogShown = isDialogShown;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (object == null || getClass() != object.getClass()) return false;

            PermissionResponse that = (PermissionResponse) object;

            if (requestCode != that.requestCode) return false;
            if (hasPermission != that.hasPermission) return false;
            if (isDialogShown != that.isDialogShown) return false;
            return permission != null ? permission.equals(that.permission) : that.permission == null;

        }

        @Override
        public int hashCode() {
            int result = permission != null ? permission.hashCode() : 0;
            result = 31 * result + requestCode;
            result = 31 * result + (hasPermission ? 1 : 0);
            result = 31 * result + (isDialogShown ? 1 : 0);
            return result;
        }

        @NotNull
        @Override
        public String toString() {
            return "PermissionResponse{" +
                    "permission='" + permission + '\'' +
                    ", requestCode=" + requestCode +
                    ", hasPermission=" + hasPermission +
                    ", isDialogShown=" + isDialogShown +
                    '}';
        }
    }
}
