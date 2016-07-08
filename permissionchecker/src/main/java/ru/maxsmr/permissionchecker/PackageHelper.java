package ru.maxsmr.permissionchecker;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PackageHelper {

    private PackageHelper() {
        throw new AssertionError("no instances.");
    }

    @NonNull
    public static List<String> getPermissionsForPackage(@NonNull Context context, String packageName) {
        try {
            return getPermissionsForPackageInfo(context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_PERMISSIONS));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @NonNull
    public static List<String> getPermissionsForArchivePackage(@NonNull Context context, String archivePath) {
        try {
            return getPermissionsForPackageInfo(context.getPackageManager().getPackageArchiveInfo(archivePath, PackageManager.GET_PERMISSIONS));
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @NonNull
    private static List<String> getPermissionsForPackageInfo(@NonNull PackageInfo packageInfo) {
        List<String> permissions = new ArrayList<>();
        if (packageInfo.requestedPermissions != null) {
            return new ArrayList<>(Arrays.asList(packageInfo.requestedPermissions));
        }
        return permissions;
    }

}
