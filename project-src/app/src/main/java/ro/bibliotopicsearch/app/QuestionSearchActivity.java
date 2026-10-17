package ro.bibliotopicsearch.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Question -> grounded passages in ALL locally archived books -> original page.
 * No LLM, external search, generated wording, or silent interpretation.
 */
public final class QuestionSearchActivity extends AppCompatActivity {
    public static final String EXTRA_QUESTION = "research_question";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private final List<QuestionEvidenceSearch.Passage> shown = new ArrayList<>();
    private QuestionEvidenceSearch.Report lastReport;
    private EditText input;
    private TextView status;
    private CheckBox showRelated;
    private BaseAdapter adapter;
    private boolean destroyed;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        String initial = getIntent().getStringExtra(EXTRA_QUESTION);
        if (initial != null && !initial.trim().isEmpty()) {
            input.setText(initial);
            executeSearch();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(12, 20, 27));
        root.setPadding(dp(14), dp(14), dp(14), dp(14));

        TextView title = label("ÎNTREBARE → PASAJE DIN SURSE", 19, Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView explanation = label(
                "Caută determinist în toate PDF-urile și scanările arhivate cu OCR, "
                        + "nu doar în sursa sau harta activă. Pasajele sunt extrase din OCR "
                        + "și se deschid pe pagina originală pentru verificare.",
                12, Color.rgb(172, 194, 205));
        explanation.setPadding(0, dp(6), 0, dp(10));
        root.addView(explanation);

        input = new EditText(this);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setHint("Ex.: Când a fost înființată instituția?");
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                executeSearch();
                return true;
            }
            return false;
        });
        root.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button submit = new Button(this);
        submit.setText("CAUTĂ ÎN TOATE SURSELE");
        submit.setAllCaps(false);
        submit.setOnClickListener(v -> executeSearch());
        root.addView(submit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        showRelated = new CheckBox(this);
        showRelated.setText("Include și pasaje conexe (nu sunt răspunsuri directe)");
        showRelated.setTextColor(Color.rgb(200, 213, 222));
        showRelated.setChecked(false);
        showRelated.setOnCheckedChangeListener((button, checked) -> updateVisible());
        root.addView(showRelated);

        status = label(
                "Introdu o întrebare și apasă CAUTĂ. Nu se construiesc răspunsuri din presupuneri.",
                12, Color.rgb(169, 222, 189));
        status.setPadding(0, dp(7), 0, dp(9));
        root.addView(status);

        ListView results = new ListView(this);
        results.setDividerHeight(dp(4));
        results.setCacheColorHint(Color.TRANSPARENT);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int position) { return shown.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View reusable, ViewGroup parent) {
                TextView row = reusable instanceof TextView
                        ? (TextView) reusable : label("", 14, Color.WHITE);
                QuestionEvidenceSearch.Passage item = shown.get(position);
                row.setPadding(dp(10), dp(10), dp(10), dp(12));
                row.setBackgroundColor(position % 2 == 0
                        ? Color.rgb(28, 40, 49) : Color.rgb(22, 33, 42));
                row.setTextColor(Color.WHITE);
                row.setMaxLines(10);
                row.setText((item.directCandidate
                        ? "INDICIU DIRECT • " : "PASAJ CONEX • ")
                        + (item.manuallyVerified ? "PAGINĂ VERIFICATĂ" : "OCR NEVERIFICAT")
                        + "\n" + item.sourceTitle + " • pagina " + item.page
                        + "\n" + item.excerpt);
                return row;
            }
        };
        results.setAdapter(adapter);
        results.setOnItemClickListener((parent, view, position, id) ->
                openOriginal(shown.get(position)));
        root.addView(results, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button back = new Button(this);
        back.setText("ÎNAPOI");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        setContentView(root);
    }

    private void executeSearch() {
        final String question = input.getText().toString().trim();
        final int current = generation.incrementAndGet();
        lastReport = null;
        shown.clear();
        adapter.notifyDataSetChanged();
        if (question.isEmpty()) {
            status.setText("Scrie întrebarea pentru care cauți dovezi în surse.");
            return;
        }
        status.setText("Parcurg pasajele OCR din toate sursele arhivate…");
        worker.execute(() -> {
            QuestionEvidenceSearch.Report report;
            try {
                report = QuestionEvidenceSearch.search(getApplicationContext(), question);
            } catch (Exception failure) {
                final String error = failure.getMessage();
                runOnUiThread(() -> {
                    if (!destroyed && generation.get() == current) {
                        status.setText("Căutare nereușită: " + error);
                    }
                });
                return;
            }
            runOnUiThread(() -> {
                if (destroyed || generation.get() != current) return;
                lastReport = report;
                updateVisible();
            });
        });
    }

    private void updateVisible() {
        shown.clear();
        QuestionEvidenceSearch.Report report = lastReport;
        if (report == null) {
            adapter.notifyDataSetChanged();
            return;
        }
        if (!report.queryUsable) {
            status.setText("Întrebarea nu conține termeni-țintă suficienți. Precizează subiectul.");
            adapter.notifyDataSetChanged();
            return;
        }
        for (QuestionEvidenceSearch.Passage passage : report.passages) {
            if (passage.directCandidate || showRelated.isChecked()) shown.add(passage);
        }
        int related = report.passages.size() - report.directCandidates;
        StringBuilder summary = new StringBuilder();
        summary.append("Surse OCR cercetate: ").append(report.sourcesSearched)
                .append(" • blocuri/pagini: ").append(report.blocksSearched)
                .append(" • pasaje cu indicii directe: ").append(report.directCandidates)
                .append(" • pasaje conexe: ").append(related)
                .append("\n");
        if (report.directCandidates == 0) {
            summary.append("Nu am identificat răspunsuri direct susținute de indicii explicite. ");
            if (related > 0 && !showRelated.isChecked()) {
                summary.append("Poți afișa separat pasajele conexe.");
            } else {
                summary.append("Lipsa unei potriviri OCR nu dovedește absența răspunsului din original.");
            }
        } else {
            summary.append("Atinge fiecare rezultat pentru pagina originală; ")
                    .append("un indiciu nu este o confirmare automată a răspunsului.");
        }
        status.setText(summary.toString());
        adapter.notifyDataSetChanged();
    }

    private void openOriginal(QuestionEvidenceSearch.Passage chosen) {
        status.setText("Localizez pasajul în pagina originală…");
        worker.execute(() -> {
            int line = -1;
            String warning = null;
            try {
                line = QuestionEvidenceSearch.closestLine(getApplicationContext(), chosen);
            } catch (Exception error) {
                warning = error.getMessage();
            }
            final int targetLine = line;
            final String failure = warning;
            runOnUiThread(() -> {
                if (destroyed) return;
                if (failure != null) {
                    status.setText("Nu am localizat exact linia; deschid pagina: " + failure);
                }
                Intent intent = new Intent(this, OriginalPageActivity.class);
                intent.putExtra(OriginalPageActivity.EXTRA_SOURCE_ID, chosen.sourceId);
                intent.putExtra(OriginalPageActivity.EXTRA_PAGE, chosen.page);
                intent.putExtra(OriginalPageActivity.EXTRA_LINE_INDEX, targetLine);
                startActivity(intent);
            });
        });
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        generation.incrementAndGet();
        worker.shutdownNow();
        super.onDestroy();
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
