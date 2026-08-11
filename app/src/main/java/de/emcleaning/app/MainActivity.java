package de.emcleaning.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int AR_MEASURE_REQUEST = 2001;
    private static final int PDF_CREATE_REQUEST = 3001;

    private boolean arMeasurementRunning = false;

    private String pendingPdfEmployee = "";
    private String pendingPdfMonth = "";
    private String pendingPdfJson = "[]";

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
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);

        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(
                new WebChromeClient() {

                    @Override
                    public boolean onShowFileChooser(
                            WebView view,
                            ValueCallback<Uri[]> callback,
                            FileChooserParams params
                    ) {

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
                }
        );

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );

        if (savedInstanceState != null) {

            webView.restoreState(savedInstanceState);

        } else {

            webView.loadUrl(
                    "file:///android_asset/index.html"
            );
        }
    }

    private class AndroidBridge {

        @JavascriptInterface
        public void startArMeasurement() {

            runOnUiThread(() -> {

                try {

                    arMeasurementRunning = true;

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

                    arMeasurementRunning = false;

                    Toast.makeText(
                            MainActivity.this,
                            "AR-Messung konnte nicht geöffnet werden.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        }

        @JavascriptInterface
        public void createTimesheetPdf(
                String employee,
                String month,
                String jsonData
        ) {

            runOnUiThread(() -> {

                pendingPdfEmployee =
                        employee == null
                                ? "Mitarbeiter"
                                : employee;

                pendingPdfMonth =
                        month == null
                                ? ""
                                : month;

                pendingPdfJson =
                        jsonData == null
                                ? "[]"
                                : jsonData;

                String safeEmployee =
                        pendingPdfEmployee.replaceAll(
                                "[^a-zA-Z0-9ÄÖÜäöüß_-]",
                                "_"
                        );

                String fileName =
                        "Stundenzettel_" +
                                safeEmployee +
                                "_" +
                                pendingPdfMonth +
                                ".pdf";

                Intent intent =
                        new Intent(
                                Intent.ACTION_CREATE_DOCUMENT
                        );

                intent.addCategory(
                        Intent.CATEGORY_OPENABLE
                );

                intent.setType(
                        "application/pdf"
                );

                intent.putExtra(
                        Intent.EXTRA_TITLE,
                        fileName
                );

                startActivityForResult(
                        intent,
                        PDF_CREATE_REQUEST
                );
            });
        }
    }

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

        if (
                requestCode
                        ==
                FILE_CHOOSER_REQUEST
        ) {

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
                requestCode
                        ==
                PDF_CREATE_REQUEST
        ) {

            if (
                    resultCode == RESULT_OK
                            &&
                    data != null
                            &&
                    data.getData() != null
            ) {

                createPdfFile(
                        data.getData()
                );

            } else {

                Toast.makeText(
                        this,
                        "PDF-Speichern abgebrochen.",
                        Toast.LENGTH_SHORT
                ).show();
            }

            return;
        }

        if (
                requestCode
                        ==
                AR_MEASURE_REQUEST
        ) {

            arMeasurementRunning = false;

            if (
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

                if (
                        width <= 0
                                ||
                        height <= 0
                                ||
                        area <= 0
                ) {

                    webView.postDelayed(
                            () -> webView.evaluateJavascript(
                                    "alert('Die Messung war ungültig.');",
                                    null
                            ),
                            250
                    );

                    return;
                }

                String javascript =
                        "if(typeof window.receiveArMeasurement==='function'){" +
                                "window.receiveArMeasurement(" +
                                width + "," +
                                height + "," +
                                area +
                                ");" +
                                "}";

                webView.postDelayed(
                        () -> webView.evaluateJavascript(
                                javascript,
                                null
                        ),
                        250
                );

            } else {

                webView.postDelayed(
                        () -> webView.evaluateJavascript(
                                "if(typeof openPage==='function'){" +
                                        "openPage('foto');" +
                                        "}",
                                null
                        ),
                        200
                );
            }
        }
    }

    private void createPdfFile(
            Uri uri
    ) {

        PdfDocument document =
                new PdfDocument();

        try {

            JSONArray rows =
                    new JSONArray(
                            pendingPdfJson
                    );

            int pageWidth = 595;
            int pageHeight = 842;
            int margin = 30;

            Paint titlePaint =
                    new Paint();

            titlePaint.setColor(
                    Color.rgb(
                            7,
                            132,
                            95
                    )
            );

            titlePaint.setTextSize(
                    22f
            );

            titlePaint.setFakeBoldText(
                    true
            );

            Paint boldPaint =
                    new Paint();

            boldPaint.setColor(
                    Color.BLACK
            );

            boldPaint.setTextSize(
                    11f
            );

            boldPaint.setFakeBoldText(
                    true
            );

            Paint normalPaint =
                    new Paint();

            normalPaint.setColor(
                    Color.BLACK
            );

            normalPaint.setTextSize(
                    8.5f
            );

            Paint linePaint =
                    new Paint();

            linePaint.setColor(
                    Color.LTGRAY
            );

            linePaint.setStrokeWidth(
                    1f
            );

            int pageNumber = 1;
            int index = 0;

            double totalHours = 0;

            while (
                    index
                            <
                    rows.length()
            ) {

                PdfDocument.PageInfo pageInfo =
                        new PdfDocument.PageInfo.Builder(
                                pageWidth,
                                pageHeight,
                                pageNumber
                        ).create();

                PdfDocument.Page page =
                        document.startPage(
                                pageInfo
                        );

                Canvas canvas =
                        page.getCanvas();

                float y = 45;

                canvas.drawText(
                        "E-M Cleaning Service",
                        margin,
                        y,
                        titlePaint
                );

                y += 28;

                canvas.drawText(
                        "Stundennachweis",
                        margin,
                        y,
                        boldPaint
                );

                y += 20;

                canvas.drawText(
                        "Mitarbeiter: " +
                                pendingPdfEmployee,
                        margin,
                        y,
                        normalPaint
                );

                y += 15;

                canvas.drawText(
                        "Monat: " +
                                pendingPdfMonth,
                        margin,
                        y,
                        normalPaint
                );

                y += 25;

                float xDate = margin;
                float xStatus = 95;
                float xStart = 155;
                float xEnd = 205;
                float xPause = 255;
                float xHours = 310;
                float xObject = 360;

                canvas.drawText(
                        "Datum",
                        xDate,
                        y,
                        boldPaint
                );

                canvas.drawText(
                        "Status",
                        xStatus,
                        y,
                        boldPaint
                );

                canvas.drawText(
                        "Start",
                        xStart,
                        y,
                        boldPaint
                );

                canvas.drawText(
                        "Ende",
                        xEnd,
                        y,
                        boldPaint
                );

                canvas.drawText(
                        "Pause",
                        xPause,
                        y,
                        boldPaint
                );

                canvas.drawText(
                        "Std.",
                        xHours,
                        y,
                        boldPaint
                );

                canvas.drawText(
                        "Objekt",
                        xObject,
                        y,
                        boldPaint
                );

                y += 8;

                canvas.drawLine(
                        margin,
                        y,
                        pageWidth - margin,
                        y,
                        linePaint
                );

                y += 15;

                while (
                        index
                                <
                        rows.length()
                                &&
                        y
                                <
                        pageHeight - 100
                ) {

                    JSONObject row =
                            rows.getJSONObject(
                                    index
                            );

                    String date =
                            row.optString(
                                    "datum"
                            );

                    String status =
                            row.optString(
                                    "status"
                            );

                    String start =
                            row.optString(
                                    "start"
                            );

                    String end =
                            row.optString(
                                    "ende"
                            );

                    String pause =
                            row.optString(
                                    "pause"
                            );

                    double hours =
                            row.optDouble(
                                    "stunden",
                                    0
                            );

                    String object =
                            row.optString(
                                    "objekt"
                            );

                    totalHours +=
                            hours;

                    canvas.drawText(
                            shorten(
                                    date,
                                    10
                            ),
                            xDate,
                            y,
                            normalPaint
                    );

                    canvas.drawText(
                            shorten(
                                    status,
                                    9
                            ),
                            xStatus,
                            y,
                            normalPaint
                    );

                    canvas.drawText(
                            shorten(
                                    start,
                                    5
                            ),
                            xStart,
                            y,
                            normalPaint
                    );

                    canvas.drawText(
                            shorten(
                                    end,
                                    5
                            ),
                            xEnd,
                            y,
                            normalPaint
                    );

                    canvas.drawText(
                            shorten(
                                    pause,
                                    5
                            ),
                            xPause,
                            y,
                            normalPaint
                    );

                    canvas.drawText(
                            String.format(
                                    Locale.GERMANY,
                                    "%.2f",
                                    hours
                            ),
                            xHours,
                            y,
                            normalPaint
                    );

                    canvas.drawText(
                            shorten(
                                    object,
                                    30
                            ),
                            xObject,
                            y,
                            normalPaint
                    );

                    y += 17;

                    canvas.drawLine(
                            margin,
                            y - 7,
                            pageWidth - margin,
                            y - 7,
                            linePaint
                    );

                    index++;
                }

                if (
                        index
                                >=
                        rows.length()
                ) {

                    y += 18;

                    boldPaint.setTextSize(
                            14f
                    );

                    canvas.drawText(
                            "Gesamtstunden: " +
                                    String.format(
                                            Locale.GERMANY,
                                            "%.2f Std.",
                                            totalHours
                                    ),
                            margin,
                            y,
                            boldPaint
                    );

                    boldPaint.setTextSize(
                            11f
                    );

                    y += 45;

                    canvas.drawText(
                            "Unterschrift Mitarbeiter: __________________________",
                            margin,
                            y,
                            normalPaint
                    );

                    y += 30;

                    canvas.drawText(
                            "Unterschrift Arbeitgeber: __________________________",
                            margin,
                            y,
                            normalPaint
                    );
                }

                document.finishPage(
                        page
                );

                pageNumber++;
            }

            OutputStream outputStream =
                    getContentResolver()
                            .openOutputStream(
                                    uri
                            );

            if (outputStream == null) {

                throw new Exception(
                        "Datei konnte nicht geöffnet werden."
                );
            }

            document.writeTo(
                    outputStream
            );

            outputStream.flush();
            outputStream.close();

            Toast.makeText(
                    this,
                    "PDF gespeichert ✅",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "PDF-Fehler: " +
                            e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();

        } finally {

            document.close();
        }
    }

    private String shorten(
            String text,
            int max
    ) {

        if (text == null) {
            return "";
        }

        if (
                text.length()
                        <=
                max
        ) {

            return text;
        }

        return text.substring(
                0,
                Math.max(
                        0,
                        max - 1
                )
        ) + "…";
    }

    @Override
    protected void onSaveInstanceState(
            Bundle outState
    ) {

        if (webView != null) {
            webView.saveState(outState);
        }

        super.onSaveInstanceState(
                outState
        );
    }

    @Override
    public void onBackPressed() {

        if (webView == null) {

            super.onBackPressed();
            return;
        }

        if (arMeasurementRunning) {

            super.onBackPressed();
            return;
        }

        webView.evaluateJavascript(
                "if(typeof window.androidBack==='function'){" +
                        "window.androidBack();" +
                        "}else{" +
                        "false;" +
                        "}",
                value -> {

                    boolean handled =
                            value != null
                                    &&
                            value.contains(
                                    "true"
                            );

                    if (handled) {
                        return;
                    }

                    if (
                            webView.canGoBack()
                    ) {

                        webView.goBack();
                        return;
                    }

                    MainActivity.super.onBackPressed();
                }
        );
    }

    @Override
    protected void onDestroy() {

        if (webView != null) {

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
