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
import android.widget.Toast;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int AR_MEASURE_REQUEST = 2001;

    private boolean arMeasurementRunning = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);

        setContentView(webView);


        WebSettings settings =
                webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);


        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {

                        super.onPageFinished(
                                view,
                                url
                        );
                    }
                }
        );


        webView.setWebChromeClient(
                new WebChromeClient() {

                    @Override
                    public boolean onShowFileChooser(
                            WebView view,
                            ValueCallback<Uri[]> callback,
                            FileChooserParams params
                    ) {

                        if (filePathCallback != null) {

                            filePathCallback.onReceiveValue(
                                    null
                            );
                        }

                        filePathCallback =
                                callback;


                        try {

                            Intent intent =
                                    params.createIntent();

                            intent.setType(
                                    "image/*"
                            );


                            startActivityForResult(
                                    intent,
                                    FILE_CHOOSER_REQUEST
                            );


                            return true;


                        } catch (
                                Exception e
                        ) {

                            filePathCallback = null;

                            return false;
                        }
                    }
                }
        );


        /*
         * HTML / JAVASCRIPT
         * ->
         * ANDROID
         */

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );


        /*
         * Zustand wiederherstellen
         */

        if (savedInstanceState != null) {

            webView.restoreState(
                    savedInstanceState
            );

        } else {

            webView.loadUrl(
                    "file:///android_asset/index.html"
            );
        }
    }


    /*
     * JAVASCRIPT BRIDGE
     */

    private class AndroidBridge {

        @JavascriptInterface
        public void startArMeasurement() {

            runOnUiThread(
                    () -> {

                        try {

                            arMeasurementRunning =
                                    true;


                            /*
                             * Vor AR sicherstellen,
                             * dass Fenster-Seite aktiv ist.
                             */

                            webView.evaluateJavascript(
                                    "if(typeof openPage==='function'){" +
                                            "openPage('foto');" +
                                            "}",
                                    null
                            );


                            Intent intent =
                                    new Intent(
                                            MainActivity.this,
                                            MeasureActivity.class
                                    );


                            startActivityForResult(
                                    intent,
                                    AR_MEASURE_REQUEST
                            );


                        } catch (
                                Exception e
                        ) {

                            arMeasurementRunning =
                                    false;


                            Toast.makeText(
                                    MainActivity.this,
                                    "AR-Messung konnte nicht geöffnet werden.",
                                    Toast.LENGTH_LONG
                            ).show();


                            webView.evaluateJavascript(
                                    "if(typeof openPage==='function'){" +
                                            "openPage('foto');" +
                                            "}",
                                    null
                            );
                        }
                    }
            );
        }
    }


    /*
     * ERGEBNISSE
     */

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );


        /*
         * FOTO / DATEI
         */

        if (
                requestCode
                        ==
                FILE_CHOOSER_REQUEST
        ) {

            if (
                    filePathCallback
                            ==
                    null
            ) {

                return;
            }


            Uri[] results =
                    null;


            if (
                    resultCode
                            ==
                    RESULT_OK
            ) {

                results =
                        WebChromeClient
                                .FileChooserParams
                                .parseResult(
                                        resultCode,
                                        data
                                );
            }


            filePathCallback.onReceiveValue(
                    results
            );


            filePathCallback =
                    null;


            return;
        }


        /*
         * AR-MESSUNG
         */

        if (
                requestCode
                        ==
                AR_MEASURE_REQUEST
        ) {

            arMeasurementRunning =
                    false;


            /*
             * Immer wieder Fenster-Seite anzeigen
             */

            webView.post(
                    () ->
                            webView.evaluateJavascript(
                                    "if(typeof openPage==='function'){" +
                                            "openPage('foto');" +
                                            "}",
                                    null
                            )
            );


            if (
                    resultCode
                            ==
                    RESULT_OK
                    &&
                    data
                            !=
                    null
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


                if (
                        width <= 0
                        ||
                        height <= 0
                        ||
                        area <= 0
                ) {

                    webView.postDelayed(
                            () ->
                                    webView.evaluateJavascript(
                                            "alert('Die Messung war ungültig. Bitte erneut messen.');",
                                            null
                                    ),
                            300
                    );

                    return;
                }


                String javascript =
                        "if(typeof window.receiveArMeasurement==='function'){" +

                                "window.receiveArMeasurement(" +

                                width +
                                "," +

                                height +
                                "," +

                                area +

                                ");" +

                                "}";


                webView.postDelayed(
                        () ->
                                webView.evaluateJavascript(
                                        javascript,
                                        null
                                ),
                        300
                );


            } else {

                webView.postDelayed(
                        () ->
                                webView.evaluateJavascript(
                                        "if(typeof openPage==='function'){" +
                                                "openPage('foto');" +
                                                "}",
                                        null
                                ),
                        250
                );
            }
        }
    }


    /*
     * ZUSTAND SPEICHERN
     */

    @Override
    protected void onSaveInstanceState(
            Bundle outState
    ) {

        if (
                webView
                        !=
                null
        ) {

            webView.saveState(
                    outState
            );
        }


        super.onSaveInstanceState(
                outState
        );
    }


    /*
     * ANDROID ZURÜCK-TASTE
     */

    @Override
    public void onBackPressed() {

        if (
                webView
                        ==
                null
        ) {

            super.onBackPressed();

            return;
        }


        /*
         * Wenn AR gerade läuft,
         * nicht gleichzeitig WebView ändern.
         */

        if (arMeasurementRunning) {

            super.onBackPressed();

            return;
        }


        /*
         * Aktive App-Seite abfragen
         */

        webView.evaluateJavascript(

                "(function(){" +
                        "var p=document.querySelector('.page.active');" +
                        "return p ? p.id : 'home';" +
                        "})()",

                value -> {

                    String currentPage =
                            "home";


                    if (
                            value
                                    !=
                            null
                    ) {

                        currentPage =
                                value
                                        .replace("\"", "")
                                        .trim();
                    }


                    /*
                     * Wenn wir auf einer Unterseite
                     * der index.html sind:
                     *
                     * -> zurück zum Hauptmenü
                     */

                    if (
                            !currentPage.equals(
                                    "home"
                            )
                    ) {

                        webView.evaluateJavascript(

                                "if(typeof openPage==='function'){" +
                                        "openPage('home');" +
                                        "}",

                                null
                        );

                        return;
                    }


                    /*
                     * Auf Home:
                     *
                     * echte WebView-History nur dann nutzen,
                     * wenn wirklich vorhanden.
                     */

                    if (
                            webView.canGoBack()
                    ) {

                        webView.goBack();

                    } else {

                        /*
                         * App verlassen
                         */

                        MainActivity.super.onBackPressed();
                    }
                }
        );
    }


    /*
     * WebView sauber beenden
     */

    @Override
    protected void onDestroy() {

        if (
                webView
                        !=
                null
        ) {

            webView.removeJavascriptInterface(
                    "Android"
            );

            webView.stopLoading();

            webView.destroy();

            webView = null;
        }

        super.onDestroy();
    }
}
