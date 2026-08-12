package de.emcleaning.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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

    /*
     * Zuletzt gespeicherte PDF.
     * Damit können wir sie anschließend teilen.
     */
    private Uri lastSavedPdfUri = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
com.google.firebase.auth.FirebaseAuth.getInstance().signInAnonymously();
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
                new WebViewClient()
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

                        } catch (Exception e) {

                            filePathCallback =
                                    null;

                            return false;
                        }
                    }
                }
        );


        /*
         * Verbindung HTML -> Android
         */

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );


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
     * =====================================================
     * JAVASCRIPT BRIDGE
     * =====================================================
     */

    private class AndroidBridge {


        /*
         * AR-Messung starten
         */

        @JavascriptInterface
        public void startArMeasurement() {

            runOnUiThread(
                    () -> {

                        try {

                            arMeasurementRunning =
                                    true;

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

                            arMeasurementRunning =
                                    false;

                            Toast.makeText(
                                    MainActivity.this,
                                    "AR-Messung konnte nicht geöffnet werden.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
            );
        }

@JavascriptInterface
public void saveOrderToFirestore(
        String customer,
        String address,
        String service,
        String date,
        String price,
        String description
) {
    java.util.Map<String, Object> order = new java.util.HashMap<>();

    order.put("customer", customer);
    order.put("address", address);
    order.put("service", service);
    order.put("date", date);
    order.put("price", price);
    order.put("description", description);
    order.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

    com.google.firebase.firestore.FirebaseFirestore
            .getInstance()
            .collection("orders")
            .add(order);
}
        @JavascriptInterface
public void loadOrdersFromFirestore() {

    com.google.firebase.firestore.FirebaseFirestore
        .getInstance()
        .collection("orders")
        .get()
        .addOnSuccessListener(snapshots -> {

            JSONArray array = new JSONArray();

            for (
                com.google.firebase.firestore.QueryDocumentSnapshot doc
                : snapshots
            ) {
                try {
                    JSONObject obj = new JSONObject();

                    obj.put("kunde", doc.getString("customer"));
                    obj.put("ort", doc.getString("address"));
                    obj.put("art", doc.getString("service"));
                    obj.put("datum", doc.getString("date"));
                    obj.put("preis", doc.getString("price"));
                    obj.put("notiz", doc.getString("description"));

                    array.put(obj);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            webView.post(() ->
                webView.evaluateJavascript(
                    "window.receiveOrdersFromFirestore(" +
                    array.toString() +
                    ");",
                    null
                )
            );
        });
}
        /*
         * Stundenzettel PDF erstellen
         */

        @JavascriptInterface
        public void createTimesheetPdf(
                String employee,
                String month,
                String jsonData
        ) {

            runOnUiThread(
                    () -> {

                        pendingPdfEmployee =
                                employee == null ||
                                employee.trim().isEmpty()
                                        ?
                                        "Mitarbeiter"
                                        :
                                        employee.trim();

                        pendingPdfMonth =
                                month == null
                                        ?
                                        ""
                                        :
                                        month.trim();

                        pendingPdfJson =
                                jsonData == null
                                        ?
                                        "[]"
                                        :
                                        jsonData;


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
                    }
            );
        }


        /*
         * Optional:
         * Falls wir später in index.html einen
         * separaten "PDF teilen"-Button einbauen.
         */

        @JavascriptInterface
        public void shareLastPdf() {

            runOnUiThread(
                    () -> sharePdf(
                            lastSavedPdfUri
                    )
            );
        }
    }


    /*
     * =====================================================
     * ACTIVITY ERGEBNISSE
     * =====================================================
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

            filePathCallback.onReceiveValue(
                    results
            );

            filePathCallback =
                    null;

            return;
        }


        /*
         * PDF-SPEICHERN
         */

        if (
                requestCode
                        ==
                PDF_CREATE_REQUEST
        ) {

            if (
                    resultCode
                            ==
                    RESULT_OK
                    &&
                    data
                            !=
                    null
                    &&
                    data.getData()
                            !=
                    null
            ) {

                Uri uri =
                        data.getData();

                lastSavedPdfUri =
                        uri;

                createPdfFile(
                        uri
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
                        250
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
                        200
                );
            }
        }
    }


    /*
     * =====================================================
     * PDF ERSTELLEN
     * =====================================================
     */

    private void createPdfFile(
            Uri uri
    ) {

        PdfDocument document =
                new PdfDocument();

        OutputStream outputStream =
                null;

        try {

            JSONArray rows =
                    new JSONArray(
                            pendingPdfJson
                    );


            /*
             * A4 in Punkten
             */

            final int pageWidth =
                    595;

            final int pageHeight =
                    842;

            final int marginLeft =
                    28;

            final int marginRight =
                    28;

            final int footerHeight =
                    35;


            /*
             * Farben / Schrift
             */

            Paint titlePaint =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );

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


            Paint subtitlePaint =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );

            subtitlePaint.setColor(
                    Color.DKGRAY
            );

            subtitlePaint.setTextSize(
                    10.5f
            );


            Paint boldPaint =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );

            boldPaint.setColor(
                    Color.BLACK
            );

            boldPaint.setTextSize(
                    9.5f
            );

            boldPaint.setFakeBoldText(
                    true
            );


            Paint normalPaint =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );

            normalPaint.setColor(
                    Color.BLACK
            );

            normalPaint.setTextSize(
                    8.3f
            );


            Paint smallPaint =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );

            smallPaint.setColor(
                    Color.DKGRAY
            );

            smallPaint.setTextSize(
                    7.5f
            );


            Paint linePaint =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );

            linePaint.setColor(
                    Color.rgb(
                            210,
                            215,
                            220
                    )
            );

            linePaint.setStrokeWidth(
                    1f
            );


            Paint headerBackgroundPaint =
                    new Paint();

            headerBackgroundPaint.setColor(
                    Color.rgb(
                            240,
                            244,
                            246
                    )
            );


            Paint totalBackgroundPaint =
                    new Paint();

            totalBackgroundPaint.setColor(
                    Color.rgb(
                            236,
                            253,
                            243
                    )
            );


            Paint totalBorderPaint =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );

            totalBorderPaint.setStyle(
                    Paint.Style.STROKE
            );

            totalBorderPaint.setStrokeWidth(
                    1f
            );

            totalBorderPaint.setColor(
                    Color.rgb(
                            171,
                            239,
                            198
                    )
            );


            /*
             * Gesamtstunden vorher berechnen.
             *
             * Dadurch stimmt die Summe auch
             * bei mehreren PDF-Seiten.
             */

            double totalHours =
                    0;

            for (
                    int i = 0;
                    i < rows.length();
                    i++
            ) {

                JSONObject row =
                        rows.getJSONObject(
                                i
                        );

                totalHours +=
                        row.optDouble(
                                "stunden",
                                0
                        );
            }


            /*
             * Tabellenwerte
             */

            int pageNumber =
                    1;

            int index =
                    0;


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


                float y =
                        34;


                /*
                 * =================================================
                 * KOPFBEREICH
                 * =================================================
                 */

                canvas.drawText(
                        "E-M Cleaning Service",
                        marginLeft,
                        y,
                        titlePaint
                );


                y +=
                        22;


                canvas.drawText(
                        "Stundennachweis",
                        marginLeft,
                        y,
                        boldPaint
                );


                y +=
                        16;


                canvas.drawText(
                        "Mitarbeiter: " +
                        pendingPdfEmployee,
                        marginLeft,
                        y,
                        subtitlePaint
                );


                String monthText =
                        "Monat: " +
                        formatPdfMonth(
                                pendingPdfMonth
                        );


                float monthWidth =
                        subtitlePaint.measureText(
                                monthText
                        );


                canvas.drawText(
                        monthText,
                        pageWidth -
                        marginRight -
                        monthWidth,
                        y,
                        subtitlePaint
                );


                y +=
                        15;


                canvas.drawLine(
                        marginLeft,
                        y,
                        pageWidth - marginRight,
                        y,
                        linePaint
                );


                y +=
                        20;


                /*
                 * =================================================
                 * TABELLENSPALTEN
                 * =================================================
                 */

                float xDate =
                        marginLeft;

                float wDate =
                        67;


                float xStatus =
                        xDate +
                        wDate;

                float wStatus =
                        58;


                float xStart =
                        xStatus +
                        wStatus;

                float wStart =
                        47;


                float xEnd =
                        xStart +
                        wStart;

                float wEnd =
                        47;


                float xPause =
                        xEnd +
                        wEnd;

                float wPause =
                        49;


                float xHours =
                        xPause +
                        wPause;

                float wHours =
                        52;


                float xObject =
                        xHours +
                        wHours;


                float tableHeaderTop =
                        y - 13;


                float tableHeaderBottom =
                        y + 6;


                canvas.drawRect(
                        new RectF(
                                marginLeft,
                                tableHeaderTop,
                                pageWidth - marginRight,
                                tableHeaderBottom
                        ),
                        headerBackgroundPaint
                );


                canvas.drawText(
                        "Datum",
                        xDate + 3,
                        y,
                        boldPaint
                );

                canvas.drawText(
                        "Status",
                        xStatus + 3,
                        y,
                        boldPaint
                );

                canvas.drawText(
                        "Start",
                        xStart + 3,
                        y,
                        boldPaint
                );

                canvas.drawText(
                        "Ende",
                        xEnd + 3,
                        y,
                        boldPaint
                );

                canvas.drawText(
                        "Pause",
                        xPause + 3,
                        y,
                        boldPaint
                );

                canvas.drawText(
                        "Std.",
                        xHours + 3,
                        y,
                        boldPaint
                );

                canvas.drawText(
                        "Objekt",
                        xObject + 3,
                        y,
                        boldPaint
                );


                y +=
                        14;


                float[] columnLines = {

                        marginLeft,

                        xStatus,

                        xStart,

                        xEnd,

                        xPause,

                        xHours,

                        xObject,

                        pageWidth -
                        marginRight
                };


                for (
                        float x :
                        columnLines
                ) {

                    canvas.drawLine(
                            x,
                            tableHeaderTop,
                            x,
                            tableHeaderBottom,
                            linePaint
                    );
                }


                canvas.drawLine(
                        marginLeft,
                        tableHeaderBottom,
                        pageWidth - marginRight,
                        tableHeaderBottom,
                        linePaint
                );


                /*
                 * =================================================
                 * TABELLENZEILEN
                 * =================================================
                 */

                while (
                        index
                                <
                        rows.length()
                        &&
                        y
                                <
                        pageHeight -
                        footerHeight -
                        125
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


                    float rowTop =
                            y - 9;


                    float rowBottom =
                            y + 7;


                    canvas.drawText(
                            shorten(
                                    date,
                                    10
                            ),
                            xDate + 3,
                            y,
                            normalPaint
                    );


                    canvas.drawText(
                            shorten(
                                    status,
                                    8
                            ),
                            xStatus + 3,
                            y,
                            normalPaint
                    );


                    canvas.drawText(
                            shorten(
                                    start,
                                    5
                            ),
                            xStart + 3,
                            y,
                            normalPaint
                    );


                    canvas.drawText(
                            shorten(
                                    end,
                                    5
                            ),
                            xEnd + 3,
                            y,
                            normalPaint
                    );


                    canvas.drawText(
                            shorten(
                                    pause,
                                    5
                            ),
                            xPause + 3,
                            y,
                            normalPaint
                    );


                    canvas.drawText(
                            String.format(
                                    Locale.GERMANY,
                                    "%.2f",
                                    hours
                            ),
                            xHours + 3,
                            y,
                            normalPaint
                    );


                    canvas.drawText(
                            shorten(
                                    object,
                                    28
                            ),
                            xObject + 3,
                            y,
                            normalPaint
                    );


                    for (
                            float x :
                            columnLines
                    ) {

                        canvas.drawLine(
                                x,
                                rowTop,
                                x,
                                rowBottom,
                                linePaint
                        );
                    }


                    canvas.drawLine(
                            marginLeft,
                            rowBottom,
                            pageWidth - marginRight,
                            rowBottom,
                            linePaint
                    );


                    y +=
                            16;


                    index++;
                }


                /*
                 * =================================================
                 * LETZTE SEITE
                 * =================================================
                 */

                if (
                        index
                                >=
                        rows.length()
                ) {

                    y +=
                            18;


                    RectF totalBox =
                            new RectF(
                                    marginLeft,
                                    y,
                                    pageWidth - marginRight,
                                    y + 42
                            );


                    canvas.drawRoundRect(
                            totalBox,
                            8,
                            8,
                            totalBackgroundPaint
                    );


                    canvas.drawRoundRect(
                            totalBox,
                            8,
                            8,
                            totalBorderPaint
                    );


                    canvas.drawText(
                            "Gesamtstunden",
                            marginLeft + 12,
                            y + 17,
                            boldPaint
                    );


                    Paint totalHoursPaint =
                            new Paint(
                                    Paint.ANTI_ALIAS_FLAG
                            );


                    totalHoursPaint.setColor(
                            Color.rgb(
                                    6,
                                    118,
                                    71
                            )
                    );


                    totalHoursPaint.setTextSize(
                            16f
                    );


                    totalHoursPaint.setFakeBoldText(
                            true
                    );


                    canvas.drawText(
                            String.format(
                                    Locale.GERMANY,
                                    "%.2f Std.",
                                    totalHours
                            ),
                            marginLeft + 12,
                            y + 34,
                            totalHoursPaint
                    );


                    /*
                     * Unterschriften
                     */

                    y +=
                            78;


                    canvas.drawText(
                            "Unterschrift Mitarbeiter",
                            marginLeft,
                            y,
                            smallPaint
                    );


                    canvas.drawLine(
                            marginLeft,
                            y + 18,
                            marginLeft + 220,
                            y + 18,
                            linePaint
                    );


                    canvas.drawText(
                            "Unterschrift Arbeitgeber",
                            pageWidth -
                            marginRight -
                            220,
                            y,
                            smallPaint
                    );


                    canvas.drawLine(
                            pageWidth -
                            marginRight -
                            220,
                            y + 18,
                            pageWidth -
                            marginRight,
                            y + 18,
                            linePaint
                    );
                }


                /*
                 * =================================================
                 * FUßZEILE
                 * =================================================
                 */

                canvas.drawLine(
                        marginLeft,
                        pageHeight - 30,
                        pageWidth - marginRight,
                        pageHeight - 30,
                        linePaint
                );


                canvas.drawText(
                        "E-M Cleaning Service",
                        marginLeft,
                        pageHeight - 16,
                        smallPaint
                );


                /*
                 * Nur EIN Seitentext.
                 */

                String pageText =
                        "Seite " +
                        pageNumber;


                float pageTextWidth =
                        smallPaint.measureText(
                                pageText
                        );


                canvas.drawText(
                        pageText,
                        pageWidth -
                        marginRight -
                        pageTextWidth,
                        pageHeight - 16,
                        smallPaint
                );


                document.finishPage(
                        page
                );


                pageNumber++;
            }


            /*
             * PDF schreiben
             */

            outputStream =
                    getContentResolver()
                            .openOutputStream(
                                    uri
                            );


            if (
                    outputStream
                            ==
                    null
            ) {

                throw new Exception(
                        "Datei konnte nicht geöffnet werden."
                );
            }


            document.writeTo(
                    outputStream
            );


            outputStream.flush();


            Toast.makeText(
                    this,
                    "PDF gespeichert ✅",
                    Toast.LENGTH_LONG
            ).show();


            /*
             * Nach erfolgreichem Speichern:
             * Teilen anbieten.
             */

            showShareDialog(
                    uri
            );


        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "PDF-Fehler: " +
                    e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();


        } finally {

            try {

                if (
                        outputStream
                                !=
                        null
                ) {

                    outputStream.close();
                }

            } catch (
                    Exception ignored
            ) {

            }


            document.close();
        }
    }


    /*
     * =====================================================
     * NACH DEM SPEICHERN: TEILEN?
     * =====================================================
     */

    private void showShareDialog(
            Uri uri
    ) {

        if (
                uri
                        ==
                null
        ) {

            return;
        }


        new AlertDialog.Builder(
                this
        )

                .setTitle(
                        "PDF gespeichert ✅"
                )

                .setMessage(
                        "Möchtest du den Stundenzettel jetzt teilen?"
                )

                .setNegativeButton(
                        "Nein",
                        null
                )

                .setPositiveButton(
                        "PDF teilen",
                        (dialog, which) ->
                                sharePdf(
                                        uri
                                )
                )

                .show();
    }


    /*
     * =====================================================
     * PDF TEILEN
     * =====================================================
     */

    private void sharePdf(
            Uri uri
    ) {

        if (
                uri
                        ==
                null
        ) {

            Toast.makeText(
                    this,
                    "Bitte zuerst eine PDF speichern.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        try {

            Intent shareIntent =
                    new Intent(
                            Intent.ACTION_SEND
                    );


            shareIntent.setType(
                    "application/pdf"
            );


            shareIntent.putExtra(
                    Intent.EXTRA_STREAM,
                    uri
            );


            shareIntent.putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Stundenzettel " +
                    pendingPdfEmployee +
                    " " +
                    formatPdfMonth(
                            pendingPdfMonth
                    )
            );


            shareIntent.putExtra(
                    Intent.EXTRA_TEXT,
                    "Stundenzettel von " +
                    pendingPdfEmployee +
                    " für " +
                    formatPdfMonth(
                            pendingPdfMonth
                    ) +
                    "."
            );


            /*
             * Sehr wichtig:
             * Andere Apps dürfen die PDF lesen.
             */

            shareIntent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );


            startActivity(
                    Intent.createChooser(
                            shareIntent,
                            "Stundenzettel teilen"
                    )
            );


        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "PDF konnte nicht geteilt werden.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }


    /*
     * =====================================================
     * MONAT SCHÖN FORMATIEREN
     * =====================================================
     */

    private String formatPdfMonth(
            String month
    ) {

        if (
                month
                        ==
                null
                ||
                month.trim().isEmpty()
        ) {

            return "";
        }


        try {

            String[] parts =
                    month.split(
                            "-"
                    );


            int year =
                    Integer.parseInt(
                            parts[0]
                    );


            int monthNumber =
                    Integer.parseInt(
                            parts[1]
                    );


            String[] monthNames = {

                    "",

                    "Januar",

                    "Februar",

                    "März",

                    "April",

                    "Mai",

                    "Juni",

                    "Juli",

                    "August",

                    "September",

                    "Oktober",

                    "November",

                    "Dezember"
            };


            if (
                    monthNumber >= 1
                    &&
                    monthNumber <= 12
            ) {

                return monthNames[
                        monthNumber
                        ] +
                        " " +
                        year;
            }


        } catch (
                Exception ignored
        ) {

        }


        return month;
    }


    /*
     * =====================================================
     * TEXT KÜRZEN
     * =====================================================
     */

    private String shorten(
            String text,
            int max
    ) {

        if (
                text
                        ==
                null
        ) {

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


    /*
     * =====================================================
     * WEBVIEW ZUSTAND
     * =====================================================
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
     * =====================================================
     * ZURÜCK-TASTE
     * =====================================================
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


        if (
                arMeasurementRunning
        ) {

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


                    if (
                            handled
                    ) {

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


    /*
     * =====================================================
     * APP SCHLIESSEN
     * =====================================================
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

            webView =
                    null;
        }


        super.onDestroy();
    }
}
