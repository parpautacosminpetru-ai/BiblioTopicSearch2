package ro.bibliotopicsearch.app;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SettingsActivity extends AppCompatActivity {
    private static final int REQ_IMPORT_DICTIONARY = 401;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private TextView charsValue;
    private TextView precisionValue;
    private TextView dictionaryStatus;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdown();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(30));
        root.setBackgroundColor(Color.rgb(246, 248, 251));
        scroll.addView(root);

        root.addView(heading("Setări de căutare", 22));

        root.addView(label("Potrivire"));
        RadioGroup modes = new RadioGroup(this);
        modes.setOrientation(RadioGroup.VERTICAL);
        RadioButton exact = radio("Exact");
        RadioButton prefix = radio("Începe cu");
        RadioButton contains = radio("Conține");
        RadioButton flexible = radio("Flexibil");
        modes.addView(exact);
        modes.addView(prefix);
        modes.addView(contains);
        modes.addView(flexible);
        root.addView(modes);

        AppPrefs.MatchMode mode = AppPrefs.getMatchMode(this);
        if (mode == AppPrefs.MatchMode.EXACT) exact.setChecked(true);
        else if (mode == AppPrefs.MatchMode.CONTAINS) contains.setChecked(true);
        else if (mode == AppPrefs.MatchMode.FLEXIBLE) flexible.setChecked(true);
        else prefix.setChecked(true);

        modes.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == exact.getId()) AppPrefs.setMatchMode(this, AppPrefs.MatchMode.EXACT);
            else if (checkedId == contains.getId()) AppPrefs.setMatchMode(this, AppPrefs.MatchMode.CONTAINS);
            else if (checkedId == flexible.getId()) AppPrefs.setMatchMode(this, AppPrefs.MatchMode.FLEXIBLE);
            else AppPrefs.setMatchMode(this, AppPrefs.MatchMode.PREFIX);
        });

        Switch diacritics = toggle("Ignoră diacriticele", AppPrefs.ignoreDiacritics(this));
        diacritics.setOnCheckedChangeListener((b, checked) -> AppPrefs.setIgnoreDiacritics(this, checked));
        root.addView(diacritics);
        Switch repair = toggle("Corectare OCR prudentă", AppPrefs.ocrRepair(this));
        repair.setOnCheckedChangeListener((b, checked) -> AppPrefs.setOcrRepair(this, checked));
        root.addView(repair);

        charsValue = valueText();
        SeekBar chars = new SeekBar(this);
        chars.setMax(24);
        chars.setProgress(AppPrefs.compareChars(this));
        root.addView(label("Lungimea potrivirii"));
        root.addView(charsValue);
        root.addView(chars);
        updateCharsLabel(chars.getProgress());
        chars.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                AppPrefs.setCompareChars(SettingsActivity.this, progress);
                updateCharsLabel(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        root.addView(sectionGap());
        root.addView(heading("Stabilizare OCR", 18));
        precisionValue = valueText();
        SeekBar precision = new SeekBar(this);
        precision.setMax(100);
        precision.setProgress(AppPrefs.precision(this));
        root.addView(precisionValue);
        root.addView(precision);
        updatePrecisionLabel(precision.getProgress());
        precision.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                AppPrefs.setPrecision(SettingsActivity.this, progress);
                updatePrecisionLabel(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        Switch labels = toggle("Etichete plutitoare", AppPrefs.floatingLabels(this));
        labels.setOnCheckedChangeListener((b, checked) -> AppPrefs.setFloatingLabels(this, checked));
        root.addView(labels);

        Switch haptic = toggle("Vibrație", AppPrefs.haptic(this));
        haptic.setOnCheckedChangeListener((b, checked) -> AppPrefs.setHaptic(this, checked));
        root.addView(haptic);

        Switch sound = toggle("Semnal audio", AppPrefs.sound(this));
        sound.setOnCheckedChangeListener((b, checked) -> AppPrefs.setSound(this, checked));
        root.addView(sound);

        root.addView(sectionGap());
        root.addView(heading("Dicționar local", 18));
        dictionaryStatus = body("");
        root.addView(dictionaryStatus);
        updateDictionaryStatus();

        LinearLayout dictButtons = new LinearLayout(this);
        dictButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button importCsv = new Button(this);
        importCsv.setText("IMPORT CSV");
        Button clear = new Button(this);
        clear.setText("GOLEȘTE");
        dictButtons.addView(importCsv, new LinearLayout.LayoutParams(0, dp(48), 1f));
        dictButtons.addView(clear, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(dictButtons);

        importCsv.setOnClickListener(v -> chooseDictionary());
        importCsv.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setTitle("Format CSV")
                    .setMessage("term,definition,synonyms,antonyms,source")
                    .setPositiveButton("OK", null).show();
            return true;
        });
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Golești dicționarul?")
                .setMessage("Vor fi șterse numai intrările importate local.")
                .setNegativeButton("Nu", null)
                .setPositiveButton("Da", (d, which) -> {
                    new DictionaryStore(this).clearAll();
                    updateDictionaryStatus();
                })
                .show());

        setContentView(scroll);
        UiInsets.apply(scroll);
    }

    private void chooseDictionary() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("text/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQ_IMPORT_DICTIONARY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT_DICTIONARY ||
                resultCode != RESULT_OK ||
                data == null ||
                data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        dictionaryStatus.setText("Import în curs…");

        ioExecutor.execute(() -> {
            try {
                DictionaryStore store = new DictionaryStore(SettingsActivity.this);
                int imported = store.importCsv(SettingsActivity.this, uri);
                runOnUiThread(() -> {
                    updateDictionaryStatus();
                    toast("Importate " + imported + " intrări.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> dictionaryStatus.setText("Import eșuat: " + e.getMessage()));
            }
        });
    }

    private void updateDictionaryStatus() {
        long count = new DictionaryStore(this).count();
        dictionaryStatus.setText(count + " intrări locale.");
    }

    private Switch toggle(String title, boolean checked) {
        Switch view = new Switch(this);
        view.setText(title);
        view.setTextSize(14);
        view.setChecked(checked);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    private RadioButton radio(String text) {
        RadioButton button = new RadioButton(this);
        button.setId(android.view.View.generateViewId());
        button.setText(text);
        return button;
    }

    private TextView label(String text) {
        TextView view = body(text);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(10), 0, dp(2));
        return view;
    }

    private TextView heading(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setTextColor(Color.rgb(23, 32, 42));
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(Color.rgb(70, 75, 82));
        return view;
    }

    private TextView valueText() {
        TextView view = body("");
        view.setGravity(Gravity.END);
        return view;
    }

    private TextView sectionGap() {
        TextView gap = new TextView(this);
        gap.setHeight(dp(14));
        return gap;
    }

    private void updateCharsLabel(int value) {
        charsValue.setText(value == 0 ? "Termen complet" : value + " caractere");
    }

    private void updatePrecisionLabel(int value) {
        String zone;
        if (value >= 80) zone = "precis";
        else if (value >= 50) zone = "echilibrat";
        else zone = "stabil";
        precisionValue.setText(value + "% • " + zone);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
    }
}
