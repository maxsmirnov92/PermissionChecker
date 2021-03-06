package net.maxsmr.permissionchecker;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PackageHelper {

    private PackageHelper() {
        throw new AssertionError("no instances.");
    }

    @NotNull
    public static List<String> getPermissionsForPackage(@NotNull Context context, String packageName) {
        try {
            return getPermissionsForPackageInfo(context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_PERMISSIONS));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @NotNull
    public static List<String> getPermissionsForArchivePackage(@NotNull Context context, String archivePath) {
        try {
            return getPermissionsForPackageInfo(context.getPackageManager().getPackageArchiveInfo(archivePath, PackageManager.GET_PERMISSIONS));
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @NotNull
    private static List<String> getPermissionsForPackageInfo(@NotNull PackageInfo packageInfo) {
        List<String> permissions = new ArrayList<>();
        if (packageInfo.requestedPermissions != null) {
            return new ArrayList<>(Arrays.asList(packageInfo.requestedPermissions));
        }
        return permissions;
    }

    public static void openAppSettingsScreen(@NotNull Context context) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(Uri.fromParts("package", context.getPackageName(), null));

        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
    }

    public static void openAppManageSettingsScreen(@NotNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.startActivity(
                    new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                            .setData(Uri.parse("package:" + context.getPackageName()))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }
}
