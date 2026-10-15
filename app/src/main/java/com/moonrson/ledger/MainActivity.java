package com.moonrson.ledger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.util.Base64;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 1001;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage 持久化（账簿的全部数据都在这）
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        // JS桥：网页把导出的文件内容（base64）交给我们存到系统"下载"目录
        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");

        // 文件选择器（账簿"导入备份"用的 <input type=file>）
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "选择备份文件"), FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "没有可用的文件选择器", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url.startsWith("blob:")) {
                // WebView 不认 blob 下载 → 回到页面里用 JS 把 blob 读成 base64，交给桥存盘
                String js = "(function(){try{"
                        + "var u='" + url + "';"
                        + "fetch(u).then(function(r){return r.blob()}).then(function(b){"
                        + "var fr=new FileReader();"
                        + "fr.onload=function(){"
                        + "var d=fr.result.split(',')[1];"
                        + "window.AndroidBridge.saveDownload("
                        + "d,"
                        + "'application/json'"
                        + ");};"
                        + "fr.readAsDataURL(b);"
                        + "})"
                        + "}catch(e){}})()";
                webView.evaluateJavascript(js, null);
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    class Bridge {
        @JavascriptInterface
        public void saveDownload(String base64, String mimeType) {
            try {
                byte[] data = Base64.getDecoder().decode(base64);
                String fileName = "极简账簿-备份-" + System.currentTimeMillis() + ".json";

                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                cv.put(MediaStore.Downloads.MIME_TYPE, mimeType == null ? "application/json" : mimeType);
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new Exception("无法创建下载文件");

                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    os.write(data);
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "已保存到 下载/" + fileName, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
