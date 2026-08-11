package de.emcleaning.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MeasureActivity extends Activity {

    private EditText widthInput;
    private EditText heightInput;
    private TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 60, 40, 40);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Fenster messen");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);

        TextView info = new TextView(this);
        info.setText(
                "\nFensterbreite und Fensterhöhe eingeben.\n" +
                "Die App berechnet automatisch die Fläche."
        );
        info.setTextSize(17);
        info.setGravity(Gravity.CENTER);

        widthInput = new EditText(this);
        widthInput.setHint("Breite in cm");
        widthInput.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        heightInput = new EditText(this);
        heightInput.setHint("Höhe in cm");
        heightInput.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        Button calculateButton = new Button(this);
        calculateButton.setText("Fläche berechnen");

        resultText = new TextView(this);
        resultText.setTextSize(22);
        resultText.setGravity(Gravity.CENTER);
        resultText.setPadding(0, 30, 0, 30);

        Button useButton = new Button(this);
        useButton.setText("Messung übernehmen");
        useButton.setEnabled(false);

        layout.addView(title);
        layout.addView(info);

        layout.addView(
                widthInput,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        layout.addView(
                heightInput,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        layout.addView(
                calculateButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        layout.addView(resultText);

        layout.addView(
                useButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        setContentView(layout);

        final double[] measurement = new double[3];

        calculateButton.setOnClickListener(v -> {

            String widthString =
                    widthInput.getText().toString()
                            .trim()
                            .replace(",", ".");

            String heightString =
                    heightInput.getText().toString()
                            .trim()
                            .replace(",", ".");

            if (widthString.isEmpty() || heightString.isEmpty()) {

                Toast.makeText(
                        this,
                        "Bitte Breite und Höhe eingeben.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            try {

                double widthCm =
                        Double.parseDouble(widthString);

                double heightCm =
                        Double.parseDouble(heightString);

                if (widthCm <= 0 || heightCm <= 0) {

                    Toast.makeText(
                            this,
                            "Bitte gültige Maße eingeben.",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                double widthM = widthCm / 100.0;
                double heightM = heightCm / 100.0;

                double area = widthM * heightM;

                measurement[0] = widthM;
                measurement[1] = heightM;
                measurement[2] = area;

                resultText.setText(
                        String.format(
                                Locale.GERMANY,
                                "Breite: %.2f m\nHöhe: %.2f m\n\nFläche: %.2f m²",
                                widthM,
                                heightM,
                                area
                        )
                );

                useButton.setEnabled(true);

            } catch (Exception e) {

                Toast.makeText(
                        this,
                        "Die Maße konnten nicht berechnet werden.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });


        useButton.setOnClickListener(v -> {

            Intent result = new Intent();

            result.putExtra(
                    "width",
                    measurement[0]
            );

            result.putExtra(
                    "height",
                    measurement[1]
            );

            result.putExtra(
                    "area",
                    measurement[2]
            );

            setResult(
                    RESULT_OK,
                    result
            );

            finish();
        });
    }
}
