package de.emcleaning.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.DepthPoint;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Session;

import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MeasureActivity extends Activity
        implements GLSurfaceView.Renderer {

    private static final int CAMERA_PERMISSION = 7001;

    private FrameLayout root;
    private GLSurfaceView surfaceView;

    private Session session;
    private Frame latestFrame;

    private CameraRenderer cameraRenderer;

    private boolean cameraTextureSet = false;
    private boolean userRequestedInstall = true;

    private final List<Anchor> anchors = new ArrayList<>();
    private final List<View> markers = new ArrayList<>();

    private TextView statusText;
    private TextView resultText;

    private Button resetButton;
    private Button useButton;

    private double widthMeters = 0;
    private double heightMeters = 0;
    private double areaMeters = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildUi();
    }


    private void buildUi() {

        root = new FrameLayout(this);

        surfaceView = new GLSurfaceView(this);

        surfaceView.setEGLContextClientVersion(2);

        surfaceView.setPreserveEGLContextOnPause(true);

        surfaceView.setRenderer(this);

        surfaceView.setRenderMode(
                GLSurfaceView.RENDERMODE_CONTINUOUSLY
        );


        root.addView(
                surfaceView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );


        /*
         * STATUS OBEN
         */

        statusText = new TextView(this);

        statusText.setText(
                "AR wird gestartet...\n" +
                "Bewege das Handy langsam."
        );

        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(18);
        statusText.setGravity(Gravity.CENTER);

        statusText.setBackgroundColor(
                Color.argb(
                        190,
                        0,
                        0,
                        0
                )
        );

        statusText.setPadding(
                20,
                25,
                20,
                25
        );


        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        statusParams.gravity = Gravity.TOP;

        root.addView(
                statusText,
                statusParams
        );


        /*
         * NEU MESSEN
         */

        resetButton = new Button(this);

        resetButton.setText(
                "NEU MESSEN"
        );


        FrameLayout.LayoutParams resetParams =
                new FrameLayout.LayoutParams(
                        330,
                        120
                );

        resetParams.gravity =
                Gravity.TOP |
                Gravity.END;

        resetParams.topMargin = 120;
        resetParams.rightMargin = 18;

        root.addView(
                resetButton,
                resetParams
        );


        /*
         * ERGEBNIS
         */

        resultText = new TextView(this);

        resultText.setTextColor(Color.WHITE);
        resultText.setTextSize(21);
        resultText.setGravity(Gravity.CENTER);

        resultText.setBackgroundColor(
                Color.argb(
                        210,
                        0,
                        0,
                        0
                )
        );

        resultText.setPadding(
                20,
                22,
                20,
                22
        );


        FrameLayout.LayoutParams resultParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        resultParams.gravity = Gravity.BOTTOM;

        /*
         * Ergebnis oberhalb des Buttons
         */
        resultParams.bottomMargin = 280;

        root.addView(
                resultText,
                resultParams
        );


        /*
         * MESSUNG ÜBERNEHMEN
         */

        useButton = new Button(this);

        useButton.setText(
                "MESSUNG ÜBERNEHMEN"
        );

        useButton.setTextSize(17);

        useButton.setEnabled(false);


        FrameLayout.LayoutParams useParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        145
                );

        useParams.gravity = Gravity.BOTTOM;

        useParams.leftMargin = 20;
        useParams.rightMargin = 20;

        /*
         * Wichtig:
         * Button deutlich über Android-Navigationsleiste
         */
        useParams.bottomMargin = 110;

        root.addView(
                useButton,
                useParams
        );


        setContentView(root);


        /*
         * KAMERA ANTIPPEN
         */

        surfaceView.setOnTouchListener(
                (view, event) -> {

                    if (
                            event.getAction()
                                    ==
                            MotionEvent.ACTION_UP
                    ) {

                        addMeasurementPoint(
                                event.getX(),
                                event.getY()
                        );
                    }

                    return true;
                }
        );


        resetButton.setOnClickListener(
                view -> resetMeasurement()
        );


        useButton.setOnClickListener(
                view -> {

                    if (
                            widthMeters <= 0 ||
                            heightMeters <= 0 ||
                            areaMeters <= 0
                    ) {

                        Toast.makeText(
                                this,
                                "Bitte zuerst das Fenster messen.",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }


                    Intent result =
                            new Intent();

                    result.putExtra(
                            "width",
                            widthMeters
                    );

                    result.putExtra(
                            "height",
                            heightMeters
                    );

                    result.putExtra(
                            "area",
                            areaMeters
                    );


                    setResult(
                            RESULT_OK,
                            result
                    );

                    finish();
                }
        );
    }


    /*
     * AR SESSION
     */

    private void createArSession()
            throws UnavailableException {

        session =
                new Session(this);


        Config config =
                session.getConfig();


        config.setPlaneFindingMode(
                Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        );


        if (
                session.isDepthModeSupported(
                        Config.DepthMode.AUTOMATIC
                )
        ) {

            config.setDepthMode(
                    Config.DepthMode.AUTOMATIC
            );

            statusText.setText(
                    "AR + Depth aktiv ✅\n" +
                    "Bewege das Handy langsam.\n" +
                    "Dann 4 Fensterecken antippen."
            );

        } else {

            statusText.setText(
                    "AR aktiv ✅\n" +
                    "Depth nicht verfügbar.\n" +
                    "Tippe möglichst auf den Fensterrahmen."
            );
        }


        session.configure(
                config
        );


        cameraTextureSet = false;
    }


    /*
     * RESUME
     */

    @Override
    protected void onResume() {
        super.onResume();


        if (
                checkSelfPermission(
                        Manifest.permission.CAMERA
                )
                        !=
                PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.CAMERA
                    },
                    CAMERA_PERMISSION
            );

            return;
        }


        try {

            /*
             * ARCore installieren / aktualisieren
             */

            if (session == null) {

                ArCoreApk.InstallStatus installStatus =
                        ArCoreApk
                                .getInstance()
                                .requestInstall(
                                        this,
                                        userRequestedInstall
                                );


                if (
                        installStatus
                                ==
                        ArCoreApk.InstallStatus.INSTALL_REQUESTED
                ) {

                    userRequestedInstall = false;

                    statusText.setText(
                            "Google Play Services für AR wird installiert..."
                    );

                    return;
                }


                createArSession();
            }


            session.resume();

            surfaceView.onResume();


        } catch (
                UnavailableUserDeclinedInstallationException e
        ) {

            Toast.makeText(
                    this,
                    "Google Play Services für AR wurden nicht installiert.",
                    Toast.LENGTH_LONG
            ).show();

            finish();


        } catch (
                UnavailableDeviceNotCompatibleException e
        ) {

            Toast.makeText(
                    this,
                    "Dieses Gerät unterstützt ARCore nicht.",
                    Toast.LENGTH_LONG
            ).show();

            finish();


        } catch (
                CameraNotAvailableException e
        ) {

            Toast.makeText(
                    this,
                    "Kamera nicht verfügbar.",
                    Toast.LENGTH_LONG
            ).show();


        } catch (
                UnavailableException e
        ) {

            Toast.makeText(
                    this,
                    "ARCore konnte nicht gestartet werden.",
                    Toast.LENGTH_LONG
            ).show();

            finish();


        } catch (
                Exception e
        ) {

            Toast.makeText(
                    this,
                    "AR-Fehler: " +
                    e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }


    @Override
    protected void onPause() {
        super.onPause();


        if (surfaceView != null) {

            surfaceView.onPause();
        }


        if (session != null) {

            session.pause();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();


        for (
                Anchor anchor :
                anchors
        ) {

            anchor.detach();
        }


        anchors.clear();


        if (session != null) {

            session.close();

            session = null;
        }
    }


    /*
     * OPENGL
     */

    @Override
    public void onSurfaceCreated(
            GL10 gl,
            EGLConfig config
    ) {

        GLES20.glClearColor(
                0,
                0,
                0,
                1
        );


        cameraRenderer =
                new CameraRenderer();


        cameraRenderer.createOnGlThread();
    }


    @Override
    public void onSurfaceChanged(
            GL10 gl,
            int width,
            int height
    ) {

        GLES20.glViewport(
                0,
                0,
                width,
                height
        );


        if (session != null) {

            session.setDisplayGeometry(
                    getWindowManager()
                            .getDefaultDisplay()
                            .getRotation(),
                    width,
                    height
            );
        }
    }


    @Override
    public void onDrawFrame(
            GL10 gl
    ) {

        GLES20.glClear(
                GLES20.GL_COLOR_BUFFER_BIT |
                GLES20.GL_DEPTH_BUFFER_BIT
        );


        if (
                session == null ||
                cameraRenderer == null
        ) {

            return;
        }


        try {

            if (!cameraTextureSet) {

                session.setCameraTextureName(
                        cameraRenderer.getTextureId()
                );

                cameraTextureSet = true;
            }


            Frame frame =
                    session.update();


            latestFrame =
                    frame;


            cameraRenderer.draw(
                    frame
            );


        } catch (
                CameraNotAvailableException e
        ) {

            runOnUiThread(
                    () ->
                            Toast.makeText(
                                    this,
                                    "Kamera wurde getrennt.",
                                    Toast.LENGTH_SHORT
                            ).show()
            );


        } catch (
                Exception ignored
        ) {

        }
    }


    /*
     * MESSPUNKT SETZEN
     */

    private void addMeasurementPoint(
            float x,
            float y
    ) {

        if (latestFrame == null) {

            Toast.makeText(
                    this,
                    "AR ist noch nicht bereit. Bewege das Handy langsam.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        if (anchors.size() >= 4) {

            Toast.makeText(
                    this,
                    "Messung bereits fertig. Drücke NEU MESSEN.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        /*
         * Erst direkt auf dem Fingertipp suchen
         */

        HitResult selected =
                findBestHit(
                        x,
                        y
                );


        /*
         * Wenn nichts gefunden:
         * automatisch rund um den Fingertipp suchen
         */

        if (selected == null) {

            float[] radii = {
                    20f,
                    40f,
                    60f,
                    80f,
                    110f,
                    140f
            };


            outer:

            for (
                    float radius :
                    radii
            ) {

                for (
                        int angle = 0;
                        angle < 360;
                        angle += 20
                ) {

                    double radians =
                            Math.toRadians(
                                    angle
                            );


                    float searchX =
                            x +
                            (float) (
                                    Math.cos(radians)
                                    *
                                    radius
                            );


                    float searchY =
                            y +
                            (float) (
                                    Math.sin(radians)
                                    *
                                    radius
                            );


                    selected =
                            findBestHit(
                                    searchX,
                                    searchY
                            );


                    if (selected != null) {

                        break outer;
                    }
                }
            }
        }


        /*
         * Noch immer nichts
         */

        if (selected == null) {

            Toast.makeText(
                    this,
                    "Kein Messpunkt erkannt.\n" +
                    "Halte etwa 1–3 m Abstand,\n" +
                    "bewege das Handy langsam\n" +
                    "und tippe auf den Fensterrahmen.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }


        Anchor anchor =
                selected.createAnchor();


        anchors.add(
                anchor
        );


        addMarker(
                x,
                y,
                anchors.size()
        );


        if (
                anchors.size()
                        <
                4
        ) {

            statusText.setText(
                    "Messpunkt "
                            +
                    anchors.size()
                            +
                    " von 4 gesetzt"
            );

        } else {

            calculateMeasurement();
        }
    }


    /*
     * BESTEN AR HIT SUCHEN
     */

    private HitResult findBestHit(
            float x,
            float y
    ) {

        if (latestFrame == null) {

            return null;
        }


        List<HitResult> results;


        try {

            results =
                    latestFrame.hitTest(
                            x,
                            y
                    );

        } catch (
                Exception e
        ) {

            return null;
        }


        /*
         * 1. DEPTH
         */

        for (
                HitResult result :
                results
        ) {

            if (
                    result.getTrackable()
                            instanceof DepthPoint
            ) {

                return result;
            }
        }


        /*
         * 2. PLANE
         */

        for (
                HitResult result :
                results
        ) {

            if (
                    result.getTrackable()
                            instanceof Plane
            ) {

                Plane plane =
                        (Plane)
                                result.getTrackable();


                if (
                        plane.isPoseInPolygon(
                                result.getHitPose()
                        )
                ) {

                    return result;
                }
            }
        }


        /*
         * 3. FEATURE POINT
         */

        for (
                HitResult result :
                results
        ) {

            if (
                    result.getTrackable()
                            instanceof Point
            ) {

                return result;
            }
        }


        return null;
    }


    /*
     * GRÜNE NUMMER AUF DISPLAY
     */

    private void addMarker(
            float x,
            float y,
            int number
    ) {

        TextView marker =
                new TextView(this);


        marker.setText(
                String.valueOf(number)
        );


        marker.setTextColor(
                Color.WHITE
        );


        marker.setTextSize(
                18
        );


        marker.setGravity(
                Gravity.CENTER
        );


        marker.setBackgroundColor(
                Color.rgb(
                        7,
                        132,
                        95
                )
        );


        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        70,
                        70
                );


        params.leftMargin =
                (int) x - 35;


        params.topMargin =
                (int) y - 35;


        root.addView(
                marker,
                params
        );


        markers.add(
                marker
        );
    }


    /*
     * MESSUNG BERECHNEN
     */

    private void calculateMeasurement() {

        if (
                anchors.size()
                        !=
                4
        ) {

            return;
        }


        float[] p0 =
                anchors
                        .get(0)
                        .getPose()
                        .getTranslation();


        float[] p1 =
                anchors
                        .get(1)
                        .getPose()
                        .getTranslation();


        float[] p2 =
                anchors
                        .get(2)
                        .getPose()
                        .getTranslation();


        float[] p3 =
                anchors
                        .get(3)
                        .getPose()
                        .getTranslation();


        /*
         * Reihenfolge:
         *
         * 1 = oben links
         * 2 = oben rechts
         * 3 = unten rechts
         * 4 = unten links
         */

        double top =
                distance(
                        p0,
                        p1
                );


        double right =
                distance(
                        p1,
                        p2
                );


        double bottom =
                distance(
                        p3,
                        p2
                );


        double left =
                distance(
                        p0,
                        p3
                );


        widthMeters =
                (
                        top +
                        bottom
                )
                /
                2.0;


        heightMeters =
                (
                        left +
                        right
                )
                /
                2.0;


        areaMeters =
                widthMeters
                        *
                heightMeters;


        /*
         * Extremwerte abfangen
         */

        if (
                widthMeters <= 0 ||
                heightMeters <= 0 ||
                widthMeters > 8 ||
                heightMeters > 8 ||
                areaMeters > 40
        ) {

            resultText.setText(
                    String.format(
                            Locale.GERMANY,

                            "⚠ Messung unplausibel\n\n" +
                            "Breite: %.2f m\n" +
                            "Höhe: %.2f m\n" +
                            "Fläche: %.2f m²\n\n" +
                            "Bitte neu messen.",

                            widthMeters,
                            heightMeters,
                            areaMeters
                    )
            );


            statusText.setText(
                    "Messung unplausibel ⚠\n" +
                    "Bitte NEU MESSEN."
            );


            useButton.setEnabled(
                    false
            );


            return;
        }


        /*
         * GÜLTIGE MESSUNG
         */

        resultText.setText(
                String.format(
                        Locale.GERMANY,

                        "Breite: %.2f m\n" +
                        "Höhe: %.2f m\n" +
                        "Fläche: %.2f m²",

                        widthMeters,
                        heightMeters,
                        areaMeters
                )
        );


        statusText.setText(
                "Messung fertig ✅\n" +
                "Jetzt Messung übernehmen."
        );


        useButton.setEnabled(
                true
        );


        /*
         * Button sicher ganz vorne
         */

        useButton.bringToFront();
    }


    /*
     * ABSTAND ZWISCHEN 2 AR PUNKTEN
     */

    private double distance(
            float[] a,
            float[] b
    ) {

        double dx =
                a[0] - b[0];


        double dy =
                a[1] - b[1];


        double dz =
                a[2] - b[2];


        return Math.sqrt(
                dx * dx
                        +
                dy * dy
                        +
                dz * dz
        );
    }


    /*
     * NEU MESSEN
     */

    private void resetMeasurement() {

        for (
                Anchor anchor :
                anchors
        ) {

            anchor.detach();
        }


        anchors.clear();


        for (
                View marker :
                markers
        ) {

            root.removeView(
                    marker
            );
        }


        markers.clear();


        widthMeters = 0;
        heightMeters = 0;
        areaMeters = 0;


        resultText.setText(
                ""
        );


        statusText.setText(
                "Neu messen ✅\n" +
                "Handy bewegen und 4 Fensterecken antippen."
        );


        useButton.setEnabled(
                false
        );
    }


    /*
     * KAMERA BERECHTIGUNG
     */

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );


        if (
                requestCode
                        ==
                CAMERA_PERMISSION
        ) {

            if (
                    grantResults.length > 0
                            &&
                    grantResults[0]
                            ==
                    PackageManager.PERMISSION_GRANTED
            ) {

                onResume();

            } else {

                Toast.makeText(
                        this,
                        "Ohne Kamera kann die Fenster-Messung nicht funktionieren.",
                        Toast.LENGTH_LONG
                ).show();


                finish();
            }
        }
    }
}
        
                
