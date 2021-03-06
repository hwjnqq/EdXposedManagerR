package org.meowcat.edxposed.manager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.topjohnwu.superuser.Shell;

import org.meowcat.edxposed.manager.util.NavUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

@SuppressLint("Registered")
public class BaseActivity extends AppCompatActivity {

    private static final String THEME_DEFAULT = "DEFAULT";
    private static final String THEME_BLACK = "BLACK";
    private String mTheme;

    public static boolean isBlackNightTheme() {
        return XposedApp.getPreferences().getBoolean("black_dark_theme", false);
    }

    public static String getTheme(Context context) {
        if (isBlackNightTheme()
                && isNightMode(context.getResources().getConfiguration()))
            return THEME_BLACK;

        return THEME_DEFAULT;
    }

    @StyleRes
    public static int getThemeStyleRes(Context context) {
        switch (getTheme(context)) {
            case THEME_BLACK:
                return R.style.ThemeOverlay_Black;
            case THEME_DEFAULT:
            default:
                return R.style.ThemeOverlay;
        }
    }

    public static boolean isNightMode(Configuration configuration) {
        return (configuration.uiMode & Configuration.UI_MODE_NIGHT_YES) > 0;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(XposedApp.getPreferences().getInt("theme", 0));
        mTheme = getTheme(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Objects.equals(mTheme, getTheme(this))) {
            recreate();
        }
    }

    @Override
    protected void onApplyThemeResource(Resources.Theme theme, int resid, boolean first) {
        // apply real style and our custom style
        if (getParent() == null) {
            theme.applyStyle(resid, true);
        } else {
            try {
                theme.setTo(getParent().getTheme());
            } catch (Exception e) {
                // Empty
            }
            theme.applyStyle(resid, false);
        }
        theme.applyStyle(getThemeStyleRes(this), true);
        // only pass theme style to super, so styled theme will not be overwritten
        super.onApplyThemeResource(theme, R.style.ThemeOverlay, first);
    }

    private void areYouSure(int contentTextId, DialogInterface.OnClickListener listener) {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.areyousure)
                .setMessage(contentTextId)
                .setPositiveButton(android.R.string.yes, listener)
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    void softReboot() {
        if (startShell())
            return;

        List<String> messages = new LinkedList<>();
        Shell.Result result = Shell.su("setprop ctl.restart surfaceflinger; setprop ctl.restart zygote").exec();
        if (result.getCode() != 0) {
            messages.add(result.getOut().toString());
            messages.add("");
            messages.add(getString(R.string.reboot_failed));
            showAlert(TextUtils.join("\n", messages).trim());
        }
    }

    private boolean startShell() {
        if (Shell.rootAccess())
            return false;

        showAlert(getString(R.string.root_failed));
        return true;
    }

    void showAlert(final String result) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> showAlert(result));
            return;
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setMessage(result).setPositiveButton(android.R.string.ok, null).create();
        dialog.show();

        TextView txtMessage = dialog
                .findViewById(android.R.id.message);
        try {
            txtMessage.setTextSize(14);
        } catch (NullPointerException ignored) {
        }
    }

    void reboot(String mode) {
        if (startShell())
            return;

        List<String> messages = new LinkedList<>();

        String command = "/system/bin/svc power reboot";
        if (mode != null) {
            command += " " + mode;
            if (mode.equals("recovery"))
                // create a flag used by some kernels to boot into recovery
                Shell.su("touch /cache/recovery/boot").exec();
        }
        Shell.Result result = Shell.su(command).exec();
        if (result.getCode() != 0) {
            messages.add(result.getOut().toString());
            messages.add("");
            messages.add(getString(R.string.reboot_failed));
            showAlert(TextUtils.join("\n", messages).trim());
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.dexopt_all:
                areYouSure(R.string.take_while_cannot_resore, (dialog, which) -> {
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle(R.string.dexopt_now)
                                    .setMessage(R.string.this_may_take_a_while)
                                    .setCancelable(false)
                                    .show();
                            new Thread("dexopt") {
                                @Override
                                public void run() {
                                    if (!Shell.rootAccess()) {
                                        dialog.dismiss();
                                        NavUtil.showMessage(BaseActivity.this, getString(R.string.root_failed));
                                        return;
                                    }
                                    Shell.su("cmd package bg-dexopt-job").exec();

                                    dialog.dismiss();
                                    XposedApp.runOnUiThread(() -> Toast.makeText(BaseActivity.this, R.string.done, Toast.LENGTH_LONG).show());
                                }
                            }.start();
                        }
                );

                break;
            case R.id.speed_all:
                areYouSure(R.string.take_while_cannot_resore, (dialog, which) -> {

                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.dexopt_now)
                            .setMessage(R.string.this_may_take_a_while)
                            .setCancelable(false)
                            .show();
                    new Thread("dex2oat") {
                        @Override
                        public void run() {
                            if (!Shell.rootAccess()) {
                                dialog.dismiss();
                                NavUtil.showMessage(BaseActivity.this, getString(R.string.root_failed));
                                return;
                            }

                            Shell.su("cmd package compile -m speed -a").exec();

                            dialog.dismiss();
                            XposedApp.runOnUiThread(() -> Toast.makeText(BaseActivity.this, R.string.done, Toast.LENGTH_LONG).show());
                        }

                    };
                });
                break;
            case R.id.reboot:
                if (XposedApp.getPreferences().getBoolean("confirm_reboots", true)) {
                    areYouSure(R.string.reboot, (dialog, which) -> reboot(null));
                } else {
                    reboot(null);
                }
                break;
            case R.id.soft_reboot:
                if (XposedApp.getPreferences().getBoolean("confirm_reboots", true)) {
                    areYouSure(R.string.soft_reboot, (dialog, which) -> softReboot());
                } else {
                    softReboot();
                }
                break;
            case R.id.reboot_recovery:
                if (XposedApp.getPreferences().getBoolean("confirm_reboots", true)) {
                    areYouSure(R.string.reboot_recovery, (dialog, which) -> reboot("recovery"));
                } else {
                    reboot("recovery");
                }
                break;
            case R.id.reboot_bootloader:
                if (XposedApp.getPreferences().getBoolean("confirm_reboots", true)) {
                    areYouSure(R.string.reboot_bootloader, (dialog, which) -> reboot("bootloader"));
                } else {
                    reboot("bootloader");
                }
                break;
            case R.id.reboot_download:
                if (XposedApp.getPreferences().getBoolean("confirm_reboots", true)) {
                    areYouSure(R.string.reboot_download, (dialog, which) -> reboot("download"));
                } else {
                    reboot("download");
                }
                break;
            case R.id.reboot_edl:
                if (XposedApp.getPreferences().getBoolean("confirm_reboots", true)) {
                    areYouSure(R.string.reboot_download, (dialog, which) -> reboot("edl"));
                } else {
                    reboot("edl");
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }
}
