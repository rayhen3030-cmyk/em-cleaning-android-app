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
         * Verbindung:
         *
         * HTML / JavaScript
         * ->
         * Android Java
         */

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );


        /*
         * WICHTIG:
         *
         * Wenn Android die Activity nur neu erstellt,
         * wird der WebView-Zustand wiederhergestellt.
         *
         * Dadurch landet die App nicht immer wieder
         * auf der Hauptseite.
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
     * JAVASCRIPT -> ANDROID
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
                             * Vor Öffnen der Kamera:
                             *
                             * Fenster-Aufmaß-Seite merken.
                             */

                            webView.evaluateJavascript(
                                    "if(typeof openPage==='function'){openPage('foto');}",
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
                                    "if(typeof openPage==='function'){openPage('foto');}",
                                    null
                            );
                        }
                    }
            );
        }
    }


    /*
     * ERGEBNISSE VON
     *
     * FOTOAUSWAHL
     * ODER
     * AR-MESSUNG
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
         * DATEIAUSWAHL
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


            filePathCallback
                    .onReceiveValue(
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
             * WICHTIG:
             *
             * Egal ob Messung erfolgreich,
             * abgebrochen oder AR einen Fehler hatte:
             *
             * Zurück zur Fenster-Seite.
             */

            webView.post(
                    () -> webView.evaluateJavascript(
                            "if(typeof openPage==='function'){openPage('foto');}",
                            null
                    )
            );


            /*
             * Erfolgreiche Messung
             */

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


                /*
                 * Werte überprüfen
                 */

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


                /*
                 * Ergebnis zurück an index.html
                 */

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

                /*
                 * Messung wurde abgebrochen
                 *
                 * NICHT zurück auf Home.
                 */

                webView.postDelayed(
                        () ->
                                webView.evaluateJavascript(
                                        "if(typeof openPage==='function'){openPage('foto');}",
                                        null
                                ),
                        250
                );
            }
        }
    }


    /*
     * WEBVIEW-ZUSTAND SPEICHERN
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
     * ZURÜCK-TASTE
     */

    @Override
    public void onBackPressed() {

        if (
                webView
                        !=
                null
                &&
                webView.canGoBack()
        ) {

            webView.goBack();

        } else {

            /*
             * Erst versuchen,
             * innerhalb der App auf Home zu gehen.
             */

            webView.evaluateJavascript(
                    "if(typeof openPage==='function'){openPage('home');}",
                    null
            );
        }
    }
}
