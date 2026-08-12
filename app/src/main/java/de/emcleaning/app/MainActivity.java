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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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

    private Uri lastSavedPdfUri = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        ensureFirebaseAuth(null);

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

                        filePathCallback = callback;

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
     * FIREBASE ANMELDUNG
     * =====================================================
     */

    private void ensureFirebaseAuth(
            Runnable action
    ) {

        com.google.firebase.auth.FirebaseAuth auth =
                com.google.firebase.auth.FirebaseAuth
                        .getInstance();

        if (auth.getCurrentUser() != null) {

            if (action != null) {
                action.run();
            }

            return;
        }

        auth.signInAnonymously()

                .addOnSuccessListener(
                        result -> {

                            if (action != null) {
                                action.run();
                            }
                        }
                )

                .addOnFailureListener(
                        e -> runOnUiThread(
                                () -> Toast.makeText(
                                        MainActivity.this,
                                        "Firebase-Anmeldung fehlgeschlagen: "
                                                + e.getMessage(),
                                        Toast.LENGTH_LONG
                                ).show()
                        )
                );
    }


    private String safeString(
            String value
    ) {

        return value == null
                ? ""
                : value;
    }


    /*
     * =====================================================
     * JAVASCRIPT BRIDGE
     * =====================================================
     */

    private class AndroidBridge {


        /*
         * =================================================
         * AR
         * =================================================
         */

        @JavascriptInterface
        public void startArMeasurement() {

            runOnUiThread(
                    () -> {

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
                    }
            );
        }


        /*
         * =================================================
         * MITARBEITER SPEICHERN
         * =================================================
         */

        @JavascriptInterface
        public void saveEmployeeToFirestore(
                String id,
                String name,
                String telefon,
                String art,
                String lohn
        ) {

            ensureFirebaseAuth(
                    () -> {

                        String employeeId =
                                safeString(id).trim();

                        if (employeeId.isEmpty()) {

                            employeeId =
                                    String.valueOf(
                                            System.currentTimeMillis()
                                    );
                        }

                        Map<String, Object> employee =
                                new HashMap<>();

                        employee.put(
                                "id",
                                employeeId
                        );

                        employee.put(
                                "name",
                                safeString(name)
                        );

                        employee.put(
                                "telefon",
                                safeString(telefon)
                        );

                        employee.put(
                                "art",
                                safeString(art)
                        );

                        employee.put(
                                "lohn",
                                safeString(lohn)
                        );

                        employee.put(
                                "updatedAt",
                                com.google.firebase.firestore
                                        .FieldValue
                                        .serverTimestamp()
                        );

                        com.google.firebase.firestore.FirebaseFirestore
                                .getInstance()

                                .collection(
                                        "employees"
                                )

                                .document(
                                        employeeId
                                )

                                .set(
                                        employee
                                )

                                .addOnSuccessListener(
                                        unused ->
                                                runOnUiThread(
                                                        () ->
                                                                Toast.makeText(
                                                                        MainActivity.this,
                                                                        "Mitarbeiter in Firebase gespeichert ✅",
                                                                        Toast.LENGTH_SHORT
                                                                ).show()
                                                )
                                )

                                .addOnFailureListener(
                                        e ->
                                                runOnUiThread(
                                                        () ->
                                                                Toast.makeText(
                                                                        MainActivity.this,
                                                                        "Mitarbeiter konnte nicht gespeichert werden: "
                                                                                + e.getMessage(),
                                                                        Toast.LENGTH_LONG
                                                                ).show()
                                                )
                                );
                    }
            );
        }


        /*
         * =================================================
         * MITARBEITER LADEN
         * =================================================
         */

        @JavascriptInterface
        public void loadEmployeesFromFirestore() {

            ensureFirebaseAuth(
                    () -> {

                        com.google.firebase.firestore.FirebaseFirestore
                                .getInstance()

                                .collection(
                                        "employees"
                                )

                                .get()

                                .addOnSuccessListener(
                                        snapshots -> {

                                            JSONArray array =
                                                    new JSONArray();

                                            for (
                                                    com.google.firebase.firestore
                                                            .QueryDocumentSnapshot doc
                                                    :
                                                    snapshots
                                            ) {

                                                try {

                                                    JSONObject obj =
                                                            new JSONObject();

                                                    String id =
                                                            safeString(
                                                                    doc.getString(
                                                                            "id"
                                                                    )
                                                            );

                                                    if (id.isEmpty()) {

                                                        id =
                                                                doc.getId();
                                                    }

                                                    obj.put(
                                                            "id",
                                                            id
                                                    );

                                                    obj.put(
                                                            "name",
                                                            safeString(
                                                                    doc.getString(
                                                                            "name"
                                                                    )
                                                            )
                                                    );

                                                    obj.put(
                                                            "telefon",
                                                            safeString(
                                                                    doc.getString(
                                                                            "telefon"
                                                                    )
                                                            )
                                                    );

                                                    obj.put(
                                                            "art",
                                                            safeString(
                                                                    doc.getString(
                                                                            "art"
                                                                    )
                                                            )
                                                    );

                                                    obj.put(
                                                            "lohn",
                                                           
