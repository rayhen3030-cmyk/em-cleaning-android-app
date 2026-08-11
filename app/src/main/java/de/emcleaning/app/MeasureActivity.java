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
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
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

    /*
     * Maximale Entfernung eines Messpunktes
     * von der Kamera.
     *
     * Verhindert extreme Treffer irgendwo
     * weit hinter dem Fenster.
     */
    private static final float MAX_HIT_DISTANCE_METERS = 6.0f;


    private FrameLayout root;
    private GLSurfaceView surfaceView;

    private LinearLayout topBar;
    private LinearLayout bottomBar;

    private TextView statusText;
    private TextView resultText;

    private Button resetButton;
    private Button useButton;


    private Session session;

    /*
     * latestFrame wird ausschließlich
     * auf dem GL-Thread benutzt.
     */
    private Frame latestFrame;

    private CameraRenderer cameraRenderer;

    private boolean cameraTextureSet = false;
    private boolean userRequestedInstall = true;


    /*
     * Anchors werden auf dem GL-Thread verändert.
     */
    private final List<Anchor> anchors =
            new ArrayList<>();


    /*
     * Marker gehören zur Android-Oberfläche
     * und werden auf dem UI-Thread verändert.
     */
    private final List<View> markers =
            new ArrayList<>();


    /*
     * Ergebnisse.
     *
     * volatile, weil GL-Thread schreibt
     * und UI-Thread liest.
     */
    private volatile double widthMeters = 0;
    private volatile double heightMeters = 0;
    private volatile double areaMeters = 0;


    private volatile boolean measurementFinished = false;


    private int topSystemInset = 0;
    private int bottomSystemInset = 0;


    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );

        buildUi();
    }


    /*
     * =====================================================
     * UI
     * =====================================================
     */

    private void buildUi() {

        root =
                new FrameLayout(
                        this
                );


        /*
         * Kamera / OpenGL
         */

        surfaceView =
                new GLSurfaceView(
                        this
                );

        surfaceView.setEGLContextClientVersion(
                2
        );

        surfaceView.setPreserveEGLContextOnPause(
                true
        );

        surfaceView.setRenderer(
                this
        );

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
         * =================================================
         * KLEINE OBERE LEISTE
         * =================================================
         */

        topBar =
                new LinearLayout(
                        this
                );

        topBar.setOrientation(
                LinearLayout.HORIZONTAL
        );

        topBar.setGravity(
                Gravity.CENTER_VERTICAL
        );

        topBar.setPadding(
                10,
                5,
                8,
                5
        );

        topBar.setBackgroundColor(
                Color.argb(
                        145,
                        0,
                        0,
                        0
                )
        );


        statusText =
                new TextView(
                        this
                );

        statusText.setText(
                "AR wird gestartet ..."
        );

        statusText.setTextColor(
                Color.WHITE
        );

        statusText.setTextSize(
                14
        );

        statusText.setGravity(
                Gravity.CENTER_VERTICAL
        );

        statusText.setSingleLine(
                true
        );


        LinearLayout.LayoutParams statusLp =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );


        topBar.addView(
                statusText,
                statusLp
        );


        resetButton =
                new Button(
                        this
                );

        resetButton.setText(
                "NEU"
        );

        resetButton.setTextSize(
                11
        );

        resetButton.setMinHeight(
                0
        );

        resetButton.setMinWidth(
                0
        );

        resetButton.setPadding(
                18,
                4,
                18,
                4
        );


        LinearLayout.LayoutParams resetLp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        58
                );


        topBar.addView(
                resetButton,
                resetLp
        );


        FrameLayout.LayoutParams topParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        topParams.gravity =
                Gravity.TOP;


        root.addView(
                topBar,
                topParams
        );


        /*
         * =================================================
         * KLEINE UNTERE LEISTE
         * =================================================
         */

        bottomBar =
                new LinearLayout(
                        this
                );

        bottomBar.setOrientation(
                LinearLayout.VERTICAL
        );

        bottomBar.setPadding(
                10,
                5,
                10,
                8
        );

        bottomBar.setBackgroundColor(
                Color.argb(
                        135,
                        0,
                        0,
                        0
                )
        );


        /*
         * Ergebnis erst anzeigen,
         * wenn 4 Punkte vorhanden sind.
         */

        resultText =
                new TextView(
                        this
                );

        resultText.setTextColor(
                Color.WHITE
        );

        resultText.setTextSize(
                15
        );

        resultText.setGravity(
                Gravity.CENTER
        );

        resultText.setPadding(
                8,
                5,
                8,
                5
        );

        resultText.setVisibility(
                View.GONE
        );


        bottomBar.addView(
                resultText,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );


        useButton =
                new Button(
                        this
                );

        useButton.setText(
                "MESSUNG ÜBERNEHMEN"
        );

        useButton.setTextSize(
                14
        );

        useButton.setEnabled(
                false
        );

        useButton.setMinHeight(
                0
        );


        LinearLayout.LayoutParams useLp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        72
                );

        useLp.topMargin =
                4;


        bottomBar.addView(
                useButton,
                useLp
        );


        FrameLayout.LayoutParams bottomParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        bottomParams.gravity =
                Gravity.BOTTOM;

        bottomParams.leftMargin =
                8;

        bottomParams.rightMargin =
                8;


        root.addView(
                bottomBar,
                bottomParams
        );


        /*
         * =================================================
         * ANDROID STATUS-/NAVIGATIONSBAR BERÜCKSICHTIGEN
         * =================================================
         *
         * Besonders wichtig bei Android 15 /
         * targetSdk 35.
         *
         * Dadurch liegt der untere Button NICHT mehr
         * hinter Home/Zurück/Gestenleiste.
         */

        root.setOnApplyWindowInsetsListener(
                (view, insets) -> {

                    topSystemInset =
                            insets.getSystemWindowInsetTop();

                    bottomSystemInset =
                            insets.getSystemWindowInsetBottom();


                    updateOverlayInsets();


                    return insets;
                }
        );


        setContentView(
                root
        );


        root.requestApplyInsets();


        /*
         * =================================================
         * TOUCH
         * =================================================
         */

        surfaceView.setOnTouchListener(
                (view, event) -> {

                    if (
                            event.getAction()
                                    ==
                            MotionEvent.ACTION_UP
                    ) {

                        final float x =
                                event.getX();

                        final float y =
                                event.getY();


                        /*
                         * Sehr wichtig:
                         *
                         * HitTest läuft auf demselben
                         * GL-Thread wie session.update().
                         *
                         * Das ist stabiler als Frame
                         * parallel vom UI-Thread zu benutzen.
                         */

                        surfaceView.queueEvent(
                                () ->
                                        addMeasurementPointOnGlThread(
                                                x,
                                                y
                                        )
                        );
                    }


                    return true;
                }
        );


        /*
         * Neu messen
         */

        resetButton.setOnClickListener(
                view ->
                        resetMeasurement()
        );


        /*
         * Messung übernehmen
         */

        useButton.setOnClickListener(
                view -> {

                    if (
                            !measurementFinished
                                    ||
                            widthMeters <= 0
                                    ||
                            heightMeters <= 0
                                    ||
                            areaMeters <= 0
                    ) {

                        Toast.makeText(
                                this,
                                "Keine gültige Messung vorhanden.",
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
     * Insets anwenden.
     */

    private void updateOverlayInsets() {

        if (
                topBar
                        !=
                null
        ) {

            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams)
                            topBar.getLayoutParams();


            params.topMargin =
                    topSystemInset;


            topBar.setLayoutParams(
                    params
            );
        }


        if (
                bottomBar
                        !=
                null
        ) {

            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams)
                            bottomBar.getLayoutParams();


            /*
             * Etwas Luft zusätzlich.
             */

            params.bottomMargin =
                    bottomSystemInset + 8;


            bottomBar.setLayoutParams(
                    params
            );
        }
    }


    /*
     * =====================================================
     * AR SESSION
     * =====================================================
     */

    private void createArSession()
            throws UnavailableException {

        session =
                new Session(
                        this
                );


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
                    "AR + Depth ✅  •  4 Ecken"
            );

        } else {

            statusText.setText(
                    "AR aktiv ✅  •  4 Ecken"
            );
        }


        session.configure(
                config
        );


        cameraTextureSet =
                false;
    }


    private void startArSessionIfPossible() {

        try {

            if (
                    session
                            ==
                    null
            ) {

                ArCoreApk.InstallStatus status =
                        ArCoreApk
                                .getInstance()
                                .requestInstall(
                                        this,
                                        userRequestedInstall
                                );


                if (
                        status
                                ==
                        ArCoreApk.InstallStatus.INSTALL_REQUESTED
                ) {

                    userRequestedInstall =
                            false;


                    statusText.setText(
                            "AR wird vorbereitet ..."
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
                    "AR wurde nicht installiert.",
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
                    "Kamera momentan nicht verfügbar.",
                    Toast.LENGTH_LONG
            ).show();


            /*
             * Nicht sofort Activity schließen.
             * Nutzer kann zurückgehen oder App erneut versuchen.
             */

            statusText.setText(
                    "Kamera nicht verfügbar ⚠"
            );


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


            statusText.setText(
                    "AR-Fehler ⚠"
            );
        }
    }


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


        startArSessionIfPossible();
    }


    @Override
    protected void onPause() {
        super.onPause();


        if (
                surfaceView
                        !=
                null
        ) {

            surfaceView.onPause();
        }


        if (
                session
                        !=
                null
        ) {

            try {

                session.pause();

            } catch (
                    Exception ignored
            ) {

            }
        }
    }


    @Override
    protected void onDestroy() {

        /*
         * Anchors lösen.
         */

        for (
                Anchor anchor :
                anchors
        ) {

            try {

                anchor.detach();

            } catch (
                    Exception ignored
            ) {

            }
        }


        anchors.clear();


        if (
                session
                        !=
                null
        ) {

            try {

                session.close();

            } catch (
                    Exception ignored
            ) {

            }


            session =
                    null;
        }


        super.onDestroy();
    }


    /*
     * =====================================================
     * OPENGL
     * =====================================================
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


        if (
                session
                        !=
                null
        ) {

            try {

                session.setDisplayGeometry(
                        getWindowManager()
                                .getDefaultDisplay()
                                .getRotation(),
                        width,
                        height
                );

            } catch (
                    Exception ignored
            ) {

            }
        }
    }


    @Override
    public void onDrawFrame(
            GL10 gl
    ) {

        GLES20.glClear(
                GLES20.GL_COLOR_BUFFER_BIT
                        |
                GLES20.GL_DEPTH_BUFFER_BIT
        );


        if (
                session
                        ==
                null
                        ||
                cameraRenderer
                        ==
                null
        ) {

            return;
        }


        try {

            if (
                    !cameraTextureSet
            ) {

                session.setCameraTextureName(
                        cameraRenderer.getTextureId()
                );


                cameraTextureSet =
                        true;
            }


            Frame frame =
                    session.update();


            /*
             * Wichtig:
             * nur auf GL-Thread benutzen.
             */

            latestFrame =
                    frame;


            cameraRenderer.draw(
                    frame
            );


        } catch (
                CameraNotAvailableException e
        ) {

            latestFrame =
                    null;


            runOnUiThread(
                    () -> {

                        statusText.setText(
                                "Kamera getrennt ⚠"
                        );


                        Toast.makeText(
                                this,
                                "Kamera momentan nicht verfügbar.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            );


        } catch (
                Exception e
        ) {

            /*
             * Einzelnen kaputten Frame ignorieren.
             *
             * Activity NICHT schließen.
             */

            latestFrame =
                    null;
        }
    }


    /*
     * =====================================================
     * MESSPUNKT
     * =====================================================
     *
     * Diese Methode läuft auf dem GL-Thread.
     */

    private void addMeasurementPointOnGlThread(
            float x,
            float y
    ) {

        if (
                latestFrame
                        ==
                null
        ) {

            showToast(
                    "AR noch nicht bereit. Handy langsam bewegen."
            );


            return;
        }


        if (
                measurementFinished
                        ||
                anchors.size()
                        >=
                4
        ) {

            showToast(
                    "Messung ist bereits fertig."
            );


            return;
        }


        HitResult selected =
                findBestHit(
                        latestFrame,
                        x,
                        y
                );


        /*
         * Nur sehr kleine Suche um den Finger.
         *
         * Die alte Version suchte bis 140 Pixel
         * in alle Richtungen.
         * Dadurch konnte ein völlig anderer
         * Punkt getroffen werden.
         */

        if (
                selected
                        ==
                null
        ) {

            final float[] offsets = {
                    12f,
                    24f
            };


            final float[][] directions = {

                    {1, 0},
                    {-1, 0},

                    {0, 1},
                    {0, -1},

                    {1, 1},
                    {-1, 1},

                    {1, -1},
                    {-1, -1}
            };


            outer:

            for (
                    float radius :
                    offsets
            ) {

                for (
                        float[] dir :
                        directions
                ) {

                    float searchX =
                            x +
                            dir[0] *
                            radius;


                    float searchY =
                            y +
                            dir[1] *
                            radius;


                    selected =
                            findBestHit(
                                    latestFrame,
                                    searchX,
                                    searchY
                            );


                    if (
                            selected
                                    !=
                            null
                    ) {

                        break outer;
                    }
                }
            }
        }


        if (
                selected
                        ==
                null
        ) {

            showToast(
                    "Kein genauer Messpunkt erkannt. Handy langsam bewegen."
            );


            return;
        }


        try {

            Anchor anchor =
                    selected.createAnchor();


            anchors.add(
                    anchor
            );


            final int number =
                    anchors.size();


            /*
             * Marker und Text auf UI-Thread.
             */

            runOnUiThread(
                    () -> {

                        addMarker(
                                x,
                                y,
                                number
                        );


                        statusText.setText(
                                "Punkt " +
                                number +
                                "/4"
                        );
                    }
            );


            if (
                    anchors.size()
                            ==
                    4
            ) {

                calculateMeasurementOnGlThread();
            }


        } catch (
                Exception e
        ) {

            showToast(
                    "Messpunkt konnte nicht gespeichert werden."
            );
        }
    }


    /*
     * Passenden Hit suchen.
     */

    private HitResult findBestHit(
            Frame frame,
            float x,
            float y
    ) {

        List<HitResult> results;


        try {

            results =
                    frame.hitTest(
                            x,
                            y
                    );

        } catch (
                Exception e
        ) {

            return null;
        }


        /*
         * Kamera-Position.
         */

        Pose cameraPose;


        try {

            cameraPose =
                    frame
                            .getCamera()
                            .getPose();

        } catch (
                Exception e
        ) {

            return null;
        }


        /*
         * 1. DepthPoint bevorzugen
         */

        for (
                HitResult result :
                results
        ) {

            if (
                    result.getTrackable()
                            instanceof
                    DepthPoint
                    &&
                    isHitReasonable(
                            cameraPose,
                            result
                    )
            ) {

                return result;
            }
        }


        /*
         * 2. erkannte Plane
         */

        for (
                HitResult result :
                results
        ) {

            if (
                    result.getTrackable()
                            instanceof
                    Plane
            ) {

                Plane plane =
                        (Plane)
                                result.getTrackable();


                if (
                        plane.getTrackingState()
                                ==
                        TrackingState.TRACKING
                        &&
                        plane.isPoseInPolygon(
                                result.getHitPose()
                        )
                        &&
                        isHitReasonable(
                                cameraPose,
                                result
                        )
                ) {

                    return result;
                }
            }
        }


        /*
         * 3. Feature Point
         */

        for (
                HitResult result :
                results
        ) {

            if (
                    result.getTrackable()
                            instanceof
                    Point
                    &&
                    result.getTrackable()
                            .getTrackingState()
                            ==
                    TrackingState.TRACKING
                    &&
                    isHitReasonable(
                            cameraPose,
                            result
                    )
            ) {

                return result;
            }
        }


        return null;
    }


    /*
     * Extrem weit entfernte Treffer ablehnen.
     */

    private boolean isHitReasonable(
            Pose cameraPose,
            HitResult result
    ) {

        try {

            float[] camera =
                    cameraPose
                            .getTranslation();


            float[] hit =
                    result
                            .getHitPose()
                            .getTranslation();


            double distance =
                    distance(
                            camera,
                            hit
                    );


            return (
                    distance > 0.10
                            &&
                    distance <=
                            MAX_HIT_DISTANCE_METERS
            );


        } catch (
                Exception e
        ) {

            return false;
        }
    }


    /*
     * =====================================================
     * MARKER
     * =====================================================
     */

    private void addMarker(
            float x,
            float y,
            int number
    ) {

        TextView marker =
                new TextView(
                        this
                );


        marker.setText(
                String.valueOf(
                        number
                )
        );


        marker.setTextColor(
                Color.WHITE
        );


        marker.setTextSize(
                15
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


        /*
         * Kleinere Marker.
         */

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        52,
                        52
                );


        params.leftMargin =
                Math.max(
                        0,
                        (int) x - 26
                );


        params.topMargin =
                Math.max(
                        topSystemInset,
                        (int) y - 26
                );


        root.addView(
                marker,
                params
        );


        /*
         * Oberflächen-Elemente immer darüber.
         */

        topBar.bringToFront();

        bottomBar.bringToFront();


        markers.add(
                marker
        );
    }


    /*
     * =====================================================
     * BERECHNUNG
     * =====================================================
     */

    private void calculateMeasurementOnGlThread() {

        if (
                anchors.size()
                        !=
                4
        ) {

            return;
        }


        try {

            float[] p0 =
                    anchors.get(0)
                            .getPose()
                            .getTranslation();


            float[] p1 =
                    anchors.get(1)
                            .getPose()
                            .getTranslation();


            float[] p2 =
                    anchors.get(2)
                            .getPose()
                            .getTranslation();


            float[] p3 =
                    anchors.get(3)
                            .getPose()
                            .getTranslation();


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


            double calculatedWidth =
                    (
                            top +
                            bottom
                    )
                            /
                    2.0;


            double calculatedHeight =
                    (
                            left +
                            right
                    )
                            /
                    2.0;


            double calculatedArea =
                    calculatedWidth *
                    calculatedHeight;


            widthMeters =
                    calculatedWidth;


            heightMeters =
                    calculatedHeight;


            areaMeters =
                    calculatedArea;


            /*
             * Typisches Fenster sollte nicht
             * zig Meter breit sein.
             *
             * Trotzdem erlauben wir Übernehmen,
             * falls Sonderfall.
             */

            boolean implausible =
                    calculatedWidth > 6.0
                            ||
                    calculatedHeight > 6.0
                            ||
                    calculatedArea > 30.0
                            ||
                    calculatedWidth < 0.10
                            ||
                    calculatedHeight < 0.10;


            measurementFinished =
                    true;


            runOnUiThread(
                    () ->
                            showMeasurementResult(
                                    implausible
                            )
            );


        } catch (
                Exception e
        ) {

            measurementFinished =
                    false;


            showToast(
                    "Messung konnte nicht berechnet werden. Bitte neu messen."
            );
        }
    }


    private void showMeasurementResult(
            boolean implausible
    ) {

        resultText.setVisibility(
                View.VISIBLE
        );


        if (
                implausible
        ) {

            statusText.setText(
                    "⚠ Messung prüfen"
            );


            resultText.setText(
                    String.format(
                            Locale.GERMANY,

                            "⚠ %.2f × %.2f m = %.2f m²",

                            widthMeters,
                            heightMeters,
                            areaMeters
                    )
            );


            useButton.setText(
                    "TROTZDEM ÜBERNEHMEN"
            );


        } else {

            statusText.setText(
                    "Messung fertig ✅"
            );


            resultText.setText(
                    String.format(
                            Locale.GERMANY,

                            "%.2f × %.2f m  •  %.2f m²",

                            widthMeters,
                            heightMeters,
                            areaMeters
                    )
            );


            useButton.setText(
                    "MESSUNG ÜBERNEHMEN"
            );
        }


        useButton.setEnabled(
                true
        );


        /*
         * Ganz wichtig:
         * BottomBar bleibt über Kamera + Markern.
         */

        bottomBar.bringToFront();

        topBar.bringToFront();
    }


    /*
     * =====================================================
     * DISTANZ
     * =====================================================
     */

    private double distance(
            float[] a,
            float[] b
    ) {

        double dx =
                a[0] -
                b[0];


        double dy =
                a[1] -
                b[1];


        double dz =
                a[2] -
                b[2];


        return Math.sqrt(
                dx * dx +
                dy * dy +
                dz * dz
        );
    }


    /*
     * =====================================================
     * NEU MESSEN
     * =====================================================
     */

    private void resetMeasurement() {

        measurementFinished =
                false;


        widthMeters =
                0;


        heightMeters =
                0;


        areaMeters =
                0;


        /*
         * Anchors auf GL-Thread löschen.
         */

        if (
                surfaceView
                        !=
                null
        ) {

            surfaceView.queueEvent(
                    () -> {

                        for (
                                Anchor anchor :
                                anchors
                        ) {

                            try {

                                anchor.detach();

                            } catch (
                                    Exception ignored
                            ) {

                            }
                        }


                        anchors.clear();
                    }
            );
        }


        /*
         * Marker auf UI-Thread.
         */

        for (
                View marker :
                markers
        ) {

            root.removeView(
                    marker
            );
        }


        markers.clear();


        resultText.setText(
                ""
        );


        resultText.setVisibility(
                View.GONE
        );


        statusText.setText(
                "Neu messen ✅  •  4 Ecken"
        );


        useButton.setText(
                "MESSUNG ÜBERNEHMEN"
        );


        useButton.setEnabled(
                false
        );


        topBar.bringToFront();

        bottomBar.bringToFront();
    }


    /*
     * =====================================================
     * TOAST AUS GL-THREAD
     * =====================================================
     */

    private void showToast(
            String text
    ) {

        runOnUiThread(
                () ->
                        Toast.makeText(
                                this,
                                text,
                                Toast.LENGTH_SHORT
                        ).show()
        );
    }


    /*
     * =====================================================
     * KAMERA-BERECHTIGUNG
     * =====================================================
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

                /*
                 * Nicht onResume() manuell aufrufen.
                 */

                startArSessionIfPossible();


            } else {

                Toast.makeText(
                        this,
                        "Kamera-Berechtigung wird benötigt.",
                        Toast.LENGTH_LONG
                ).show();


                finish();
            }
        }
    }
}
                
