package de.emcleaning.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int AR_MEASURE_REQUEST = 2001;
    private static final int PDF_CREATE_REQUEST = 3001;
    private static final int DOCUMENT_PICK_REQUEST = 4001;

    private static final String ADMIN_PIN = "2012";

    private boolean adminLoggedIn = false;
    private boolean arMeasurementRunning = false;

    private String loggedEmployeeId = "";
    private String loggedEmployeeName = "";

    private String pendingPdfEmployee = "";
    private String pendingPdfMonth = "";
    private String pendingPdfJson = "[]";

    private Uri lastSavedPdfUri = null;

    /*
     * Informationen für einen Dokument-Upload.
     */
    private String pendingDocumentId = "";
    private String pendingDocumentTitle = "";
    private String pendingDocumentType = "";
    private String pendingDocumentEmployeeId = "";
    private String pendingDocumentEmployeeName = "";
    private String pendingDocumentNote = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        webView = new WebView(this);

        setContentView(webView);

        WebSettings settings =
                webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        settings.setCacheMode(
                WebSettings.LOAD_NO_CACHE
        );

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);

        webView.clearCache(true);


        webView.setWebViewClient(

                new WebViewClient() {

                    @Override
                    public void onPageStarted(
                            WebView view,
                            String url,
                            Bitmap favicon
                    ) {

                        super.onPageStarted(
                                view,
                                url,
                                favicon
                        );
                    }


                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {

                        super.onPageFinished(
                                view,
                                url
                        );

                        if (
                                url != null
                                        &&
                                url.contains(
                                        "index.html"
                                )
                        ) {

                            ensureFirebaseAuth(
                                    () -> {

                                        toast(
                                                "Firebase verbunden ✅"
                                        );

                                        loadEmployeesInternal();
                                    }
                            );
                        }
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

                            startActivityForResult(
                                    intent,
                                    FILE_CHOOSER_REQUEST
                            );

                            return true;

                        } catch (Exception e) {

                            filePathCallback =
                                    null;

                            toast(
                                    "Datei konnte nicht geöffnet werden."
                            );

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


    /* =====================================================
       FIREBASE
    ===================================================== */

    private void ensureFirebaseAuth(
            Runnable action
    ) {

        FirebaseAuth auth =
                FirebaseAuth.getInstance();


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
                        e -> {

                            String message =
                                    e.getMessage() == null
                                            ?
                                            "Unbekannter Fehler"
                                            :
                                            e.getMessage();

                            toast(
                                    "Firebase-Anmeldung fehlgeschlagen: "
                                            +
                                            message
                            );

                            sendFirebaseError(
                                    "AUTH",
                                    message
                            );
                        }
                );
    }


    private FirebaseFirestore db() {

        return FirebaseFirestore.getInstance();
    }


    private FirebaseStorage storage() {

        return FirebaseStorage.getInstance();
    }


    /* =====================================================
       MITARBEITER INTERN LADEN
    ===================================================== */

    private void loadEmployeesInternal() {

        ensureFirebaseAuth(
                () -> {

                    db()
                            .collection(
                                    "employees"
                            )
                            .get()

                            .addOnSuccessListener(
                                    snapshots -> {

                                        JSONArray result =
                                                new JSONArray();


                                        for (
                                                QueryDocumentSnapshot doc :
                                                snapshots
                                        ) {

                                            try {

                                                JSONObject item =
                                                        new JSONObject();


                                                String id =
                                                        safe(
                                                                doc.getString(
                                                                        "id"
                                                                )
                                                        );


                                                if (id.isEmpty()) {

                                                    id =
                                                            doc.getId();
                                                }


                                                item.put(
                                                        "id",
                                                        id
                                                );


                                                item.put(
                                                        "name",
                                                        safe(
                                                                doc.getString(
                                                                        "name"
                                                                )
                                                        )
                                                );


                                                Boolean pinSet =
                                                        doc.getBoolean(
                                                                "pinSet"
                                                        );


                                                item.put(
                                                        "pinSet",
                                                        pinSet != null
                                                                &&
                                                                pinSet
                                                );


                                                if (adminLoggedIn) {

                                                    item.put(
                                                            "telefon",
                                                            safe(
                                                                    doc.getString(
                                                                            "telefon"
                                                                    )
                                                            )
                                                    );


                                                    item.put(
                                                            "art",
                                                            safe(
                                                                    doc.getString(
                                                                            "art"
                                                                    )
                                                            )
                                                    );


                                                    item.put(
                                                            "lohn",
                                                            safe(
                                                                    doc.getString(
                                                                            "lohn"
                                                                    )
                                                            )
                                                    );
                                                }


                                                result.put(
                                                        item
                                                );

                                            } catch (Exception e) {

                                                e.printStackTrace();
                                            }
                                        }


                                        sendJs(
                                                "receiveEmployeesFromFirestore",
                                                result
                                        );
                                    }
                            )

                            .addOnFailureListener(
                                    e -> {

                                        String message =
                                                safe(
                                                        e.getMessage()
                                                );


                                        sendFirebaseError(
                                                "EMPLOYEES",
                                                message
                                        );


                                        sendJs(
                                                "receiveEmployeesFromFirestore",
                                                new JSONArray()
                                        );


                                        toast(
                                                "Mitarbeiter konnten nicht geladen werden: "
                                                        +
                                                        message
                                        );
                                    }
                            );
                }
        );
    }


    private void sendFirebaseError(
            String type,
            String message
    ) {

        try {

            JSONObject error =
                    new JSONObject();


            error.put(
                    "type",
                    safe(type)
            );


            error.put(
                    "message",
                    safe(message)
            );


            sendJs(
                    "receiveFirebaseError",
                    error
            );

        } catch (Exception ignored) {

        }
    }


    /* =====================================================
       HELFER
    ===================================================== */

    private void toast(
            String text
    ) {

        runOnUiThread(
                () ->
                        Toast.makeText(
                                MainActivity.this,
                                text,
                                Toast.LENGTH_LONG
                        ).show()
        );
    }


    private String safe(
            String value
    ) {

        return value == null
                ?
                ""
                :
                value;
    }


    private String pinHash(
            String employeeId,
            String pin
    ) {

        try {

            String value =
                    safe(employeeId)
                            +
                            ":"
                            +
                            safe(pin);


            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );


            byte[] bytes =
                    digest.digest(
                            value.getBytes(
                                    "UTF-8"
                            )
                    );


            StringBuilder result =
                    new StringBuilder();


            for (byte b : bytes) {

                result.append(
                        String.format(
                                Locale.US,
                                "%02x",
                                b & 0xff
                        )
                );
            }


            return result.toString();

        } catch (Exception e) {

            return "";
        }
    }


    private long timestampMillis(
            QueryDocumentSnapshot doc,
            String field
    ) {

        try {

            Timestamp timestamp =
                    doc.getTimestamp(
                            field
                    );


            if (timestamp == null) {

                return 0;
            }


            return timestamp
                    .toDate()
                    .getTime();

        } catch (Exception e) {

            return 0;
        }
    }


    private void sendJs(
            String function,
            Object json
    ) {

        if (webView == null) {

            return;
        }


        String javascript =

                "if(typeof window."
                        +
                        function
                        +
                        "==='function'){window."
                        +
                        function
                        +
                        "("
                        +
                        json.toString()
                        +
                        ");}";


        webView.post(
                () ->
                        webView.evaluateJavascript(
                                javascript,
                                null
                        )
        );
    }


    private String getFileName(
            Uri uri
    ) {

        String result =
                "datei";


        try {

            if (
                    "content".equals(
                            uri.getScheme()
                    )
            ) {

                Cursor cursor =
                        getContentResolver()
                                .query(
                                        uri,
                                        null,
                                        null,
                                        null,
                                        null
                                );


                if (cursor != null) {

                    try {

                        int index =
                                cursor.getColumnIndex(
                                        OpenableColumns.DISPLAY_NAME
                                );


                        if (
                                index >= 0
                                        &&
                                cursor.moveToFirst()
                        ) {

                            result =
                                    cursor.getString(
                                            index
                                    );
                        }

                    } finally {

                        cursor.close();
                    }
                }
            }


            if (
                    result == null
                            ||
                    result.trim().isEmpty()
            ) {

                result =
                        uri.getLastPathSegment();
            }

        } catch (Exception ignored) {

        }


        if (
                result == null
                        ||
                result.trim().isEmpty()
        ) {

            result =
                    "dokument";
        }


        return result;
    }


    private String cleanStorageName(
            String fileName
    ) {

        String cleaned =
                safe(fileName)
                        .replaceAll(
                                "[^a-zA-Z0-9ÄÖÜäöüß._-]",
                                "_"
                        );


        if (cleaned.isEmpty()) {

            cleaned =
                    "dokument";
        }


        return cleaned;
    }


    /* =====================================================
       DOKUMENT UPLOAD
    ===================================================== */

    private void openDocumentPicker() {

        runOnUiThread(
                () -> {

                    Intent intent =
                            new Intent(
                                    Intent.ACTION_OPEN_DOCUMENT
                            );


                    intent.addCategory(
                            Intent.CATEGORY_OPENABLE
                    );


                    intent.setType(
                            "*/*"
                    );


                    intent.putExtra(
                            Intent.EXTRA_MIME_TYPES,
                            new String[]{
                                    "application/pdf",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "image/jpeg",
                                    "image/png"
                            }
                    );


                    try {

                        startActivityForResult(
                                intent,
                                DOCUMENT_PICK_REQUEST
                        );

                    } catch (Exception e) {

                        toast(
                                "Dateiauswahl konnte nicht geöffnet werden."
                        );
                    }
                }
        );
    }


    private void uploadDocumentFile(
            Uri fileUri
    ) {

        if (!adminLoggedIn) {

            toast(
                    "Nur die Verwaltung darf Dokumente hochladen."
            );

            return;
        }


        if (fileUri == null) {

            toast(
                    "Keine Datei ausgewählt."
            );

            return;
        }


        ensureFirebaseAuth(
                () -> {

                    String documentId =
                            pendingDocumentId;


                    if (
                            documentId == null
                                    ||
                            documentId.trim().isEmpty()
                    ) {

                        documentId =
                                String.valueOf(
                                        System.currentTimeMillis()
                                );
                    }


                    final String finalDocumentId =
                            documentId;


                    String originalFileName =
                            getFileName(
                                    fileUri
                            );


                    String cleanFileName =
                            cleanStorageName(
                                    originalFileName
                            );


                    String folder =

                            pendingDocumentEmployeeId
                                    .trim()
                                    .isEmpty()

                                    ?

                                    "general"

                                    :

                                    "employees/"
                                            +
                                            cleanStorageName(
                                                    pendingDocumentEmployeeId
                                            );


                    String storagePath =

                            "documents/"
                                    +
                                    folder
                                    +
                                    "/"
                                    +
                                    finalDocumentId
                                    +
                                    "_"
                                    +
                                    cleanFileName;


                    StorageReference reference =
                            storage()
                                    .getReference()
                                    .child(
                                            storagePath
                                    );


                    String mimeType =
                            getContentResolver()
                                    .getType(
                                            fileUri
                                    );


                    if (
                            mimeType == null
                                    ||
                            mimeType.trim().isEmpty()
                    ) {

                        mimeType =
                                "application/octet-stream";
                    }


                    StorageMetadata metadata =
                            new StorageMetadata.Builder()
                                    .setContentType(
                                            mimeType
                                    )
                                    .setCustomMetadata(
                                            "documentId",
                                            finalDocumentId
                                    )
                                    .setCustomMetadata(
                                            "employeeId",
                                            pendingDocumentEmployeeId
                                    )
                                    .build();


                    toast(
                            "Dokument wird hochgeladen..."
                    );


                    final String finalMimeType =
                            mimeType;


                    reference
                            .putFile(
                                    fileUri,
                                    metadata
                            )

                            .addOnSuccessListener(
                                    taskSnapshot -> {

                                        saveUploadedDocumentMetadata(
                                                finalDocumentId,
                                                originalFileName,
                                                finalMimeType,
                                                storagePath
                                        );
                                    }
                            )

                            .addOnFailureListener(
                                    e -> {

                                        toast(
                                                "Upload fehlgeschlagen: "
                                                        +
                                                        safe(
                                                                e.getMessage()
                                                        )
                                        );


                                        sendDocumentUploadResult(
                                                false,
                                                "Dokument konnte nicht hochgeladen werden."
                                        );
                                    }
                            );
                }
        );
    }


    private void saveUploadedDocumentMetadata(
            String documentId,
            String fileName,
            String mimeType,
            String storagePath
    ) {

        Map<String, Object> map =
                new HashMap<>();


        map.put(
                "id",
                documentId
        );


        map.put(
                "title",
                pendingDocumentTitle
        );


        map.put(
                "type",
                pendingDocumentType
        );


        map.put(
                "employeeId",
                pendingDocumentEmployeeId
        );


        map.put(
                "employeeName",
                pendingDocumentEmployeeName
        );


        map.put(
                "note",
                pendingDocumentNote
        );


        map.put(
                "fileName",
                fileName
        );


        map.put(
                "mimeType",
                mimeType
        );


        map.put(
                "storagePath",
                storagePath
        );


        /*
         * Alte Link-Funktion bleibt kompatibel.
         */
        map.put(
                "url",
                ""
        );


        map.put(
                "updatedAt",
                FieldValue.serverTimestamp()
        );


        db()
                .collection(
                        "companyDocuments"
                )
                .document(
                        documentId
                )
                .set(
                        map
                )

                .addOnSuccessListener(
                        unused -> {

                            toast(
                                    "Dokument hochgeladen ✅"
                            );


                            sendDocumentUploadResult(
                                    true,
                                    "Dokument wurde erfolgreich hochgeladen."
                            );


                            clearPendingDocument();


                            new AndroidBridge()
                                    .loadDocumentsFromFirestore(
                                            ""
                                    );
                        }
                )

                .addOnFailureListener(
                        e -> {

                            /*
                             * Falls Firestore fehlschlägt,
                             * hochgeladene Datei wieder entfernen.
                             */
                            storage()
                                    .getReference()
                                    .child(
                                            storagePath
                                    )
                                    .delete();


                            toast(
                                    "Dokument konnte nicht gespeichert werden: "
                                            +
                                            safe(
                                                    e.getMessage()
                                            )
                            );


                            sendDocumentUploadResult(
                                    false,
                                    "Dokumentdaten konnten nicht gespeichert werden."
                            );
                        }
                );
    }


    private void sendDocumentUploadResult(
            boolean success,
            String message
    ) {

        try {

            JSONObject result =
                    new JSONObject();


            result.put(
                    "success",
                    success
            );


            result.put(
                    "message",
                    message
            );


            sendJs(
                    "receiveDocumentUploadResult",
                    result
            );

        } catch (Exception ignored) {

        }
    }


    private void clearPendingDocument() {

        pendingDocumentId =
                "";

        pendingDocumentTitle =
                "";

        pendingDocumentType =
                "";

        pendingDocumentEmployeeId =
                "";

        pendingDocumentEmployeeName =
                "";

        pendingDocumentNote =
                "";
    }


    private void openStoredDocument(
            String storagePath,
            String fileName,
            String mimeType
    ) {

        String cleanPath =
                safe(storagePath).trim();


        if (cleanPath.isEmpty()) {

            toast(
                    "Dokument-Datei wurde nicht gefunden."
            );

            return;
        }


        ensureFirebaseAuth(
                () -> {

                    try {

                        File directory =
                                new File(
                                        getCacheDir(),
                                        "documents"
                                );


                        if (!directory.exists()) {

                            directory.mkdirs();
                        }


                        String safeName =
                                cleanStorageName(
                                        fileName
                                );


                        if (safeName.isEmpty()) {

                            safeName =
                                    "dokument";
                        }


                        File localFile =
                                new File(
                                        directory,
                                        safeName
                                );


                        toast(
                                "Dokument wird geöffnet..."
                        );


                        storage()
                                .getReference()
                                .child(
                                        cleanPath
                                )
                                .getFile(
                                        localFile
                                )

                                .addOnSuccessListener(
                                        taskSnapshot -> {

                                            try {

                                                Uri contentUri =
                                                        FileProvider.getUriForFile(
                                                                MainActivity.this,
                                                                getPackageName()
                                                                        +
                                                                        ".fileprovider",
                                                                localFile
                                                        );


                                                Intent intent =
                                                        new Intent(
                                                                Intent.ACTION_VIEW
                                                        );


                                                String type =
                                                        safe(
                                                                mimeType
                                                        );


                                                if (type.isEmpty()) {

                                                    type =
                                                            "*/*";
                                                }


                                                intent.setDataAndType(
                                                        contentUri,
                                                        type
                                                );


                                                intent.addFlags(
                                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                );


                                                startActivity(
                                                        Intent.createChooser(
                                                                intent,
                                                                "Dokument öffnen"
                                                        )
                                                );

                                            } catch (Exception e) {

                                                toast(
                                                        "Für diese Datei wurde keine passende App gefunden."
                                                );
                                            }
                                        }
                                )

                                .addOnFailureListener(
                                        e ->
                                                toast(
                                                        "Dokument konnte nicht geladen werden: "
                                                                +
                                                                safe(
                                                                        e.getMessage()
                                                                )
                                                )
                                );

                    } catch (Exception e) {

                        toast(
                                "Dokument konnte nicht geöffnet werden."
                        );
                    }
                }
        );
    }


    /* =====================================================
       JAVASCRIPT BRIDGE
    ===================================================== */

    private class AndroidBridge {


        /* =================================================
           ADMIN
        ================================================= */

        @JavascriptInterface
        public void loginAdmin(
                String pin
        ) {

            try {

                JSONObject result =
                        new JSONObject();


                if (
                        ADMIN_PIN.equals(
                                safe(pin).trim()
                        )
                ) {

                    adminLoggedIn =
                            true;


                    loggedEmployeeId =
                            "";


                    loggedEmployeeName =
                            "";


                    result.put(
                            "success",
                            true
                    );


                    result.put(
                            "message",
                            "Verwaltung geöffnet."
                    );

                } else {

                    adminLoggedIn =
                            false;


                    result.put(
                            "success",
                            false
                    );


                    result.put(
                            "message",
                            "Admin-PIN ist falsch."
                    );
                }


                sendJs(
                        "receiveAdminLogin",
                        result
                );

            } catch (Exception e) {

                e.printStackTrace();
            }
        }


        @JavascriptInterface
        public void logoutAdmin() {

            adminLoggedIn =
                    false;
        }


        /* =================================================
           MITARBEITER
        ================================================= */

        @JavascriptInterface
        public void loadEmployeesFromFirestore() {

            loadEmployeesInternal();
        }


        @JavascriptInterface
        public void saveEmployeeToFirestore(
                String id,
                String name,
                String telefon,
                String art,
                String lohn,
                String pin
        ) {

            if (!adminLoggedIn) {

                toast(
                        "Nur die Verwaltung darf Mitarbeiter ändern."
                );

                return;
            }


            ensureFirebaseAuth(
                    () -> {

                        String employeeId =
                                safe(id).trim();


                        if (employeeId.isEmpty()) {

                            employeeId =
                                    String.valueOf(
                                            System.currentTimeMillis()
                                    );
                        }


                        String employeeName =
                                safe(name).trim();


                        String cleanPin =
                                safe(pin).trim();


                        if (employeeName.isEmpty()) {

                            toast(
                                    "Name fehlt."
                            );

                            return;
                        }


                        if (
                                !cleanPin.isEmpty()
                                        &&
                                !cleanPin.matches(
                                                "\\d{4}"
                                        )
                        ) {

                            toast(
                                    "PIN muss aus genau 4 Ziffern bestehen."
                            );

                            return;
                        }


                        final String finalId =
                                employeeId;


                        DocumentReference ref =
                                db()
                                        .collection(
                                                "employees"
                                        )
                                        .document(
                                                finalId
                                        );


                        ref.get()

                                .addOnSuccessListener(
                                        snapshot -> {

                                            Map<String, Object> map =
                                                    new HashMap<>();


                                            map.put(
                                                    "id",
                                                    finalId
                                            );


                                            map.put(
                                                    "name",
                                                    employeeName
                                            );


                                            map.put(
                                                    "telefon",
                                                    safe(telefon)
                                            );


                                            map.put(
                                                    "art",
                                                    safe(art)
                                            );


                                            map.put(
                                                    "lohn",
                                                    safe(lohn)
                                            );


                                            if (!cleanPin.isEmpty()) {

                                                map.put(
                                                        "pinHash",
                                                        pinHash(
                                                                finalId,
                                                                cleanPin
                                                        )
                                                );


                                                map.put(
                                                        "pinSet",
                                                        true
                                                );

                                            } else {

                                                Object oldHash =
                                                        snapshot.get(
                                                                "pinHash"
                                                        );


                                                if (oldHash != null) {

                                                    map.put(
                                                            "pinHash",
                                                            oldHash
                                                    );


                                                    map.put(
                                                            "pinSet",
                                                            true
                                                    );

                                                } else {

                                                    map.put(
                                                            "pinSet",
                                                            false
                                                    );
                                                }
                                            }


                                            map.put(
                                                    "updatedAt",
                                                    FieldValue.serverTimestamp()
                                            );


                                            ref.set(
                                                    map
                                            )

                                                    .addOnSuccessListener(
                                                            unused -> {

                                                                toast(
                                                                        "Mitarbeiter gespeichert ✅"
                                                                );


                                                                loadEmployeesInternal();
                                                            }
                                                    )

                                                    .addOnFailureListener(
                                                            e ->
                                                                    toast(
                                                                            "Mitarbeiter konnte nicht gespeichert werden: "
                                                                                    +
                                                                                    safe(
                                                                                            e.getMessage()
                                                                                    )
                                                                    )
                                                    );
                                        }
                                );
                    }
            );
        }


        /* =================================================
           MITARBEITER LOGIN
        ================================================= */

        @JavascriptInterface
        public void loginEmployee(
                String employeeId,
                String pin
        ) {

            ensureFirebaseAuth(
                    () -> {

                        String id =
                                safe(employeeId).trim();


                        String cleanPin =
                                safe(pin).trim();


                        if (
                                id.isEmpty()
                                        ||
                                !cleanPin.matches(
                                        "\\d{4}"
                                )
                        ) {

                            sendEmployeeLogin(
                                    false,
                                    null,
                                    "Bitte Mitarbeiter und 4-stellige PIN eingeben."
                            );

                            return;
                        }


                        db()
                                .collection(
                                        "employees"
                                )
                                .document(
                                        id
                                )
                                .get()

                                .addOnSuccessListener(
                                        snapshot -> {

                                            if (!snapshot.exists()) {

                                                sendEmployeeLogin(
                                                        false,
                                                        null,
                                                        "Mitarbeiter nicht gefunden."
                                                );

                                                return;
                                            }


                                            String stored =
                                                    snapshot.getString(
                                                            "pinHash"
                                                    );


                                            if (
                                                    stored == null
                                                            ||
                                                    stored.trim().isEmpty()
                                            ) {

                                                sendEmployeeLogin(
                                                        false,
                                                        null,
                                                        "Für diesen Mitarbeiter wurde noch keine PIN eingerichtet."
                                                );

                                                return;
                                            }


                                            String entered =
                                                    pinHash(
                                                            id,
                                                            cleanPin
                                                    );


                                            if (!stored.equals(entered)) {

                                                sendEmployeeLogin(
                                                        false,
                                                        null,
                                                        "PIN ist falsch."
                                                );

                                                return;
                                            }


                                            try {

                                                JSONObject employee =
                                                        new JSONObject();


                                                employee.put(
                                                        "id",
                                                        id
                                                );


                                                employee.put(
                                                        "name",
                                                        safe(
                                                                snapshot.getString(
                                                                        "name"
                                                                )
                                                        )
                                                );


                                                employee.put(
                                                        "telefon",
                                                        safe(
                                                                snapshot.getString(
                                                                        "telefon"
                                                                )
                                                        )
                                                );


                                                employee.put(
                                                        "art",
                                                        safe(
                                                                snapshot.getString(
                                                                        "art"
                                                                )
                                                        )
                                                );


                                                employee.put(
                                                        "lohn",
                                                        safe(
                                                                snapshot.getString(
                                                                        "lohn"
                                                                )
                                                        )
                                                );


                                                loggedEmployeeId =
                                                        id;


                                                loggedEmployeeName =
                                                        safe(
                                                                snapshot.getString(
                                                                        "name"
                                                                )
                                                        );


                                                adminLoggedIn =
                                                        false;


                                                sendEmployeeLogin(
                                                        true,
                                                        employee,
                                                        "Anmeldung erfolgreich."
                                                );

                                            } catch (Exception e) {

                                                sendEmployeeLogin(
                                                        false,
                                                        null,
                                                        "Login-Fehler."
                                                );
                                            }
                                        }
                                )

                                .addOnFailureListener(
                                        e ->
                                                sendEmployeeLogin(
                                                        false,
                                                        null,
                                                        "Mitarbeiter konnte nicht geladen werden: "
                                                                +
                                                                safe(
                                                                        e.getMessage()
                                                                )
                                                )
                                );
                    }
            );
        }


        private void sendEmployeeLogin(
                boolean success,
                JSONObject employee,
                String message
        ) {

            try {

                JSONObject result =
                        new JSONObject();


                result.put(
                        "success",
                        success
                );


                result.put(
                        "message",
                        message
                );


                if (employee != null) {

                    result.put(
                            "employee",
                            employee
                    );
                }


                sendJs(
                        "receiveEmployeeLogin",
                        result
                );

            } catch (Exception e) {

                e.printStackTrace();
            }
        }


        @JavascriptInterface
        public void logoutEmployee() {

            loggedEmployeeId =
                    "";


            loggedEmployeeName =
                    "";
        }


        /* =================================================
           STUNDENZETTEL
        ================================================= */

        @JavascriptInterface
        public void saveTimesheetToFirestore(
                String employeeId,
                String employeeName,
                String month,
                String json
        ) {

            ensureFirebaseAuth(
                    () -> {

                        String finalId =
                                adminLoggedIn
                                        ?
                                        safe(employeeId).trim()
                                        :
                                        loggedEmployeeId;


                        String finalName =
                                adminLoggedIn
                                        ?
                                        safe(employeeName).trim()
                                        :
                                        loggedEmployeeName;


                        if (
                                finalId.isEmpty()
                                        ||
                                safe(month).trim().isEmpty()
                        ) {

                            toast(
                                    "Mitarbeiter oder Monat fehlt."
                            );

                            return;
                        }


                        String documentId =
                                finalId
                                        +
                                        "_"
                                        +
                                        safe(month);


                        Map<String, Object> map =
                                new HashMap<>();


                        map.put(
                                "key",
                                documentId
                        );


                        map.put(
                                "mitarbeiterId",
                                finalId
                        );


                        map.put(
                                "mitarbeiter",
                                finalName
                        );


                        map.put(
                                "monat",
                                safe(month)
                        );


                        map.put(
                                "tageJson",
                                json == null
                                        ?
                                        "[]"
                                        :
                                        json
                        );


                        map.put(
                                "updatedAt",
                                FieldValue.serverTimestamp()
                        );


                        db()
                                .collection(
                                        "timesheets"
                                )
                                .document(
                                        documentId
                                )
                                .set(
                                        map
                                )

                                .addOnSuccessListener(
                                        unused -> {

                                            toast(
                                                    "Stundenzettel gespeichert ✅"
                                            );


                                            loadTimesheetsFromFirestore();
                                        }
                                )

                                .addOnFailureListener(
                                        e ->
                                                toast(
                                                        "Stundenzettel konnte nicht gespeichert werden: "
                                                                +
                                                                safe(
                                                                        e.getMessage()
                                                                )
                                                )
                                );
                    }
            );
        }


        @JavascriptInterface
        public void loadTimesheetsFromFirestore() {

            ensureFirebaseAuth(
                    () -> {

                        if (
                                !adminLoggedIn
                                        &&
                                loggedEmployeeId.isEmpty()
                        ) {

                            return;
                        }


                        if (adminLoggedIn) {

                            db()
                                    .collection(
                                            "timesheets"
                                    )
                                    .get()

                                    .addOnSuccessListener(
                                            this::sendTimesheets
                                    );

                        } else {

                            db()
                                    .collection(
                                            "timesheets"
                                    )
                                    .whereEqualTo(
                                            "mitarbeiterId",
                                            loggedEmployeeId
                                    )
                                    .get()

                                    .addOnSuccessListener(
                                            this::sendTimesheets
                                    );
                        }
                    }
            );
        }


        private void sendTimesheets(
                QuerySnapshot snapshots
        ) {

            JSONArray result =
                    new JSONArray();


            for (
                    QueryDocumentSnapshot doc :
                    snapshots
            ) {

                try {

                    JSONObject item =
                            new JSONObject();


                    item.put(
                            "key",
                            safe(
                                    doc.getString(
                                            "key"
                                    )
                            )
                    );


                    item.put(
                            "mitarbeiterId",
                            safe(
                                    doc.getString(
                                            "mitarbeiterId"
                                    )
                            )
                    );


                    item.put(
                            "mitarbeiter",
                            safe(
                                    doc.getString(
                                            "mitarbeiter"
                                    )
                            )
                    );


                    item.put(
                            "monat",
                            safe(
                                    doc.getString(
                                            "monat"
                                    )
                            )
                    );


                    String tageJson =
                            doc.getString(
                                    "tageJson"
                            );


                    if (
                            tageJson == null
                                    ||
                            tageJson.trim().isEmpty()
                    ) {

                        item.put(
                                "tage",
                                new JSONArray()
                        );

                    } else {

                        try {

                            item.put(
                                    "tage",
                                    new JSONArray(
                                            tageJson
                                    )
                            );

                        } catch (Exception e) {

                            item.put(
                                    "tage",
                                    new JSONArray()
                            );
                        }
                    }


                    result.put(
                            item
                    );

                } catch (Exception e) {

                    e.printStackTrace();
                }
            }


            sendJs(
                    "receiveTimesheetsFromFirestore",
                    result
            );
        }


        /* =================================================
           CHAT
        ================================================= */

        @JavascriptInterface
        public void sendChatMessage(
                String employeeId,
                String employeeName,
                String message
        ) {

            ensureFirebaseAuth(
                    () -> {

                        String text =
                                safe(message).trim();


                        if (text.isEmpty()) {

                            return;
                        }


                        String finalEmployeeId;
                        String finalEmployeeName;
                        String sender;


                        if (adminLoggedIn) {

                            finalEmployeeId =
                                    safe(employeeId);


                            finalEmployeeName =
                                    safe(employeeName);


                            sender =
                                    "admin";

                        } else {

                            finalEmployeeId =
                                    loggedEmployeeId;


                            finalEmployeeName =
                                    loggedEmployeeName;


                            sender =
                                    "employee";
                        }


                        if (finalEmployeeId.isEmpty()) {

                            return;
                        }


                        Map<String, Object> map =
                                new HashMap<>();


                        map.put(
                                "employeeId",
                                finalEmployeeId
                        );


                        map.put(
                                "employeeName",
                                finalEmployeeName
                        );


                        map.put(
                                "sender",
                                sender
                        );


                        map.put(
                                "message",
                                text
                        );


                        map.put(
                                "createdAt",
                                FieldValue.serverTimestamp()
                        );


                        db()
                                .collection(
                                        "messages"
                                )
                                .add(
                                        map
                                )

                                .addOnSuccessListener(
                                        unused ->
                                                loadChatMessages(
                                                        finalEmployeeId
                                                )
                                );
                    }
            );
        }


        @JavascriptInterface
        public void loadChatMessages(
                String employeeId
        ) {

            ensureFirebaseAuth(
                    () -> {

                        String finalId =
                                adminLoggedIn
                                        ?
                                        safe(employeeId)
                                        :
                                        loggedEmployeeId;


                        if (finalId.isEmpty()) {

                            return;
                        }


                        db()
                                .collection(
                                        "messages"
                                )
                                .whereEqualTo(
                                        "employeeId",
                                        finalId
                                )
                                .get()

                                .addOnSuccessListener(
                                        snapshots -> {

                                            JSONArray result =
                                                    new JSONArray();


                                            for (
                                                    QueryDocumentSnapshot doc :
                                                    snapshots
                                            ) {

                                                try {

                                                    JSONObject item =
                                                            new JSONObject();


                                                    item.put(
                                                            "id",
                                                            doc.getId()
                                                    );


                                                    item.put(
                                                            "employeeId",
                                                            safe(
                                                                    doc.getString(
                                                                            "employeeId"
                                                                    )
                                                            )
                                                    );


                                                    item.put(
                                                            "employeeName",
                                                            safe(
                                                                    doc.getString(
                                                                            "employeeName"
                                                                    )
                                                            )
                                                    );


                                                    item.put(
                                                            "sender",
                                                            safe(
                                                                    doc.getString(
                                                                            "sender"
                                                                    )
                                                            )
                                                    );


                                                    item.put(
                                                            "message",
                                                            safe(
                                                                    doc.getString(
                                                                            "message"
                                                                    )
                                                            )
                                                    );


                                                    item.put(
                                                            "createdAt",
                                                            timestampMillis(
                                                                    doc,
                                                                    "createdAt"
                                                            )
                                                    );


                                                    result.put(
                                                            item
                                                    );

                                                } catch (Exception e) {

                                                    e.printStackTrace();
                                                }
                                            }


                                            sendJs(
                                                    "receiveChatMessagesFromFirestore",
                                                    result
                                            );
                                        }
                                );
                    }
            );
        }


        /* =================================================
           TICKETS
        ================================================= */

        @JavascriptInterface
        public void saveTicket(
                String employeeId,
                String employeeName,
                String type,
                String subject,
                String description
        ) {

            ensureFirebaseAuth(
                    () -> {

                        String finalId =
                                adminLoggedIn
                                        ?
                                        safe(employeeId)
                                        :
                                        loggedEmployeeId;


                        String finalName =
                                adminLoggedIn
                                        ?
                                        safe(employeeName)
                                        :
                                        loggedEmployeeName;


                        if (
                                finalId.isEmpty()
                                        ||
                                safe(subject).trim().isEmpty()
                        ) {

                            toast(
                                    "Bitte Betreff eingeben."
                            );

                            return;
                        }


                        Map<String, Object> map =
                                new HashMap<>();


                        map.put(
                                "employeeId",
                                finalId
                        );


                        map.put(
                                "employeeName",
                                finalName
                        );


                        map.put(
                                "type",
                                safe(type)
                        );


                        map.put(
                                "subject",
                                safe(subject)
                        );


                        map.put(
                                "description",
                                safe(description)
                        );


                        map.put(
                                "status",
                                "Offen"
                        );


                        map.put(
                                "createdAt",
                                FieldValue.serverTimestamp()
                        );


                        db()
                                .collection(
                                        "tickets"
                                )
                                .add(
                                        map
                                )

                                .addOnSuccessListener(
                                        unused -> {

                                            toast(
                                                    "Ticket gesendet ✅"
                                            );


                                            loadTickets(
                                                    finalId
                                            );
                                        }
                                );
                    }
            );
        }


        @JavascriptInterface
        public void loadTickets(
                String employeeId
        ) {

            ensureFirebaseAuth(
                    () -> {

                        if (adminLoggedIn) {

                            db()
                                    .collection(
                                            "tickets"
                                    )
                                    .get()

                                    .addOnSuccessListener(
                                            this::sendTickets
                                    );

                        } else {

                            if (loggedEmployeeId.isEmpty()) {

                                return;
                            }


                            db()
                                    .collection(
                                            "tickets"
                                    )
                                    .whereEqualTo(
                                            "employeeId",
                                            loggedEmployeeId
                                    )
                                    .get()

                                    .addOnSuccessListener(
                                            this::sendTickets
                                    );
                        }
                    }
            );
        }


        private void sendTickets(
                QuerySnapshot snapshots
        ) {

            JSONArray result =
                    new JSONArray();


            for (
                    QueryDocumentSnapshot doc :
                    snapshots
            ) {

                try {

                    JSONObject item =
                            new JSONObject();


                    item.put(
                            "id",
                            doc.getId()
                    );


                    item.put(
                            "employeeId",
                            safe(
                                    doc.getString(
                                            "employeeId"
                                    )
                            )
                    );


                    item.put(
                            "employeeName",
                            safe(
                                    doc.getString(
                                            "employeeName"
                                    )
                            )
                    );


                    item.put(
                            "type",
                            safe(
                                    doc.getString(
                                            "type"
                                    )
                            )
                    );


                    item.put(
                            "subject",
                            safe(
                                    doc.getString(
                                            "subject"
                                    )
                            )
                    );


                    item.put(
                            "description",
                            safe(
                                    doc.getString(
                                            "description"
                                    )
                            )
                    );


                    item.put(
                            "status",
                            safe(
                                    doc.getString(
                                            "status"
                                    )
                            )
                    );


                    item.put(
                            "createdAt",
                            timestampMillis(
                                    doc,
                                    "createdAt"
                            )
                    );


                    result.put(
                            item
                    );

                } catch (Exception e) {

                    e.printStackTrace();
                }
            }


            sendJs(
                    "receiveTicketsFromFirestore",
                    result
            );
        }


        @JavascriptInterface
        public void updateTicketStatus(
                String ticketId,
                String status
        ) {

            if (!adminLoggedIn) {

                return;
            }


            db()
                    .collection(
                            "tickets"
                    )
                    .document(
                            safe(ticketId)
                    )
                    .update(
                            "status",
                            safe(status)
                    )

                    .addOnSuccessListener(
                            unused -> {

                                toast(
                                        "Ticket aktualisiert ✅"
                                );


                                loadTickets(
                                        ""
                                );
                            }
                    );
        }


        /* =================================================
           URLAUB
        ================================================= */

        @JavascriptInterface
        public void saveLeaveRequest(
                String employeeId,
                String employeeName,
                String from,
                String to,
                String note
        ) {

            ensureFirebaseAuth(
                    () -> {

                        String finalId =
                                adminLoggedIn
                                        ?
                                        safe(employeeId)
                                        :
                                        loggedEmployeeId;


                        String finalName =
                                adminLoggedIn
                                        ?
                                        safe(employeeName)
                                        :
                                        loggedEmployeeName;


                        if (
                                finalId.isEmpty()
                                        ||
                                safe(from).isEmpty()
                                        ||
                                safe(to).isEmpty()
                        ) {

                            toast(
                                    "Bitte Zeitraum auswählen."
                            );

                            return;
                        }


                        Map<String, Object> map =
                                new HashMap<>();


                        map.put(
                                "employeeId",
                                finalId
                        );


                        map.put(
                                "employeeName",
                                finalName
                        );


                        map.put(
                                "from",
                                safe(from)
                        );


                        map.put(
                                "to",
                                safe(to)
                        );


                        map.put(
                                "note",
                                safe(note)
                        );


                        map.put(
                                "status",
                                "Offen"
                        );


                        map.put(
                                "createdAt",
                                FieldValue.serverTimestamp()
                        );


                        db()
                                .collection(
                                        "leaveRequests"
                                )
                                .add(
                                        map
                                )

                                .addOnSuccessListener(
                                        unused -> {

                                            toast(
                                                    "Urlaubsantrag gesendet ✅"
                                            );


                                            loadLeaveRequests(
                                                    finalId
                                            );
                                        }
                                );
                    }
            );
        }


        @JavascriptInterface
        public void loadLeaveRequests(
                String employeeId
        ) {

            ensureFirebaseAuth(
                    () -> {

                        if (adminLoggedIn) {

                            db()
                                    .collection(
                                            "leaveRequests"
                                    )
                                    .get()

                                    .addOnSuccessListener(
                                            this::sendLeaveRequests
                                    );

                        } else {

                            if (loggedEmployeeId.isEmpty()) {

                                return;
                            }


                            db()
                                    .collection(
                                            "leaveRequests"
                                    )
                                    .whereEqualTo(
                                            "employeeId",
                                            loggedEmployeeId
                                    )
                                    .get()

                                    .addOnSuccessListener(
                                            this::sendLeaveRequests
                                    );
                        }
                    }
            );
        }


        private void sendLeaveRequests(
                QuerySnapshot snapshots
        ) {

            JSONArray result =
                    new JSONArray();


            for (
                    QueryDocumentSnapshot doc :
                    snapshots
            ) {

                try {

                    JSONObject item =
                            new JSONObject();


                    item.put(
                            "id",
                            doc.getId()
                    );


                    item.put(
                            "employeeId",
                            safe(
                                    doc.getString(
                                            "employeeId"
                                    )
                            )
                    );


                    item.put(
                            "employeeName",
                            safe(
                                    doc.getString(
                                            "employeeName"
                                    )
                            )
                    );


                    item.put(
                            "from",
                            safe(
                                    doc.getString(
                                            "from"
                                    )
                            )
                    );


                    item.put(
                            "to",
                            safe(
                                    doc.getString(
                                            "to"
                                    )
                            )
                    );


                    item.put(
                            "note",
                            safe(
                                    doc.getString(
                                            "note"
                                    )
                            )
                    );


                    item.put(
                            "status",
                            safe(
                                    doc.getString(
                                            "status"
                                    )
                            )
                    );


                    item.put(
                            "createdAt",
                            timestampMillis(
                                    doc,
                                    "createdAt"
                            )
                    );


                    result.put(
                            item
                    );

                } catch (Exception e) {

                    e.printStackTrace();
                }
            }


            sendJs(
                    "receiveLeaveRequestsFromFirestore",
                    result
            );
        }


        @JavascriptInterface
        public void updateLeaveStatus(
                String requestId,
                String status
        ) {

            if (!adminLoggedIn) {

                return;
            }


            db()
                    .collection(
                            "leaveRequests"
                    )
                    .document(
                            safe(requestId)
                    )
                    .update(
                            "status",
                            safe(status)
                    )

                    .addOnSuccessListener(
                            unused -> {

                                toast(
                                        "Urlaubsantrag aktualisiert ✅"
                                );


                                loadLeaveRequests(
                                        ""
                                );
                            }
                    );
        }


        /* =================================================
           AUFTRÄGE
        ================================================= */

        @JavascriptInterface
        public void saveOrderToFirestore(
                String customer,
                String address,
                String service,
                String date,
                String price,
                String description
        ) {

            if (!adminLoggedIn) {

                return;
            }


            Map<String, Object> map =
                    new HashMap<>();


            map.put(
                    "customer",
                    safe(customer)
            );


            map.put(
                    "address",
                    safe(address)
            );


            map.put(
                    "service",
                    safe(service)
            );


            map.put(
                    "date",
                    safe(date)
            );


            map.put(
                    "price",
                    safe(price)
            );


            map.put(
                    "description",
                    safe(description)
            );


            map.put(
                    "createdAt",
                    FieldValue.serverTimestamp()
            );


            db()
                    .collection(
                            "orders"
                    )
                    .add(
                            map
                    )

                    .addOnSuccessListener(
                            unused ->
                                    loadOrdersFromFirestore()
                    );
        }


        @JavascriptInterface
        public void loadOrdersFromFirestore() {

            if (!adminLoggedIn) {

                return;
            }


            db()
                    .collection(
                            "orders"
                    )
                    .get()

                    .addOnSuccessListener(
                            snapshots -> {

                                JSONArray result =
                                        new JSONArray();


                                for (
                                        QueryDocumentSnapshot doc :
                                        snapshots
                                ) {

                                    try {

                                        JSONObject item =
                                                new JSONObject();


                                        item.put(
                                                "kunde",
                                                safe(
                                                        doc.getString(
                                                                "customer"
                                                        )
                                                )
                                        );


                                        item.put(
                                                "ort",
                                                safe(
                                                        doc.getString(
                                                                "address"
                                                        )
                                                )
                                        );


                                        item.put(
                                                "art",
                                                safe(
                                                        doc.getString(
                                                                "service"
                                                        )
                                                )
                                        );


                                        item.put(
                                                "datum",
                                                safe(
                                                        doc.getString(
                                                                "date"
                                                        )
                                                )
                                        );


                                        item.put(
                                                "preis",
                                                safe(
                                                        doc.getString(
                                                                "price"
                                                        )
                                                )
                                        );


                                        item.put(
                                                "notiz",
                                                safe(
                                                        doc.getString(
                                                                "description"
                                                        )
                                                )
                                        );


                                        result.put(
                                                item
                                        );

                                    } catch (Exception e) {

                                        e.printStackTrace();
                                    }
                                }


                                sendJs(
                                        "receiveOrdersFromFirestore",
                                        result
                                );
                            }
                    );
        }


        /* =================================================
           MATERIALIEN
        ================================================= */

        @JavascriptInterface
        public void saveMaterialToFirestore(
                String id,
                String name,
                String manufacturer,
                String category,
                String use,
                String productUrl,
                String safetyUrl
        ) {

            if (!adminLoggedIn) {

                return;
            }


            String finalId =
                    safe(id).trim();


            if (finalId.isEmpty()) {

                finalId =
                        String.valueOf(
                                System.currentTimeMillis()
                        );
            }


            Map<String, Object> map =
                    new HashMap<>();


            map.put(
                    "id",
                    finalId
            );


            map.put(
                    "name",
                    safe(name)
            );


            map.put(
                    "manufacturer",
                    safe(manufacturer)
            );


            map.put(
                    "category",
                    safe(category)
            );


            map.put(
                    "use",
                    safe(use)
            );


            map.put(
                    "productUrl",
                    safe(productUrl)
            );


            map.put(
                    "safetyUrl",
                    safe(safetyUrl)
            );


            map.put(
                    "updatedAt",
                    FieldValue.serverTimestamp()
            );


            db()
                    .collection(
                            "materials"
                    )
                    .document(
                            finalId
                    )
                    .set(
                            map
                    )

                    .addOnSuccessListener(
                            unused -> {

                                toast(
                                        "Material gespeichert ✅"
                                );


                                loadMaterialsFromFirestore();
                            }
                    );
        }


        @JavascriptInterface
        public void loadMaterialsFromFirestore() {

            ensureFirebaseAuth(
                    () ->

                            db()
                                    .collection(
                                            "materials"
                                    )
                                    .get()

                                    .addOnSuccessListener(
                                            snapshots -> {

                                                JSONArray result =
                                                        new JSONArray();


                                                for (
                                                        QueryDocumentSnapshot doc :
                                                        snapshots
                                                ) {

                                                    try {

                                                        JSONObject item =
                                                                new JSONObject();


                                                        String id =
                                                                safe(
                                                                        doc.getString(
                                                                                "id"
                                                                        )
                                                                );


                                                        if (id.isEmpty()) {

                                                            id =
                                                                    doc.getId();
                                                        }


                                                        item.put(
                                                                "id",
                                                                id
                                                        );


                                                        item.put(
                                                                "name",
                                                                safe(
                                                                        doc.getString(
                                                                                "name"
                                                                        )
                                                                )
                                                        );


                                                        item.put(
                                                                "manufacturer",
                                                                safe(
                                                                        doc.getString(
                                                                                "manufacturer"
                                                                        )
                                                                )
                                                        );


                                                        item.put(
                                                                "category",
                                                                safe(
                                                                        doc.getString(
                                                                                "category"
                                                                        )
                                                                )
                                                        );


                                                        item.put(
                                                                "use",
                                                                safe(
                                                                        doc.getString(
                                                                                "use"
                                                                        )
                                                                )
                                                        );


                                                        item.put(
                                                                "productUrl",
                                                                safe(
                                                                        doc.getString(
                                                                                "productUrl"
                                                                        )
                                                                )
                                                        );


                                                        item.put(
                                                                "safetyUrl",
                                                                safe(
                                                                        doc.getString(
                                                                                "safetyUrl"
                                                                        )
                                                                )
                                                        );


                                                        result.put(
                                                                item
                                                        );

                                                    } catch (Exception e) {

                                                        e.printStackTrace();
                                                    }
                                                }


                                                sendJs(
                                                        "receiveMaterialsFromFirestore",
                                                        result
                                                );
                                            }
                                    )
            );
        }


        @JavascriptInterface
        public void deleteMaterialFromFirestore(
                String id
        ) {

            if (!adminLoggedIn) {

                return;
            }


            db()
                    .collection(
                            "materials"
                    )
                    .document(
                            safe(id)
                    )
                    .delete()

                    .addOnSuccessListener(
                            unused -> {

                                toast(
                                        "Material gelöscht."
                                );


                                loadMaterialsFromFirestore();
                            }
                    );
        }


        /* =================================================
           DOKUMENTE - NEUER DATEI-UPLOAD
        ================================================= */

        @JavascriptInterface
        public void uploadDocument(
                String id,
                String title,
                String type,
                String employeeId,
                String employeeName,
                String note
        ) {

            if (!adminLoggedIn) {

                toast(
                        "Nur die Verwaltung darf Dokumente hochladen."
                );

                return;
            }


            String cleanTitle =
                    safe(title).trim();


            if (cleanTitle.isEmpty()) {

                toast(
                        "Bitte zuerst einen Dokumenttitel eingeben."
                );

                return;
            }


            pendingDocumentId =
                    safe(id).trim();


            if (pendingDocumentId.isEmpty()) {

                pendingDocumentId =
                        String.valueOf(
                                System.currentTimeMillis()
                        );
            }


            pendingDocumentTitle =
                    cleanTitle;


            pendingDocumentType =
                    safe(type);


            pendingDocumentEmployeeId =
                    safe(employeeId).trim();


            pendingDocumentEmployeeName =
                    safe(employeeName).trim();


            pendingDocumentNote =
                    safe(note);


            openDocumentPicker();
        }


        /*
         * Alte Methode bleibt bestehen,
         * falls alte Dokumente mit URL vorhanden sind.
         */
        @JavascriptInterface
        public void saveDocumentToFirestore(
                String id,
                String title,
                String type,
                String employeeId,
                String employeeName,
                String url,
                String note
        ) {

            if (!adminLoggedIn) {

                return;
            }


            String finalId =
                    safe(id).trim();


            if (finalId.isEmpty()) {

                finalId =
                        String.valueOf(
                                System.currentTimeMillis()
                        );
            }


            Map<String, Object> map =
                    new HashMap<>();


            map.put(
                    "id",
                    finalId
            );


            map.put(
                    "title",
                    safe(title)
            );


            map.put(
                    "type",
                    safe(type)
            );


            map.put(
                    "employeeId",
                    safe(employeeId)
            );


            map.put(
                    "employeeName",
                    safe(employeeName)
            );


            map.put(
                    "url",
                    safe(url)
            );


            map.put(
                    "note",
                    safe(note)
            );


            map.put(
                    "updatedAt",
                    FieldValue.serverTimestamp()
            );


            db()
                    .collection(
                            "companyDocuments"
                    )
                    .document(
                            finalId
                    )
                    .set(
                            map
                    )

                    .addOnSuccessListener(
                            unused -> {

                                toast(
                                        "Dokument gespeichert ✅"
                                );


                                loadDocumentsFromFirestore(
                                        ""
                                );
                            }
                    );
        }


        @JavascriptInterface
        public void loadDocumentsFromFirestore(
                String employeeId
        ) {

            ensureFirebaseAuth(
                    () -> {

                        if (adminLoggedIn) {

                            db()
                                    .collection(
                                            "companyDocuments"
                                    )
                                    .get()

                                    .addOnSuccessListener(
                                            this::sendDocuments
                                    );

                        } else {

                            if (loggedEmployeeId.isEmpty()) {

                                return;
                            }


                            db()
                                    .collection(
                                            "companyDocuments"
                                    )
                                    .get()

                                    .addOnSuccessListener(
                                            snapshots -> {

                                                JSONArray result =
                                                        new JSONArray();


                                                for (
                                                        QueryDocumentSnapshot doc :
                                                        snapshots
                                                ) {

                                                    String assigned =
                                                            safe(
                                                                    doc.getString(
                                                                            "employeeId"
                                                                    )
                                                            );


                                                    if (
                                                            !assigned.isEmpty()
                                                                    &&
                                                            !assigned.equals(
                                                                    loggedEmployeeId
                                                            )
                                                    ) {

                                                        continue;
                                                    }


                                                    try {

                                                        result.put(
                                                                documentJson(
                                                                        doc
                                                                )
                                                        );

                                                    } catch (Exception e) {

                                                        e.printStackTrace();
                                                    }
                                                }


                                                sendJs(
                                                        "receiveDocumentsFromFirestore",
                                                        result
                                                );
                                            }
                                    );
                        }
                    }
            );
        }


        private void sendDocuments(
                QuerySnapshot snapshots
        ) {

            JSONArray result =
                    new JSONArray();


            for (
                    QueryDocumentSnapshot doc :
                    snapshots
            ) {

                try {

                    result.put(
                            documentJson(
                                    doc
                            )
                    );

                } catch (Exception e) {

                    e.printStackTrace();
                }
            }


            sendJs(
                    "receiveDocumentsFromFirestore",
                    result
            );
        }


        private JSONObject documentJson(
                QueryDocumentSnapshot doc
        ) throws Exception {

            JSONObject item =
                    new JSONObject();


            String id =
                    safe(
                            doc.getString(
                                    "id"
                            )
                    );


            if (id.isEmpty()) {

                id =
                        doc.getId();
            }


            item.put(
                    "id",
                    id
            );


            item.put(
                    "title",
                    safe(
                            doc.getString(
                                    "title"
                            )
                    )
            );


            item.put(
                    "type",
                    safe(
                            doc.getString(
                                    "type"
                            )
                    )
            );


            item.put(
                    "employeeId",
                    safe(
                            doc.getString(
                                    "employeeId"
                            )
                    )
            );


            item.put(
                    "employeeName",
                    safe(
                            doc.getString(
                                    "employeeName"
                            )
                    )
            );


            item.put(
                    "note",
                    safe(
                            doc.getString(
                                    "note"
                            )
                    )
            );


            item.put(
                    "url",
                    safe(
                            doc.getString(
                                    "url"
                            )
                    )
            );


            item.put(
                    "storagePath",
                    safe(
                            doc.getString(
                                    "storagePath"
                            )
                    )
            );


            item.put(
                    "fileName",
                    safe(
                            doc.getString(
                                    "fileName"
                            )
                    )
            );


            item.put(
                    "mimeType",
                    safe(
                            doc.getString(
                                    "mimeType"
                            )
                    )
            );


            return item;
        }


        @JavascriptInterface
        public void openDocument(
                String storagePath,
                String fileName,
                String mimeType,
                String fallbackUrl
        ) {

            String path =
                    safe(storagePath).trim();


            if (!path.isEmpty()) {

                openStoredDocument(
                        path,
                        fileName,
                        mimeType
                );

                return;
            }


            /*
             * Alte Dokumente mit Link weiterhin öffnen.
             */
            openExternalUrl(
                    fallbackUrl
            );
        }


        @JavascriptInterface
        public void deleteDocumentFromFirestore(
                String id
        ) {

            if (!adminLoggedIn) {

                return;
            }


            final String documentId =
                    safe(id);


            db()
                    .collection(
                            "companyDocuments"
                    )
                    .document(
                            documentId
                    )
                    .get()

                    .addOnSuccessListener(
                            snapshot -> {

                                String storagePath =
                                        safe(
                                                snapshot.getString(
                                                        "storagePath"
                                                )
                                        );


                                Runnable deleteFirestore =
                                        () ->

                                                db()
                                                        .collection(
                                                                "companyDocuments"
                                                        )
                                                        .document(
                                                                documentId
                                                        )
                                                        .delete()

                                                        .addOnSuccessListener(
                                                                unused -> {

                                                                    toast(
                                                                            "Dokument gelöscht."
                                                                    );


                                                                    loadDocumentsFromFirestore(
                                                                            ""
                                                                    );
                                                                }
                                                        );


                                if (storagePath.isEmpty()) {

                                    deleteFirestore.run();

                                    return;
                                }


                                storage()
                                        .getReference()
                                        .child(
                                                storagePath
                                        )
                                        .delete()

                                        .addOnCompleteListener(
                                                task ->
                                                        deleteFirestore.run()
                                        );
                            }
                    );
        }


        /* =================================================
           EXTERNE LINKS
        ================================================= */

        @JavascriptInterface
        public void openExternalUrl(
                String url
        ) {

            String value =
                    safe(url).trim();


            if (value.isEmpty()) {

                toast(
                        "Kein Dokument hinterlegt."
                );

                return;
            }


            runOnUiThread(
                    () -> {

                        try {

                            Uri uri =
                                    Uri.parse(
                                            value
                                    );


                            Intent intent =
                                    new Intent(
                                            Intent.ACTION_VIEW,
                                            uri
                                    );


                            startActivity(
                                    intent
                            );

                        } catch (Exception e) {

                            toast(
                                    "Dokument konnte nicht geöffnet werden."
                            );
                        }
                    }
            );
        }


        /* =================================================
           AR
        ================================================= */

        @JavascriptInterface
        public void startArMeasurement() {

            if (!adminLoggedIn) {

                return;
            }


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


                            toast(
                                    "AR-Messung konnte nicht geöffnet werden."
                            );
                        }
                    }
            );
        }


        /* =================================================
           PDF
        ================================================= */

        @JavascriptInterface
        public void createTimesheetPdf(
                String employee,
                String month,
                String jsonData
        ) {

            if (
                    !adminLoggedIn
                            &&
                    loggedEmployeeId.isEmpty()
            ) {

                toast(
                        "Bitte zuerst anmelden."
                );

                return;
            }


            runOnUiThread(
                    () -> {

                        pendingPdfEmployee =
                                adminLoggedIn
                                        ?
                                        safe(employee).trim()
                                        :
                                        safe(
                                                loggedEmployeeName
                                        ).trim();


                        if (pendingPdfEmployee.isEmpty()) {

                            pendingPdfEmployee =
                                    "Mitarbeiter";
                        }


                        pendingPdfMonth =
                                safe(month).trim();


                        if (pendingPdfMonth.isEmpty()) {

                            toast(
                                    "Bitte zuerst einen Monat auswählen."
                            );

                            return;
                        }


                        pendingPdfJson =
                                jsonData == null
                                        ?
                                        "[]"
                                        :
                                        jsonData;


                        try {

                            JSONArray test =
                                    new JSONArray(
                                            pendingPdfJson
                                    );


                            if (test.length() == 0) {

                                toast(
                                        "Der Stundenzettel enthält keine Einträge."
                                );

                                return;
                            }

                        } catch (Exception e) {

                            toast(
                                    "Stundenzettel konnte nicht gelesen werden."
                            );

                            return;
                        }


                        String safeEmployee =
                                pendingPdfEmployee
                                        .replaceAll(
                                                "[^a-zA-Z0-9ÄÖÜäöüß_-]",
                                                "_"
                                        );


                        String safeMonth =
                                pendingPdfMonth
                                        .replaceAll(
                                                "[^0-9_-]",
                                                "_"
                                        );


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
                                "Stundenzettel_"
                                        +
                                        safeEmployee
                                        +
                                        "_"
                                        +
                                        safeMonth
                                        +
                                        ".pdf"
                        );


                        try {

                            startActivityForResult(
                                    intent,
                                    PDF_CREATE_REQUEST
                            );

                        } catch (Exception e) {

                            toast(
                                    "PDF-Datei konnte nicht erstellt werden."
                            );
                        }
                    }
            );
        }


        @JavascriptInterface
        public void shareTimesheetPdf(
                String employee,
                String month,
                String jsonData
        ) {

            createTimesheetPdf(
                    employee,
                    month,
                    jsonData
            );
        }


        @JavascriptInterface
        public void shareLastPdf() {

            if (
                    !adminLoggedIn
                            &&
                    loggedEmployeeId.isEmpty()
            ) {

                return;
            }


            runOnUiThread(
                    () -> {

                        if (lastSavedPdfUri == null) {

                            toast(
                                    "Es wurde noch keine PDF erstellt."
                            );

                            return;
                        }


                        sharePdf(
                                lastSavedPdfUri
                        );
                    }
            );
        }
    }


    /* =====================================================
       ACTIVITY RESULT
    ===================================================== */

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
                DOCUMENT_PICK_REQUEST
        ) {

            if (
                    resultCode == RESULT_OK
                            &&
                    data != null
                            &&
                    data.getData() != null
            ) {

                Uri uri =
                        data.getData();


                try {

                    getContentResolver()
                            .takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );

                } catch (Exception ignored) {

                }


                uploadDocumentFile(
                        uri
                );

            } else {

                clearPendingDocument();
            }


            return;
        }


        if (
                requestCode
                        ==
                FILE_CHOOSER_REQUEST
        ) {

            if (filePathCallback == null) {

                return;
            }


            Uri[] results =
                    null;


            if (resultCode == RESULT_OK) {

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

                lastSavedPdfUri =
                        data.getData();


                createPdfFile(
                        lastSavedPdfUri
                );
            }


            return;
        }


        if (
                requestCode
                        ==
                AR_MEASURE_REQUEST
        ) {

            arMeasurementRunning =
                    false;


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


                String javascript =

                        "if(typeof window.receiveArMeasurement==='function'){"
                                +
                                "window.receiveArMeasurement("
                                +
                                width
                                +
                                ","
                                +
                                height
                                +
                                ","
                                +
                                area
                                +
                                ");"
                                +
                                "}";


                if (webView != null) {

                    webView.postDelayed(
                            () ->
                                    webView.evaluateJavascript(
                                            javascript,
                                            null
                                    ),
                            200
                    );
                }
            }
        }
    }


    /* =====================================================
       PDF GENERATOR
    ===================================================== */

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


            final int pageWidth =
                    595;


            final int pageHeight =
                    842;


            final int margin =
                    28;


            Paint green =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );


            green.setColor(
                    Color.rgb(
                            7,
                            138,
                            101
                    )
            );


            green.setTextSize(
                    21f
            );


            green.setFakeBoldText(
                    true
            );


            Paint bold =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );


            bold.setColor(
                    Color.BLACK
            );


            bold.setTextSize(
                    9f
            );


            bold.setFakeBoldText(
                    true
            );


            Paint normal =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );


            normal.setColor(
                    Color.BLACK
            );


            normal.setTextSize(
                    8f
            );


            Paint line =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );


            line.setColor(
                    Color.LTGRAY
            );


            Paint totalPaint =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );


            totalPaint.setColor(
                    Color.rgb(
                            7,
                            138,
                            101
                    )
            );


            totalPaint.setTextSize(
                    16f
            );


            totalPaint.setFakeBoldText(
                    true
            );


            double total =
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


                if (
                        "Arbeit".equalsIgnoreCase(
                                row.optString(
                                        "status"
                                )
                        )
                ) {

                    total +=
                            row.optDouble(
                                    "stunden",
                                    0
                            );
                }
            }


            int pageNo =
                    1;


            int index =
                    0;


            boolean first =
                    true;


            while (
                    index < rows.length()
                            ||
                    first
            ) {

                first =
                        false;


                PdfDocument.Page page =
                        document.startPage(

                                new PdfDocument
                                        .PageInfo
                                        .Builder(
                                                pageWidth,
                                                pageHeight,
                                                pageNo
                                        )
                                        .create()
                        );


                Canvas canvas =
                        page.getCanvas();


                float y =
                        38;


                canvas.drawText(
                        "E-M Cleaning Service",
                        margin,
                        y,
                        green
                );


                y +=
                        25;


                canvas.drawText(
                        "Stundenzettel",
                        margin,
                        y,
                        bold
                );


                y +=
                        15;


                canvas.drawText(
                        "Mitarbeiter: "
                                +
                        pendingPdfEmployee,
                        margin,
                        y,
                        bold
                );


                y +=
                        15;


                canvas.drawText(
                        "Monat: "
                                +
                        pendingPdfMonth,
                        margin,
                        y,
                        bold
                );


                y +=
                        25;


                float xDate =
                        margin;


                float xStatus =
                        92;


                float xStart =
                        155;


                float xEnd =
                        205;


                float xPause =
                        255;


                float xHours =
                        305;


                float xObject =
                        355;


                canvas.drawText(
                        "Datum",
                        xDate,
                        y,
                        bold
                );


                canvas.drawText(
                        "Status",
                        xStatus,
                        y,
                        bold
                );


                canvas.drawText(
                        "Start",
                        xStart,
                        y,
                        bold
                );


                canvas.drawText(
                        "Ende",
                        xEnd,
                        y,
                        bold
                );


                canvas.drawText(
                        "Pause",
                        xPause,
                        y,
                        bold
                );


                canvas.drawText(
                        "Std.",
                        xHours,
                        y,
                        bold
                );


                canvas.drawText(
                        "Objekt / Kunde",
                        xObject,
                        y,
                        bold
                );


                y +=
                        11;


                canvas.drawLine(
                        margin,
                        y,
                        pageWidth - margin,
                        y,
                        line
                );


                y +=
                        15;


                while (
                        index < rows.length()
                                &&
                        y < 720
                ) {

                    JSONObject row =
                            rows.getJSONObject(
                                    index
                            );


                    String datum =
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


                    String ende =
                            row.optString(
                                    "ende"
                            );


                    String pause =
                            String.valueOf(
                                    row.optInt(
                                            "pause",
                                            0
                                    )
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


                    canvas.drawText(
                            shorten(
                                    datum,
                                    10
                            ),
                            xDate,
                            y,
                            normal
                    );


                    canvas.drawText(
                            shorten(
                                    status,
                                    9
                            ),
                            xStatus,
                            y,
                            normal
                    );


                    canvas.drawText(
                            shorten(
                                    start,
                                    5
                            ),
                            xStart,
                            y,
                            normal
                    );


                    canvas.drawText(
                            shorten(
                                    ende,
                                    5
                            ),
                            xEnd,
                            y,
                            normal
                    );


                    canvas.drawText(
                            pause,
                            xPause,
                            y,
                            normal
                    );


                    canvas.drawText(
                            String.format(
                                    Locale.GERMANY,
                                    "%.2f",
                                    hours
                            ),
                            xHours,
                            y,
                            normal
                    );


                    canvas.drawText(
                            shorten(
                                    object,
                                    30
                            ),
                            xObject,
                            y,
                            normal
                    );


                    y +=
                            18;


                    canvas.drawLine(
                            margin,
                            y - 7,
                            pageWidth - margin,
                            y - 7,
                            line
                    );


                    index++;
                }


                if (
                        index >= rows.length()
                ) {

                    y +=
                            24;


                    canvas.drawText(
                            "Gesamtstunden: "
                                    +
                            String.format(
                                    Locale.GERMANY,
                                    "%.2f",
                                    total
                            )
                                    +
                            " Stunden",
                            margin,
                            y,
                            totalPaint
                    );
                }


                canvas.drawText(
                        "Seite "
                                +
                        pageNo,
                        pageWidth - 70,
                        pageHeight - 20,
                        normal
                );


                document.finishPage(
                        page
                );


                pageNo++;
            }


            outputStream =
                    getContentResolver()
                            .openOutputStream(
                                    uri
                            );


            if (outputStream == null) {

                throw new Exception(
                        "PDF-Datei konnte nicht geöffnet werden."
                );
            }


            document.writeTo(
                    outputStream
            );


            outputStream.flush();


            toast(
                    "PDF gespeichert ✅"
            );


            showShareDialog(
                    uri
            );

        } catch (Exception e) {

            toast(
                    "PDF-Fehler: "
                            +
                    safe(
                            e.getMessage()
                    )
            );

        } finally {

            try {

                if (outputStream != null) {

                    outputStream.close();
                }

            } catch (Exception ignored) {

            }


            document.close();
        }
    }


    private void showShareDialog(
            Uri uri
    ) {

        runOnUiThread(
                () ->

                        new AlertDialog.Builder(
                                MainActivity.this
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
                                        "Teilen",
                                        (dialog, which) ->
                                                sharePdf(
                                                        uri
                                                )
                                )

                                .show()
        );
    }


    private void sharePdf(
            Uri uri
    ) {

        if (uri == null) {

            toast(
                    "Keine PDF zum Teilen vorhanden."
            );

            return;
        }


        try {

            Intent intent =
                    new Intent(
                            Intent.ACTION_SEND
                    );


            intent.setType(
                    "application/pdf"
            );


            intent.putExtra(
                    Intent.EXTRA_STREAM,
                    uri
            );


            intent.putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Stundenzettel "
                            +
                    pendingPdfEmployee
                            +
                    " "
                            +
                    pendingPdfMonth
            );


            intent.putExtra(
                    Intent.EXTRA_TEXT,
                    "Stundenzettel von "
                            +
                    pendingPdfEmployee
                            +
                    " für "
                            +
                    pendingPdfMonth
            );


            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );


            startActivity(
                    Intent.createChooser(
                            intent,
                            "Stundenzettel teilen"
                    )
            );

        } catch (Exception e) {

            toast(
                    "PDF konnte nicht geteilt werden."
            );
        }
    }


    private String shorten(
            String value,
            int max
    ) {

        if (value == null) {

            return "";
        }


        if (value.length() <= max) {

            return value;
        }


        return value.substring(
                0,
                Math.max(
                        0,
                        max - 1
                )
        )
                +
        "…";
    }


    /* =====================================================
       WEBVIEW STATE
    ===================================================== */

    @Override
    protected void onSaveInstanceState(
            Bundle outState
    ) {

        if (webView != null) {

            webView.saveState(
                    outState
            );
        }


        super.onSaveInstanceState(
                outState
        );
    }


    /* =====================================================
       BACK
    ===================================================== */

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

                "if(typeof window.androidBack==='function'){window.androidBack();}else{false;}",

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


                    if (webView.canGoBack()) {

                        webView.goBack();

                    } else {

                        MainActivity.super.onBackPressed();
                    }
                }
        );
    }


    /* =====================================================
       DESTROY
    ===================================================== */

    @Override
    protected void onDestroy() {

        if (webView != null) {

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
