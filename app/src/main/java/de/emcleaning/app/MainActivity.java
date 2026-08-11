package de.emcleaning.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int AR_MEASURE_REQUEST = 2001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {

                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }

                filePathCallback = callback;

                try {

                    Intent intent = params.createIntent();
                    intent.setType("image/*");

                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST
                    );

                    return true;

                } catch (Exception e) {

                    filePathCallback = null;
                    return false;
                }
            }
        });


        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );


        webView.loadUrl(
                "file:///android_asset/index.html"
        );
    }


    private class AndroidBridge {

        @JavascriptInterface
        public void startArMeasurement() {

            runOnUiThread(() -> {

                try {

                    Intent intent =
                            new Intent(
                                    MainActivity.this,
                                    MeasureActivity.class
                            );

                    startActivityForResult(
                            intent,
                            AR_MEASURE_REQUEST
                    );

                } catch (Exception e) {

                    runOnUiThread(() ->
                            webView.evaluateJavascript(
                                    "alert('AR-Messung konnte nicht geöffnet werden.');",
                                    null
                            )
                    );
                }
            });
        }
    }


    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );


        if (requestCode == FILE_CHOOSER_REQUEST) {

            if (filePathCallback == null) {
                return;
            }

            Uri[] results = null;

            if (resultCode == RESULT_OK) {

                results =
                        WebChromeClient
                                .FileChooserParams
                                .parseResult(
                                        resultCode,
                                        data
                                );
            }

            filePathCallback.onReceiveValue(results);

            filePathCallback = null;

            return;
        }


        if (
                requestCode == AR_MEASURE_REQUEST
                        &&
                resultCode == RESULT_OK
                        &&
                data != null
        ) {

            double width =
                    data.getDoubleExtra(
                            "width",
                            0
                    );

            double height =
                    data.getDoubleExtra(
                            "height",
                            0
                    );

            double area =
                    data.getDoubleExtra(
                            "area",
                            0
                    );


            String javascript =
                    "if(window.receiveArMeasurement){" +
                    "window.receiveArMeasurement(" +
                    width + "," +
                    height + "," +
                    area +
                    ");" +
                    "}";


            webView.evaluateJavascript(
                    javascript,
                    null
            );
        }
    }


    @Override
    public void onBackPressed() {

        if (
                webView != null
                        &&
                webView.canGoBack()
        ) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }
}
