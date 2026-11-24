package ro.bibliotopicsearch.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public final class ColorPickerDialog {
    public interface OnColorSelected {
        void onColorSelected(int color);
    }

    private static final int[] PALETTE = new int[] {
            Color.rgb(218, 73, 96),
            Color.rgb(48, 129, 157),
            Color.rgb(58, 146, 114),
            Color.rgb(222, 148, 54),
            Color.rgb(127, 90, 168),
            Color.rgb(76, 112, 157),
            Color.rgb(190, 91, 55),
            Color.rgb(82, 142, 63),
            Color.rgb(184, 72, 143),
            Color.rgb(61, 150, 148),
            Color.rgb(112, 98, 79),
            Color.rgb(40, 57, 76),
            Color.rgb(230, 90, 45),
            Color.rgb(9, 132, 227),
            Color.rgb(0, 168, 150),
            Color.rgb(253, 203, 110),
            Color.rgb(108, 92, 231),
            Color.rgb(214, 48, 49),
            Color.rgb(45, 52, 54),
            Color.rgb(99, 110, 114)
    };

    private ColorPickerDialog() {}

    public static void show(Activity activity, int initialColor, OnColorSelected listener) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 18), dp(activity, 12), dp(activity, 18), dp(activity, 8));

        TextView hint = new TextView(activity);
        hint.setText("Alege o culoare rapidă sau reglează RGB / HEX.");
        hint.setTextSize(14);
        root.addView(hint);

        for (int row = 0; row < 5; row++) {
            LinearLayout line = new LinearLayout(activity);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER);
            for (int col = 0; col < 4; col++) {
                int index = row * 4 + col;
                Button swatch = new Button(activity);
                swatch.setText("");
                swatch.setBackgroundTintList(ColorStateList.valueOf(PALETTE[index]));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(activity, 42), 1f);
                lp.setMargins(dp(activity, 3), dp(activity, 3), dp(activity, 3), dp(activity, 3));
                line.addView(swatch, lp);
            }
            root.addView(line);
        }

        Button preview = new Button(activity);
        preview.setText("PREVIZUALIZARE");
        preview.setTextColor(Color.WHITE);
        root.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 46)));

        SeekBar red = channelSeek(activity, Color.red(initialColor));
        SeekBar green = channelSeek(activity, Color.green(initialColor));
        SeekBar blue = channelSeek(activity, Color.blue(initialColor));

        root.addView(channelRow(activity, "R", red));
        root.addView(channelRow(activity, "G", green));
        root.addView(channelRow(activity, "B", blue));

        EditText hex = new EditText(activity);
        hex.setSingleLine(true);
        hex.setHint("#RRGGBB");
        hex.setInputType(InputType.TYPE_CLASS_TEXT);
        root.addView(hex);

        final int[] selected = { initialColor };

        Runnable updateFromRgb = () -> {
            selected[0] = Color.rgb(red.getProgress(), green.getProgress(), blue.getProgress());
            preview.setBackgroundTintList(ColorStateList.valueOf(selected[0]));
            preview.setText(String.format(Locale.ROOT, "#%06X", 0xFFFFFF & selected[0]));
            hex.setText(String.format(Locale.ROOT, "#%06X", 0xFFFFFF & selected[0]));
            hex.setSelection(hex.length());
        };

        SeekBar.OnSeekBarChangeListener channelListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) updateFromRgb.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        red.setOnSeekBarChangeListener(channelListener);
        green.setOnSeekBarChangeListener(channelListener);
        blue.setOnSeekBarChangeListener(channelListener);

        updateFromRgb.run();

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Paletă nod")
                .setView(root)
                .setNegativeButton("Renunță", null)
                .setPositiveButton("Aplică", null)
                .create();

        for (int row = 1; row <= 5; row++) {
            LinearLayout line = (LinearLayout) root.getChildAt(row);
            for (int col = 0; col < line.getChildCount(); col++) {
                int color = PALETTE[(row - 1) * 4 + col];
                line.getChildAt(col).setOnClickListener(v -> {
                    red.setProgress(Color.red(color));
                    green.setProgress(Color.green(color));
                    blue.setProgress(Color.blue(color));
                    updateFromRgb.run();
                });
            }
        }

        preview.setOnClickListener(v -> {
            try {
                String value = hex.getText().toString().trim();
                if (!value.startsWith("#")) value = "#" + value;
                int parsed = Color.parseColor(value);
                red.setProgress(Color.red(parsed));
                green.setProgress(Color.green(parsed));
                blue.setProgress(Color.blue(parsed));
                updateFromRgb.run();
            } catch (Exception ignored) {
                hex.setError("HEX invalid");
            }
        });

        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String value = hex.getText().toString().trim();
                if (!value.isEmpty()) {
                    if (!value.startsWith("#")) value = "#" + value;
                    selected[0] = Color.parseColor(value);
                }
            } catch (Exception parseError) {
                hex.setError("HEX invalid");
                return;
            }
            listener.onColorSelected(selected[0]);
            dialog.dismiss();
        }));

        dialog.show();
    }

    private static SeekBar channelSeek(Activity activity, int value) {
        SeekBar seek = new SeekBar(activity);
        seek.setMax(255);
        seek.setProgress(value);
        return seek;
    }

    private static View channelRow(Activity activity, String label, SeekBar seek) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView text = new TextView(activity);
        text.setText(label);
        text.setGravity(Gravity.CENTER);
        row.addView(text, new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 44)));
        row.addView(seek, new LinearLayout.LayoutParams(0, dp(activity, 44), 1f));
        return row;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
