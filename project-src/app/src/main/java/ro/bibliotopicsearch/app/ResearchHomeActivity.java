package ro.bibliotopicsearch.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Simple launcher organized around the historian's real workflow rather than
 * internal app modules.
 */
public final class ResearchHomeActivity extends AppCompatActivity {

    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(12, 20, 27));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("BiblioTopicSearch", 24f, Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = text(
                "Cartografiere istorică • offline • sursa rămâne controlabilă",
                12.2f,
                Color.rgb(146, 202, 177)
        );
        subtitle.setPadding(0, 0, 0, dp(12));
        root.addView(subtitle);

        TopicMap map = TopicMapStore.load(this);
        MapProfile profile = TopicLibraryStore.getActive(this);
        int termCount = 0;
        int activeNodes = 0;
        for (TopicNode node : map.nodes) {
            if (node == null) continue;
            if (node.enabled) activeNodes++;
            termCount += node.terms == null || node.terms.isEmpty() ? 1 : node.terms.size();
        }
        boolean mapReady = !map.nodes.isEmpty();

        IndexCoreDatabase db = IndexCoreDatabase.get(this);
        IndexCoreDatabase.SourceRecord source = IndexCoreSourceRegistry.activeSource(this);
        boolean sourceReady = source != null && !source.title.trim().isEmpty();
        IndexCoreCartographyReader.Stats coverage = source == null
                ? null
                : IndexCoreCartographyReader.stats(db, source.id);

        addStateCard(
                "1. TEME",
                mapReady
                        ? "✓ " + profile.name + " • " + activeNodes + " noduri active • " + termCount + " termeni"
                        : "○ Nu ai încă o hartă tematică",
                mapReady
        );

        addStateCard(
                "2. SURSĂ",
                sourceReady
                        ? "✓ " + source.displayName()
                            + coverageLabel(coverage)
                        : "○ Creează sau identifică sursa înainte de cartografiere",
                sourceReady
        );

        Button continueButton = bigButton(continueLabel(mapReady, sourceReady), true);
        continueButton.setOnClickListener(v -> {
            if (!mapReady) {
                startActivity(new Intent(this, TopicLibraryActivity.class));
            } else if (!sourceReady) {
                openSourceSetup();
            } else {
                startActivity(new Intent(this, LensActivity.class));
            }
        });
        root.addView(continueButton, full(dp(60)));

        TextView setup = section("PREGĂTIRE");
        root.addView(setup);

        LinearLayout setupRow = row();
        Button themes = actionButton("TEME", "Creează / editează harta");
        themes.setOnClickListener(v ->
                startActivity(new Intent(this, TopicLibraryActivity.class)));
        setupRow.addView(themes, weighted(dp(66)));

        Button sourceButton = actionButton("SURSĂ", "Titlu, autor, ediție, domeniu");
        sourceButton.setOnClickListener(v -> openSourceSetup());
        setupRow.addView(sourceButton, weighted(dp(66)));
        root.addView(setupRow);

        TextView capture = section("CARTOGRAFIERE");
        root.addView(capture);

        LinearLayout captureRow = row();
        Button camera = actionButton("CARTE FIZICĂ", "Camera • AUTO • ÎNCHEIE ✓");
        camera.setOnClickListener(v ->
                startActivity(new Intent(this, LensActivity.class)));
        captureRow.addView(camera, weighted(dp(72)));

        Button pdf = actionButton("PDF", "Import în lot, offline");
        pdf.setOnClickListener(v ->
                startActivity(new Intent(this, DigitalSourceImportActivity.class)));
        captureRow.addView(pdf, weighted(dp(72)));
        root.addView(captureRow);

        TextView research = section("LUCRU ISTORIC");
        root.addView(research);

        LinearLayout researchRow = row();
        Button results = actionButton("REZULTATE", "Acoperire, arii, ×, corpus");
        results.setOnClickListener(v ->
                startActivity(new Intent(this, IndexCoreActivity.class)));
        researchRow.addView(results, weighted(dp(72)));

        Button workbench = actionButton("ATELIER", "Critica sursei • afirmații • dovezi");
        workbench.setOnClickListener(v ->
                startActivity(new Intent(this, HistorianWorkbenchActivity.class)));
        researchRow.addView(workbench, weighted(dp(72)));
        root.addView(researchRow);

        TextView tools = section("OPȚIONAL");
        root.addView(tools);

        LinearLayout toolsRow = row();
        Button settings = actionButton("SETĂRI", "Căutare, dicționar, culori");
        settings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        toolsRow.addView(settings, weighted(dp(62)));

        Button help = actionButton("CUM LUCREZ?", "Fluxul în 60 de secunde");
        help.setOnClickListener(v -> showQuickHelp());
        toolsRow.addView(help, weighted(dp(62)));
        root.addView(toolsRow);

        TextView principle = text(
                "Flux recomandat: TEME → SURSĂ → CARTOGRAFIERE → EXTRACȚIE → ATELIER. "
                        + "Nu trebuie să folosești toate ecranele la fiecare sesiune.",
                11.6f,
                Color.rgb(167, 186, 198)
        );
        principle.setPadding(0, dp(14), 0, 0);
        root.addView(principle);

        setContentView(scroll);
    }

    private void openSourceSetup() {
        Intent intent = new Intent(this, IndexCoreActivity.class);
        intent.putExtra(IndexCoreActivity.EXTRA_OPEN_PANEL, IndexCoreActivity.PANEL_SOURCES);
        startActivity(intent);
    }

    private String continueLabel(boolean mapReady, boolean sourceReady) {
        if (!mapReady) return "ÎNCEPE: ALEGE TEMELE";
        if (!sourceReady) return "CONTINUĂ: DEFINEȘTE SURSA";
        return "CONTINUĂ: SCANEAZĂ / CARTOGRAFIAZĂ";
    }

    private String coverageLabel(IndexCoreCartographyReader.Stats coverage) {
        if (coverage == null) return "";
        if (coverage.scopeDefined()) {
            return " • " + coverage.pagesComplete + "/" + coverage.expectedPages + " pagini încheiate"
                    + (coverage.exhaustive() ? " • EXHAUSTIVĂ ✓" : "");
        }
        if (coverage.pagesSeen > 0) return " • " + coverage.pagesSeen + " pagini văzute";
        return "";
    }

    private void addStateCard(String heading, String value, boolean ready) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        card.setBackgroundColor(ready ? Color.rgb(24, 54, 48) : Color.rgb(50, 43, 31));

        TextView h = text(heading, 11.5f, ready
                ? Color.rgb(139, 222, 184)
                : Color.rgb(238, 190, 107));
        h.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(h);

        TextView body = text(value, 13f, Color.WHITE);
        body.setPadding(0, dp(2), 0, 0);
        card.addView(body);

        LinearLayout.LayoutParams lp = full(ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(7));
        root.addView(card, lp);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private TextView section(String value) {
        TextView view = text(value, 11.5f, Color.rgb(147, 170, 184));
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(15), 0, dp(5));
        return view;
    }

    private Button actionButton(String title, String subtitle) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title + "\n" + subtitle);
        button.setTextSize(11f);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(7), dp(4), dp(7), dp(4));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(48, 83, 107)));
        return button;
    }

    private Button bigButton(String value, boolean primary) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextSize(13.5f);
        button.setTextColor(Color.WHITE);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setBackgroundTintList(ColorStateList.valueOf(
                primary ? Color.rgb(42, 128, 94) : Color.rgb(48, 83, 107)
        ));
        return button;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.12f);
        return view;
    }

    private void showQuickHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Flux simplu")
                .setMessage(
                        "1. TEME — definești ce urmărești.\n\n"
                                + "2. SURSĂ — identifici cartea și, dacă vrei exhaustivitate, intervalul de pagini.\n\n"
                                + "3. CARTE FIZICĂ sau PDF — o parcurgi o singură dată. Toate temele active sunt cartografiate simultan.\n\n"
                                + "4. REZULTATE — verifici acoperirea, ariile, intersecțiile și extragi pasajele.\n\n"
                                + "5. ATELIER — formulezi tu afirmațiile și le legi de dovezile exacte.\n\n"
                                + "Pentru început sunt suficiente primele trei etape."
                )
                .setPositiveButton("AM ÎNȚELES", null)
                .show();
    }

    private LinearLayout.LayoutParams weighted(int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, height, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private LinearLayout.LayoutParams full(int height) {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
