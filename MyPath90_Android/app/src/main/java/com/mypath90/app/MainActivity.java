package com.mypath90.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.webkit.JavascriptInterface;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> upload;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.addJavascriptInterface(new FoodBridge(), "FoodAI");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                upload = cb;
                Intent i = p.createIntent();
                startActivityForResult(i, 12);
                return true;
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }

    private class FoodBridge {
        @JavascriptInterface public void analyzeImage(String dataUrl) {
            try {
                String encoded = dataUrl.substring(dataUrl.indexOf(",") + 1);
                byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                int sample = 1;
                while (opts.outWidth / sample > 1200 || opts.outHeight / sample > 1200) sample *= 2;
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = sample;
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                if (bitmap == null) throw new Exception("Не удалось прочитать изображение");
                InputImage image = InputImage.fromBitmap(bitmap, 0);
                ImageLabeling.getClient(new ImageLabelerOptions.Builder()
                    .setConfidenceThreshold(0.45f).build())
                    .process(image)
                    .addOnSuccessListener(labels -> {
                        JSONArray arr = new JSONArray();
                        for (ImageLabel label : labels) {
                            JSONObject item = new JSONObject();
                            try {
                                item.put("text", label.getText());
                                item.put("confidence", Math.round(label.getConfidence() * 100));
                                arr.put(item);
                            } catch (Exception ignored) {}
                        }
                        final String json = arr.toString();
                        runOnUiThread(() -> web.evaluateJavascript("window.onFoodRecognized(" + JSONObject.quote(json) + ")", null));
                        bitmap.recycle();
                    })
                    .addOnFailureListener(e -> {
                        String msg = e.getMessage() == null ? "Не удалось распознать фото" : e.getMessage();
                        runOnUiThread(() -> web.evaluateJavascript("window.onFoodRecognitionError(" + JSONObject.quote(msg) + ")", null));
                        bitmap.recycle();
                    });
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "Ошибка обработки фото" : e.getMessage();
                runOnUiThread(() -> web.evaluateJavascript("window.onFoodRecognitionError(" + JSONObject.quote(msg) + ")", null));
            }
        }
    }

    @Override protected void onActivityResult(int r, int c, Intent d) {
        super.onActivityResult(r, c, d);
        if (r == 12 && upload != null) {
            upload.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(c, d));
            upload = null;
        }
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
