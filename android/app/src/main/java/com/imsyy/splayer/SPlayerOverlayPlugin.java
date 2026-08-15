package com.imsyy.splayer;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "SPlayerOverlay")
public class SPlayerOverlayPlugin extends Plugin {
    private TextView overlayView;
    private WindowManager windowManager;

    @PluginMethod
    public void checkPermission(PluginCall call) {
        JSObject result = new JSObject();
        result.put("granted", canDrawOverlays());
        call.resolve(result);
    }

    @PluginMethod
    public void requestPermission(PluginCall call) {
        if (!canDrawOverlays() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getContext().getPackageName())
            );
            getContext().startActivity(intent);
        }
        JSObject result = new JSObject();
        result.put("granted", canDrawOverlays());
        call.resolve(result);
    }

    @PluginMethod
    public void show(PluginCall call) {
        if (!canDrawOverlays()) {
            call.reject("Overlay permission is not granted");
            return;
        }
        ensureOverlayView();
        updateText(call);
        call.resolve();
    }

    @PluginMethod
    public void update(PluginCall call) {
        if (overlayView == null) {
            show(call);
            return;
        }
        updateText(call);
        call.resolve();
    }

    @PluginMethod
    public void hide(PluginCall call) {
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
        call.resolve();
    }

    @Override
    protected void handleOnDestroy() {
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
        super.handleOnDestroy();
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(getContext());
    }

    private void ensureOverlayView() {
        if (overlayView != null) return;
        windowManager = (WindowManager) getContext().getSystemService(WindowManager.class);
        overlayView = new TextView(getContext());
        overlayView.setTextColor(Color.WHITE);
        overlayView.setTextSize(16);
        overlayView.setPadding(28, 18, 28, 18);
        overlayView.setBackgroundColor(0xCC202020);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = 96;
        windowManager.addView(overlayView, params);
    }

    private void updateText(PluginCall call) {
        if (overlayView == null) return;
        String title = call.getString("title", "");
        String artist = call.getString("artist", "");
        String lyric = call.getString("lyric", "");
        overlayView.setText(title + "\n" + artist + "\n" + lyric);
    }
}
