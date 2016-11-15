package net.maxsmr.permission.test;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Toast;

import net.maxsmr.permissionchecker.gui.activities.BaseSplashPermissionActivity;

public class TestSplashPermissionsActivity extends BaseSplashPermissionActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
    }

    @Override
    protected boolean isShowingSplashEnabled() {
        return true;
    }

    @Override
    protected boolean isCheckingPermissionsEnabled() {
        return true;
    }

    @Override
    protected boolean isShowingAllSystemDialogsEnabled() {
        return false;
    }

    @Override
    protected boolean isShowingGrantedDialogEnabled(String permission) {
        return false;
    }

    @Override
    protected long getBaseSplashTimeout() {
        return 4000;
    }

    @Override
    protected void doFinalAction() {
        Toast.makeText(this, R.string.text_all_permissions_granted, Toast.LENGTH_SHORT).show();
    }

    @Nullable
    @Override
    protected View getClickableView() {
        return findViewById(R.id.tvMessage);
    }

    @Override
    protected boolean allowRemoveCallbackWhenScreenIsOff() {
        return false;
    }
}
