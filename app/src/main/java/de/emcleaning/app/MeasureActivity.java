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


        statusText = new TextView(this);

        statusText.setText(
                "AR wird gestartet...\n" +
                "Danach das Handy langsam bewegen."
        );

        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(17);
        statusText.setGravity(Gravity.CENTER);

        statusText.setBackgroundColor(
                Color.argb(
                        180,
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


        resultText = new TextView(this);

        resultText.setTextColor(Color.WHITE);
        resultText.setTextSize(20);
        resultText.setGravity(Gravity.CENTER);

        resultText.setBackgroundColor(
                Color.argb(
                        180,
                        0,
                        0,
                        0
                )
        );

        resultText.setPadding(
                20,
                20,
                20,
                20
        );

        FrameLayout.LayoutParams resultParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        resultParams.gravity = Gravity.BOTTOM;
        resultParams.bottomMargin = 150;

        root.addView(
                resultText,
                resultParams
        );


        resetButton = new Button(this);

        resetButton.setText("Neu messen");

        FrameLayout.LayoutParams resetParams =
                new FrameLayout.LayoutParams(
                        340,
                        110
                );

        resetParams.gravity =
                Gravity.TOP |
                Gravity.RIGHT;

        resetParams.topMargin = 120;
        resetParams.rightMargin = 15;

        root.addView(
                resetButton,
                resetParams
        );


        useButton = new Button(this);

        useButton.setText(
                "Messung übernehmen"
        );

        useButton.setEnabled(false);

        FrameLayout.LayoutParams useParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        120
                );

        useParams.gravity = Gravity.BOTTOM;

        useParams.leftMargin = 20;
        useParams.rightMargin = 20;
        useParams.bottomMargin = 15;

        root.addView(
                useButton,
                useParams
        );


        setContentView(root);


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


    private void createArSession()
            throws UnavailableException {

        session = new Session(this);

        Config config =
                session.getConfig();


        if (
                session.isDepthModeSupported(
                        Config.DepthMode.AUTOMATIC
                )
        ) {

            config.setDepthMode(
                    Config.DepthMode.AUTOMATIC
            );

            statusText.setText(
                    "Depth aktiv ✅\n" +
                    "Handy langsam bewegen.\n" +
                    "Dann 4 Fensterecken antippen."
            );

        } else {

            statusText.setText(
                    "AR aktiv ✅\n" +
                    "Depth ist nicht verfügbar.\n" +
                    "Handy bewegen und 4 Ecken antippen."
            );
        }


        config.setPlaneFindingMode(
                Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        );

        session.configure(config);

        cameraTextureSet = false;
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


        try {

            if (session == null) {

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
                    "Die Kamera ist momentan nicht verfügbar.",
                    Toast.LENGTH_LONG
            ).show();


        } catch (UnavailableException e) {

            Toast.makeText(
                    this,
                    "ARCore konnte nicht gestartet werden.",
                    Toast.LENGTH_LONG
            ).show();

            finish();


        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "AR-Fehler: " + e.getMessage(),
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

        for (Anchor anchor : anchors) {
            anchor.detach();
        }

        anchors.clear();

        if (session != null) {

            session.close();
            session = null;
        }
    }


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
    public void onDrawFrame(GL10 gl) {

        GLES20.glClear(
                GLES20.GL_COLOR_BUFFER_BIT |
                        GLES20.GL_DEPTH_BUFFER_BIT
        );


        if (
                session == null
                        ||
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
                    () -> Toast.makeText(
                            this,
                            "Kamera wurde getrennt.",
                            Toast.LENGTH_SHORT
                    ).show()
            );


        } catch (Exception ignored) {

        }
    }


    private void addMeasurementPoint(
            float x,
            float y
    ) {

        if (latestFrame == null) {

            Toast.makeText(
                    this,
                    "AR ist noch nicht bereit.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        if (anchors.size() >= 4) {

            Toast.makeText(
                    this,
                    "Drücke 'Neu messen'.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        List<HitResult> results =
                latestFrame.hitTest(
                        x,
                        y
                );


        HitResult selected = null;


        for (
                HitResult result :
                results
        ) {

            if (
                    result.getTrackable()
                            instanceof DepthPoint
            ) {

                selected = result;

                break;
            }
        }


        if (selected == null) {

            for (
                    HitResult result :
                    results
            ) {

                if (
                        result.getTrackable()
                                instanceof Plane
                                ||
                        result.getTrackable()
                                instanceof Point
                ) {

                    selected = result;

                    break;
                }
            }
        }


        if (selected == null) {

            Toast.makeText(
                    this,
                    "Kein Messpunkt erkannt.\n" +
                    "Bewege das Handy etwas und tippe erneut.",
                    Toast.LENGTH_SHORT
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


        statusText.setText(
                "Punkt "
                        +
                anchors.size()
                        +
                " von 4 gesetzt"
        );


        if (anchors.size() == 4) {

            calculateMeasurement();
        }
    }


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

        marker.setTextSize(17);

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


    private void calculateMeasurement() {

        if (anchors.size() != 4) {
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
                (top + bottom)
                        /
                2.0;


        heightMeters =
                (left + right)
                        /
                2.0;


        areaMeters =
                widthMeters
                        *
                heightMeters;


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


        useButton.setEnabled(true);
    }


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


        resultText.setText("");


        statusText.setText(
                "Neu messen ✅\n" +
                "Handy bewegen und 4 Fensterecken antippen."
        );


        useButton.setEnabled(false);
    }


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
        
                
