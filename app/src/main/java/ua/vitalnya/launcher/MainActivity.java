package ua.vitalnya.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.provider.Settings;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private WebView web;
    private WebView mapView;          // вбудована онлайн-мапа (map.ukrainealarm.com)
    private FrameLayout root;
    private String mapUrl = null;
    private long mapLoadedAt = 0;
    private SharedPreferences prefs;
    private final Map<String, String[]> iconCache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs = getSharedPreferences("vitalnya", Context.MODE_PRIVATE);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0c0d10"));
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        hideSystemUi();
        web.loadUrl("file:///android_asset/index.html");
        web.requestFocus();
    }

    private void ensureMapView() {
        if (mapView != null) return;
        mapView = new WebView(this);
        mapView.setBackgroundColor(Color.parseColor("#1b1d22"));
        mapView.setFocusable(false);
        mapView.setFocusableInTouchMode(false);
        mapView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        WebSettings ms = mapView.getSettings();
        ms.setJavaScriptEnabled(true);
        ms.setDomStorageEnabled(true);
        ms.setUseWideViewPort(true);
        ms.setLoadWithOverviewMode(true);
        ms.setMediaPlaybackRequiresUserGesture(true);
        // десктопна версія сайту — повна мапа без мобільного меню
        ms.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
        mapView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                String js = readAsset("mapfix.js");
                if (js != null) view.evaluateJavascript(js, null);
            }
        });
        mapView.setVisibility(View.GONE);
        root.addView(mapView, new FrameLayout.LayoutParams(10, 10));
    }

    private String mapFixJs = null;

    private String readAsset(String name) {
        if (mapFixJs != null) return mapFixJs;
        try {
            InputStream in = getAssets().open(name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            mapFixJs = out.toString("UTF-8");
        } catch (Throwable t) {
            mapFixJs = null;
        }
        return mapFixJs;
    }

    /** x,y,w,h — у CSS-пікселях сторінки лаунчера */
    private void placeMap(String url, float x, float y, float w, float h) {
        ensureMapView();
        float d = getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(w * d), Math.round(h * d));
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = Math.round(x * d);
        lp.topMargin = Math.round(y * d);
        mapView.setLayoutParams(lp);
        mapView.setVisibility(View.VISIBLE);
        long now = System.currentTimeMillis();
        // перезавантажуємо сторінку раз на 10 хв, щоб не накопичувала памʼять
        if (!url.equals(mapUrl) || now - mapLoadedAt > 10 * 60 * 1000) {
            mapUrl = url;
            mapLoadedAt = now;
            mapView.loadUrl(url);
        }
        web.requestFocus();
    }

    private void hideSystemUi() {
        web.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (web != null) {
            web.onResume();
            web.evaluateJavascript("window.onTvResume&&window.onTvResume()", null);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // кнопка Home, коли лаунчер вже відкритий — закрити оверлеї
        if (web != null) web.evaluateJavascript("window.onTvBack&&window.onTvBack()", null);
    }

    @Override
    public void onBackPressed() {
        // лаунчер не закривається кнопкою «Назад»
        if (web != null) web.evaluateJavascript("window.onTvBack&&window.onTvBack()", null);
    }

    private static String drawableToDataUri(Drawable d, int maxW, int maxH) {
        if (d == null) return null;
        try {
            Bitmap bmp;
            if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
                bmp = ((BitmapDrawable) d).getBitmap();
            } else {
                int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : maxW;
                int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : maxH;
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bmp);
                d.setBounds(0, 0, w, h);
                d.draw(c);
            }
            float scale = Math.min(1f, Math.min((float) maxW / bmp.getWidth(), (float) maxH / bmp.getHeight()));
            if (scale < 1f) {
                bmp = Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(bmp.getWidth() * scale)),
                        Math.max(1, Math.round(bmp.getHeight() * scale)), true);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable t) {
            return null;
        }
    }

    private class Bridge {

        @JavascriptInterface
        public String getApps() {
            PackageManager pm = getPackageManager();
            Set<String> seen = new HashSet<>();
            JSONArray arr = new JSONArray();
            String[] cats = {"android.intent.category.LEANBACK_LAUNCHER", Intent.CATEGORY_LAUNCHER};
            for (String cat : cats) {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(cat);
                List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
                for (ResolveInfo ri : list) {
                    String pkg = ri.activityInfo.packageName;
                    if (pkg.equals(getPackageName()) || seen.contains(pkg)) continue;
                    seen.add(pkg);
                    try {
                        JSONObject o = new JSONObject();
                        o.put("pkg", pkg);
                        o.put("label", String.valueOf(ri.loadLabel(pm)));
                        String[] imgs = iconCache.get(pkg);
                        if (imgs == null) {
                            String banner = null;
                            try {
                                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                                Drawable b = ri.activityInfo.loadBanner(pm);
                                if (b == null) b = ai.loadBanner(pm);
                                banner = drawableToDataUri(b, 320, 180);
                            } catch (Throwable ignored) { }
                            String icon = drawableToDataUri(ri.loadIcon(pm), 128, 128);
                            imgs = new String[]{banner, icon};
                            iconCache.put(pkg, imgs);
                        }
                        if (imgs[0] != null) o.put("banner", imgs[0]);
                        if (imgs[1] != null) o.put("icon", imgs[1]);
                        arr.put(o);
                    } catch (Throwable ignored) { }
                }
            }
            return arr.toString();
        }

        @JavascriptInterface
        public void launch(final String pkg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    PackageManager pm = getPackageManager();
                    Intent i = null;
                    if (Build.VERSION.SDK_INT >= 21) i = pm.getLeanbackLaunchIntentForPackage(pkg);
                    if (i == null) i = pm.getLaunchIntentForPackage(pkg);
                    if (i == null) {
                        Toast.makeText(MainActivity.this, "Не вдалося запустити " + pkg, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(i);
                    } catch (Throwable t) {
                        Toast.makeText(MainActivity.this, "Помилка запуску", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void openSettings() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Throwable ignored) { }
                }
            });
        }

        @JavascriptInterface
        public boolean isDefaultHome() {
            try {
                Intent h = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
                ResolveInfo ri = getPackageManager().resolveActivity(h, PackageManager.MATCH_DEFAULT_ONLY);
                return ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
            } catch (Throwable t) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean canAutostart() {
            if (Build.VERSION.SDK_INT < 29) return true;          // до Android 10 обмежень немає
            return Settings.canDrawOverlays(MainActivity.this);   // Android 10+: потрібен дозвіл «поверх інших вікон»
        }

        /** Скидає вибір головного екрана і показує системне вікно «Який лаунчер використовувати». */
        @JavascriptInterface
        public void chooseHome() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final PackageManager pm = getPackageManager();
                    final ComponentName fake = new ComponentName(MainActivity.this, FakeHome.class);
                    try {
                        pm.setComponentEnabledSetting(fake, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                        Intent h = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(h);
                    } catch (Throwable ignored) { }
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                pm.setComponentEnabledSetting(fake, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                            } catch (Throwable ignored) { }
                        }
                    }, 1500);
                }
            });
        }

        @JavascriptInterface
        public void openHomeSettings() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String[] actions = {Settings.ACTION_HOME_SETTINGS, "android.settings.MANAGE_DEFAULT_APPS_SETTINGS", Settings.ACTION_APPLICATION_SETTINGS};
                    for (String a : actions) {
                        try {
                            startActivity(new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            return;
                        } catch (Throwable ignored) { }
                    }
                }
            });
        }

        @JavascriptInterface
        public void openAutostartSettings() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        return;
                    } catch (Throwable ignored) { }
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Throwable ignored) { }
                }
            });
        }

        @JavascriptInterface
        public void showMap(final String url, final float x, final float y, final float w, final float h) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    placeMap(url, x, y, w, h);
                }
            });
        }

        @JavascriptInterface
        public void hideMap() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mapView != null) mapView.setVisibility(View.GONE);
                }
            });
        }

        @JavascriptInterface
        public void reloadMap() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mapView != null && mapUrl != null) {
                        mapLoadedAt = System.currentTimeMillis();
                        mapView.loadUrl(mapUrl);
                    }
                }
            });
        }

        @JavascriptInterface
        public String load(String key) {
            return prefs.getString(key, "");
        }

        @JavascriptInterface
        public void save(String key, String value) {
            prefs.edit().putString(key, value).apply();
        }

        @JavascriptInterface
        public void httpGet(final String url, final String headersJson, final int id) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int code = 0;
                    String body = "";
                    HttpURLConnection c = null;
                    try {
                        c = (HttpURLConnection) new URL(url).openConnection();
                        c.setConnectTimeout(10000);
                        c.setReadTimeout(15000);
                        c.setRequestProperty("User-Agent", "Vitalnya-Launcher/1.0 (Android TV)");
                        c.setRequestProperty("Accept", "application/json");
                        JSONObject h = new JSONObject(headersJson == null || headersJson.isEmpty() ? "{}" : headersJson);
                        Iterator<String> keys = h.keys();
                        while (keys.hasNext()) {
                            String k = keys.next();
                            c.setRequestProperty(k, h.getString(k));
                        }
                        code = c.getResponseCode();
                        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                        if (in != null) {
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                            in.close();
                            body = out.toString("UTF-8");
                        }
                    } catch (Throwable t) {
                        code = 0;
                        // текст помилки — щоб на екрані було видно справжню причину
                        String m = t.getMessage();
                        body = t.getClass().getSimpleName() + (m != null ? ": " + m : "");
                    } finally {
                        if (c != null) c.disconnect();
                    }
                    final String js = "window.__httpCb&&window.__httpCb(" + id + "," + code + "," + JSONObject.quote(body) + ")";
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (web != null) web.evaluateJavascript(js, null);
                        }
                    });
                }
            }).start();
        }
    }
}
