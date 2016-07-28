package ru.maxsmr.permissionchecker;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

public final class PermissionUtils {

    public PermissionUtils() {
        throw new AssertionError("no instances.");
    }

    public static boolean has(@NonNull Activity activity, String permission) {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED;
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

    @NonNull
    public static PermissionResponse requestRuntimePermission(@NonNull Activity activity, String permission, int requestCode) {
        if (!has(activity, permission)) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
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
