package ro.bibliotopicsearch.app;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Scalable v7 workspace: stable sources, physical outline and facet intersections. */
public final class IndexCoreActivity extends AppCompatActivity {
    private static final int REQ_EXPORT_CARTOGRAPHY = 401;

    private IndexCoreDatabase db;
    private String sourceId;
    private FacetIntersectionQuery.Parsed query = FacetIntersectionQuery.parse("");
    private List<IndexCoreDatabase.SearchResult> rows = Collections.emptyList();
    private String pendingExportText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = IndexCoreDatabase.get(this);
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        sourceId = IndexCoreSourceRegistry.activeSourceId(this);
        rows = IndexCoreIntersectionEngine.search(db, query.filters, sourceId, query.pageFrom, query.pageTo, 300);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.setBackgroundColor(Color.rgb(12, 20, 27));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(17.5f, Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setText("INDEX CORE v7");
        top.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button sources = button("SURSE");
        sources.setOnClickListener(v -> showSources());
        top.addView(sources, new LinearLayout.LayoutParams(dp(76), dp(42)));
        Button outline = button("OUTLINE");
        outline.setOnClickListener(v -> showOutline());
        top.addView(outline, new LinearLayout.LayoutParams(dp(82), dp(42)));
        Button intersect = button("INTERSECȚIE");
        intersect.setOnClickListener(v -> showIntersection());
        top.addView(intersect, new LinearLayout.LayoutParams(dp(106), dp(42)));
        Button close = button("ÎNAPOI");
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(dp(78), dp(42)));
        root.addView(top);

        LinearLayout research = new LinearLayout(this);
        research.setGravity(Gravity.CENTER_VERTICAL);
        Button coverage = button("ACOPERIRE");
        coverage.setOnClickListener(v -> showCoverage());
        research.addView(coverage, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button ranges = button("ARII");
        ranges.setOnClickListener(v -> showCoverageRanges());
        research.addView(ranges, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button crossings = button("× DET.");
        crossings.setOnClickListener(v -> showDeterministicIntersections());
        research.addView(crossings, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button matrix = button("MATRICE");
        matrix.setOnClickListener(v -> showMatrix());
        research.addView(matrix, new LinearLayout.LayoutParams(0, dp(42), 1f));
        root.addView(research);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        Button chronology = button("CRONOLOGIE");
        chronology.setOnClickListener(v -> showFacetIndex("YEAR", "Cronologie explicită"));
        navigation.addView(chronology, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button time = button("TIMP");
        time.setOnClickListener(v -> showFacetIndex("TIME", "Expresii temporale"));
        navigation.addView(time, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button places = button("LOCURI");
        places.setOnClickListener(v -> showFacetIndex("PLACE", "Locuri explicite"));
        navigation.addView(places, new LinearLayout.LayoutParams(0, dp(42), 1f));
        root.addView(navigation);

        LinearLayout audit = new LinearLayout(this);
        audit.setGravity(Gravity.CENTER_VERTICAL);
        Button review = button("REVIZUIRE");
        review.setOnClickListener(v -> showReviewQueue());
        audit.addView(review, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button extraction = button("EXTRACȚIE");
        extraction.setOnClickListener(v -> showExtractionTopics());
        audit.addView(extraction, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button corpus = button("CORPUS");
        corpus.setOnClickListener(v -> showCorpusTopics());
        audit.addView(corpus, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button export = button("EXPORT FIȘĂ");
        export.setOnClickListener(v -> exportCartography());
        audit.addView(export, new LinearLayout.LayoutParams(0, dp(42), 1f));
        root.addView(audit);

        LinearLayout method = new LinearLayout(this);
        method.setGravity(Gravity.CENTER_VERTICAL);
        Button workbench = button("ATELIER ISTORIC • AFIRMAȚII + DOVEZI + CRITICA SURSEI");
        workbench.setOnClickListener(v ->
                startActivity(new Intent(this, HistorianWorkbenchActivity.class)));
        method.addView(workbench, new LinearLayout.LayoutParams(0, dp(44), 1f));
        root.addView(method);

        IndexCoreDatabase.SourceRecord source = db.source(sourceId);
        IndexCoreDatabase.Stats stats = db.stats();
        IndexCoreCartographyReader.Stats cartography = IndexCoreCartographyReader.stats(db, sourceId);
        TextView summary = text(12.2f, Color.rgb(207, 221, 230));
        summary.setText(
                "SURSĂ ACTIVĂ: " + (source == null ? "—" : source.displayName())
                        + "\nFILTRU: " + FacetIntersectionQuery.describe(query)
                        + "\nDB: " + stats.sources + " surse • " + stats.entries + " intrări • "
                        + stats.occurrences + " apariții • " + stats.facets + " fațete • " + stats.outlines + " noduri outline"
                        + "\nREZULTATE ÎN SURSĂ: " + rows.size() + " • aparițiile SQLite nu au plafonul de 256."
                        + "\nCARTOGRAFIE: " + cartography.paragraphUnits + " paragrafe • "
                        + cartography.clauseUnits + " clauze • " + cartography.distinctTopics + " indici • "
                        + cartography.intersections + " încrucișări"
                        + (cartography.scopeDefined()
                            ? "\nACOPERIRE CONFIRMATĂ: " + cartography.pagesComplete + "/" + cartography.expectedPages
                                + " pagini (" + cartography.coveragePercent() + "%)"
                                + (cartography.exhaustive() ? " • EXHAUSTIVĂ ✓" : "")
                            : "\nACOPERIRE: definește intervalul sursei pentru măsurare exhaustivă")
                        + (cartography.unresolvedUnits > 0
                            ? "\nDE VERIFICAT: " + cartography.unresolvedUnits + " unități excepționale"
                            : "")
        );
        summary.setPadding(0, dp(4), 0, dp(8));
        root.addView(summary);

        List<String> display = new ArrayList<>();
        for (IndexCoreDatabase.SearchResult row : rows) {
            display.add(row.canonical + "\n" + row.category + " • " + row.occurrences + " apariții" + (row.validated ? " • VALIDAT" : ""));
        }
        ListView list = new ListView(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, display) {
            @Override public android.view.View getView(int position, android.view.View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.WHITE); view.setTextSize(12.8f); view.setPadding(dp(12), dp(10), dp(12), dp(10));
                view.setBackgroundColor(Color.rgb(23, 36, 45));
                return view;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, position, id) -> showResult(rows.get(position)));
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void showSources() {
        List<IndexCoreDatabase.SourceRecord> sources = new ArrayList<>(IndexCoreSourceRegistry.sources(this));
        String[] labels = new String[sources.size() + 1];
        for (int i = 0; i < sources.size(); i++) {
            IndexCoreDatabase.SourceRecord s = sources.get(i);
            labels[i] = (s.id.equals(sourceId) ? "✓ " : "") + s.displayName()
                    + (s.author.isEmpty() ? "" : " • " + s.author);
        }
        labels[labels.length - 1] = "＋ SURSĂ NOUĂ";
        new AlertDialog.Builder(this)
                .setTitle("Registru surse")
                .setItems(labels, (dialog, which) -> {
                    if (which == sources.size()) createSource();
                    else {
                        IndexCoreSourceRegistry.setActiveSource(this, sources.get(which).id);
                        query = FacetIntersectionQuery.parse("");
                        rebuild();
                    }
                })
                .setNeutralButton("META SURSĂ ACTIVĂ", (d, w) -> editActiveSource())
                .setNegativeButton("ÎNCHIDE", null)
                .show();
    }

    private void createSource() {
        IndexCoreSourceRegistry.newSource(this, "", "", "", "", "");
        editActiveSource();
    }

    private void editActiveSource() {
        IndexCoreDatabase.SourceRecord old = IndexCoreSourceRegistry.activeSource(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18), dp(8), dp(18), 0);
        EditText title = field("Titlul sursei/cărții", old == null ? "" : old.title); box.addView(title);
        EditText author = field("Autor", old == null ? "" : old.author); box.addView(author);
        EditText edition = field("Ediție / volum", old == null ? "" : old.edition); box.addView(edition);
        EditText isbn = field("ISBN (opțional)", old == null ? "" : old.isbn); box.addView(isbn);
        EditText locator = field("Bibliotecă / localizare / observație", old == null ? "" : old.locator); box.addView(locator);
        new AlertDialog.Builder(this)
                .setTitle("Identitate permanentă a sursei")
                .setView(box)
                .setPositiveButton("SALVEAZĂ", (d, w) -> {
                    IndexCoreSourceRegistry.updateActive(this, title.getText().toString(), author.getText().toString(),
                            edition.getText().toString(), isbn.getText().toString(), locator.getText().toString());
                    rebuild();
                })
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void showOutline() {
        List<IndexCoreDatabase.OutlineRecord> values = db.outlinesForSource(sourceId);
        if (values.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Outline sursă")
                    .setMessage("Nu au fost detectate încă titluri/capitole/secțiuni în această sursă.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (IndexCoreDatabase.OutlineRecord node : values) {
            if (shown++ >= 240) { out.append("\n…"); break; }
            out.append('\n');
            for (int i = 0; i < node.depth; i++) out.append("   ");
            out.append("• ").append(node.kind).append(" • ").append(node.title);
            if (!node.pageStart.isEmpty()) out.append(" • pag. ").append(node.pageStart);
        }
        new AlertDialog.Builder(this).setTitle("CARTE → PARTE → CAPITOL → SECȚIUNE")
                .setMessage(out.toString().trim()).setPositiveButton("ÎNCHIDE", null).show();
    }

    private void showCoverage() {
        IndexCoreCartographyReader.Stats stats = IndexCoreCartographyReader.stats(db, sourceId);
        IndexCoreDatabase.SourceScope scope = db.sourceScope(sourceId);
        StringBuilder msg = new StringBuilder();
        if (scope.defined()) {
            msg.append("DOMENIU DECLARAT: pag. ").append(scope.pageFrom).append("–").append(scope.pageTo)
                    .append(" • ").append(scope.expectedPages()).append(" pagini")
                    .append("\nPAGINI ÎNCHEIATE: ").append(stats.pagesComplete).append("/").append(stats.expectedPages)
                    .append(" • ").append(stats.coveragePercent()).append("%");
            if (stats.exhaustive()) msg.append("\nSTARE: EXHAUSTIVĂ ✓");
            else msg.append("\nLIPSESC: ").append(compactPages(stats.missingPages, 60));
        } else {
            msg.append("Nu este definit încă intervalul complet al sursei. ")
                    .append("Fără un domeniu declarat, aplicația nu pretinde că sursa este exhaustiv cartografiată.");
        }
        msg.append("\n\nPAGINI VĂZUTE: ").append(stats.pagesSeen)
                .append("\nPARAGRAFE CONTABILIZATE: ").append(stats.paragraphUnits)
                .append("\nCLAUZE CONTABILIZATE: ").append(stats.clauseUnits)
                .append("\nUNITĂȚI CU INDICE: ").append(stats.mappedUnits)
                .append("\nFĂRĂ INDICE TEMATIC: ").append(stats.noIndexUnits)
                .append("\nAMBIGUE: ").append(stats.ambiguousUnits)
                .append("\nNEPARSE: ").append(stats.unparsedUnits)
                .append("\nEXCEPȚII NEREVIZUITE: ").append(stats.unresolvedUnits)
                .append("\nÎNCRUCIȘĂRI DETERMINISTE: ").append(stats.intersections)
                .append("\nINDICI DISTINCȚI: ").append(stats.distinctTopics)
                .append("\n\nO pagină intră la «încheiată» numai prin comanda explicită ÎNCHEIE PAGINA ✓.");

        new AlertDialog.Builder(this)
                .setTitle("Registrul exhaustivității")
                .setMessage(msg.toString())
                .setPositiveButton("DEFINEȘTE DOMENIUL", (d, w) -> editCoverageScope())
                .setNegativeButton("ÎNCHIDE", null)
                .show();
    }

    private void editCoverageScope() {
        IndexCoreDatabase.SourceScope old = db.sourceScope(sourceId);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), 0);
        EditText from = field("Prima pagină inclusă", old.defined() ? String.valueOf(old.pageFrom) : "");
        EditText to = field("Ultima pagină inclusă", old.defined() ? String.valueOf(old.pageTo) : "");
        from.setInputType(InputType.TYPE_CLASS_NUMBER);
        to.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(from);
        box.addView(to);

        new AlertDialog.Builder(this)
                .setTitle("Domeniul fizic al sursei")
                .setMessage("Acest interval este etalonul exhaustivității. Pagina este bifată numai când o închei explicit în LUPĂ.")
                .setView(box)
                .setPositiveButton("SALVEAZĂ", (d, w) -> {
                    int a = parseInt(from.getText().toString());
                    int b = parseInt(to.getText().toString());
                    db.setSourceScope(sourceId, a, b);
                    rebuild();
                })
                .setNeutralButton("ȘTERGE DOMENIUL", (d, w) -> {
                    db.setSourceScope(sourceId, 0, 0);
                    rebuild();
                })
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void showCorpusTopics() {
        List<IndexCoreCartographyReader.MatrixRow> topics =
                IndexCoreCartographyReader.matrix(db, 300);
        if (topics.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Corpus tematic")
                    .setMessage("Nu există încă surse cartografiate exact.")
                    .setPositiveButton("OK", null).show();
            return;
        }

        String[] labels = new String[topics.size()];
        for (int i = 0; i < topics.size(); i++) {
            IndexCoreCartographyReader.MatrixRow row = topics.get(i);
            int sources = 0;
            int units = 0;
            for (Integer count : row.countsBySource.values()) {
                if (count != null && count > 0) sources++;
                if (count != null) units += count;
            }
            labels[i] = (row.topicTitle.isEmpty() ? row.topicPath : row.topicTitle)
                    + " • " + sources + " surse • " + units + " zone-paragraf";
        }

        new AlertDialog.Builder(this)
                .setTitle("Alege tema transversală")
                .setItems(labels, (d, which) -> showCorpusExtraction(topics.get(which)))
                .setNegativeButton("ÎNCHIDE", null)
                .show();
    }

    private void showCorpusExtraction(IndexCoreCartographyReader.MatrixRow topic) {
        if (topic == null) return;
        List<IndexCoreCartographyReader.CorpusExtractionItem> items =
                IndexCoreCartographyReader.corpusExtractionQueue(db, topic.topicPath, 3000);
        StringBuilder out = new StringBuilder();
        String lastSource = null;
        int shown = 0;
        for (IndexCoreCartographyReader.CorpusExtractionItem item : items) {
            if (shown++ >= 900) { out.append("\n…"); break; }
            if (!item.sourceId.equals(lastSource)) {
                if (out.length() > 0) out.append("\n");
                out.append("\n[").append(item.sourceTitle.isEmpty() ? item.sourceId : item.sourceTitle).append("]");
                lastSource = item.sourceId;
            }
            out.append("\n• ");
            if (!item.page.isEmpty()) out.append("pag. ").append(item.page).append(" • ");
            out.append("P").append(item.paragraphIndex + 1);
            if (item.sentenceIndex >= 0) out.append(" S").append(item.sentenceIndex + 1);
            if (item.clauseIndex >= 0) out.append(" C").append(item.clauseIndex + 1);
            if (!item.outlineTitle.isEmpty()) out.append(" • ").append(item.outlineTitle);
            if (!item.function.isEmpty()) out.append(" • F:").append(item.function);
            if (!item.relation.isEmpty() && !"GENERIC".equals(item.relation)) {
                out.append(" • R:").append(item.relation);
            }
            if (!item.surface.isEmpty()) out.append(" • „").append(trim(item.surface, 48)).append("”");
            if (!item.crossings.isEmpty()) out.append(" • × ").append(trim(item.crossings, 90));
        }

        new AlertDialog.Builder(this)
                .setTitle("Corpus — " + (topic.topicTitle.isEmpty() ? topic.topicPath : topic.topicTitle))
                .setMessage(out.length() == 0 ? "Nu există locații." : out.toString().trim())
                .setPositiveButton("ÎNCHIDE", null)
                .show();
    }

    private void showExtractionTopics() {
        List<IndexCoreCartographyReader.TopicSummary> topics =
                IndexCoreCartographyReader.topicsForSource(db, sourceId, 300);
        if (topics.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Extracție țintită")
                    .setMessage("Nu există încă indici proveniți din CAPTURĂ EXACTĂ.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        String[] labels = new String[topics.size()];
        for (int i = 0; i < topics.size(); i++) {
            IndexCoreCartographyReader.TopicSummary topic = topics.get(i);
            labels[i] = (topic.topicTitle.isEmpty() ? topic.topicPath : topic.topicTitle)
                    + " • " + topic.units + " unități • " + topic.pages + " pagini";
        }
        new AlertDialog.Builder(this)
                .setTitle("Alege indicele pentru extracție")
                .setItems(labels, (d, which) -> showExtractionQueue(topics.get(which)))
                .setNegativeButton("ÎNCHIDE", null)
                .show();
    }

    private void showExtractionQueue(IndexCoreCartographyReader.TopicSummary topic) {
        if (topic == null) return;
        List<IndexCoreCartographyReader.ExtractionItem> items =
                IndexCoreCartographyReader.extractionQueue(db, sourceId, topic.topicPath, 1200);
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (IndexCoreCartographyReader.ExtractionItem item : items) {
            if (shown++ >= 500) { out.append("\n…"); break; }
            out.append("\n• ");
            if (!item.page.isEmpty()) out.append("pag. ").append(item.page).append(" • ");
            out.append("P").append(item.paragraphIndex + 1);
            if (item.sentenceIndex >= 0) out.append(" S").append(item.sentenceIndex + 1);
            if (item.clauseIndex >= 0) out.append(" C").append(item.clauseIndex + 1);
            out.append(" • ").append(item.unitKind);
            if (!item.outlineTitle.isEmpty()) out.append(" • ").append(item.outlineTitle);
            if (!item.function.isEmpty()) out.append(" • F:").append(item.function);
            if (!item.relation.isEmpty() && !"GENERIC".equals(item.relation)) {
                out.append(" • R:").append(item.relation);
            }
            if (!item.surface.isEmpty()) out.append(" • „").append(trim(item.surface, 48)).append("”");
            if (!item.crossings.isEmpty()) out.append(" • × ").append(trim(item.crossings, 90));
        }
        new AlertDialog.Builder(this)
                .setTitle("Coada de extracție — " + (topic.topicTitle.isEmpty() ? topic.topicPath : topic.topicTitle))
                .setMessage(out.length() == 0 ? "Nu există locații." : out.toString().trim())
                .setPositiveButton("ÎNCHIDE", null)
                .show();
    }

    private void showReviewQueue() {
        List<IndexCoreCartographyReader.ReviewItem> items =
                IndexCoreCartographyReader.reviewQueue(db, sourceId, 500);
        if (items.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Revizuire excepții")
                    .setMessage("Nu există unități AMBIGUOUS/UNPARSED nerevizuite. Registrul nu are excepții deschise.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            IndexCoreCartographyReader.ReviewItem item = items.get(i);
            StringBuilder label = new StringBuilder();
            if (!item.page.isEmpty()) label.append("pag. ").append(item.page).append(" • ");
            label.append("P").append(item.paragraphIndex + 1)
                    .append(" • ").append(item.unitKind)
                    .append(" • ").append(item.status);
            if (!item.outlineTitle.isEmpty()) label.append(" • ").append(item.outlineTitle);
            if (!item.evidence.isEmpty()) label.append("\n").append(trim(item.evidence, 110));
            labels[i] = label.toString();
        }

        new AlertDialog.Builder(this)
                .setTitle("Excepții nerevizuite: " + items.size())
                .setItems(labels, (dialog, which) -> reviewUnit(items.get(which)))
                .setNegativeButton("ÎNCHIDE", null)
                .show();
    }

    private void reviewUnit(IndexCoreCartographyReader.ReviewItem item) {
        if (item == null) return;
        StringBuilder detail = new StringBuilder();
        if (!item.page.isEmpty()) detail.append("Pagina: ").append(item.page).append("\n");
        detail.append("Paragraf: ").append(item.paragraphIndex + 1)
                .append("\nUnitate: ").append(item.unitKind)
                .append("\nStare automată: ").append(item.status);
        if (!item.function.isEmpty()) detail.append("\nFuncție: ").append(item.function);
        if (!item.relation.isEmpty()) detail.append("\nRelație: ").append(item.relation);
        if (!item.outlineTitle.isEmpty()) detail.append("\nSecțiune: ").append(item.outlineTitle);
        if (!item.evidence.isEmpty()) detail.append("\nDovezi tematice: ").append(item.evidence);
        detail.append("\n\nRevizuirea este umană și nu inventează conținut. Dacă clasificarea trebuie schimbată, modifică harta și recapturează pasajul.");

        new AlertDialog.Builder(this)
                .setTitle("Audit unitate")
                .setMessage(detail.toString())
                .setItems(new String[]{
                        "REVIZUITĂ — păstrează starea explicită",
                        "CONFIRMĂ FĂRĂ INDICE TEMATIC",
                        "NON-CONȚINUT / ELEMENT EDITORIAL"
                }, (d, which) -> {
                    if (which == 0) db.reviewSemanticUnit(item.unitId, "ACKNOWLEDGED");
                    else if (which == 1) db.reviewSemanticUnit(item.unitId, "CONFIRMED_NO_INDEX");
                    else db.reviewSemanticUnit(item.unitId, "NON_CONTENT");
                    rebuild();
                })
                .setNegativeButton("LASĂ NEREVIZUITĂ", null)
                .show();
    }

    private void showFacetIndex(String dimension, String title) {
        List<IndexCoreCartographyReader.FacetSummary> values =
                IndexCoreCartographyReader.facetSummary(db, sourceId, dimension, 1200);
        if (values.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage("Nu există încă valori explicite pentru acest tip în CAPTURĂ EXACTĂ.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String[] labels = new String[values.size()];
        for (int i = 0; i < values.size(); i++) {
            IndexCoreCartographyReader.FacetSummary value = values.get(i);
            labels[i] = value.value + " • " + value.units + " unități • " + value.pages + " pagini";
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels, (d, which) -> showFacetLocations(values.get(which)))
                .setNegativeButton("ÎNCHIDE", null)
                .show();
    }

    private void showFacetLocations(IndexCoreCartographyReader.FacetSummary facet) {
        if (facet == null) return;
        List<IndexCoreCartographyReader.FacetLocation> items =
                IndexCoreCartographyReader.facetLocations(
                        db, sourceId, facet.dimension, facet.value, 2000
                );
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (IndexCoreCartographyReader.FacetLocation item : items) {
            if (shown++ >= 700) { out.append("\n…"); break; }
            out.append("\n• ");
            if (!item.page.isEmpty()) out.append("pag. ").append(item.page).append(" • ");
            out.append("P").append(item.paragraphIndex + 1);
            if (item.sentenceIndex >= 0) out.append(" S").append(item.sentenceIndex + 1);
            if (item.clauseIndex >= 0) out.append(" C").append(item.clauseIndex + 1);
            if (!item.outlineTitle.isEmpty()) out.append(" • ").append(item.outlineTitle);
            if (!item.function.isEmpty()) out.append(" • F:").append(item.function);
            if (!item.relation.isEmpty() && !"GENERIC".equals(item.relation)) {
                out.append(" • R:").append(item.relation);
            }
            if (!item.topics.isEmpty()) out.append(" • ").append(trim(item.topics, 100));
        }

        new AlertDialog.Builder(this)
                .setTitle(facet.value + " • " + facet.dimension)
                .setMessage(out.length() == 0 ? "Nu există locații." : out.toString().trim())
                .setPositiveButton("ÎNCHIDE", null)
                .show();
    }

    private void showCoverageRanges() {
        List<IndexCoreCartographyReader.CoverageRange> ranges =
                IndexCoreCartographyReader.coverageRanges(db, sourceId);
        if (ranges.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Arii tematice")
                    .setMessage("Nu există încă intervale tematice provenite din CAPTURĂ EXACTĂ.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (IndexCoreCartographyReader.CoverageRange range : ranges) {
            if (shown++ >= 260) { out.append("\n…"); break; }
            out.append("\n• ").append(range.topicTitle.isEmpty() ? range.topicPath : range.topicTitle)
                    .append(" • pag. ").append(range.pageLabel())
                    .append(" • ").append(range.units).append(" unități");
        }
        new AlertDialog.Builder(this)
                .setTitle("Arii de acoperire tematice")
                .setMessage(out.toString().trim())
                .setPositiveButton("ÎNCHIDE", null)
                .show();
    }

    private void showDeterministicIntersections() {
        List<IndexCoreCartographyReader.IntersectionSummary> values =
                IndexCoreCartographyReader.intersections(db, sourceId, 250);
        List<IndexCoreCartographyReader.CoPresenceSummary> plus =
                IndexCoreCartographyReader.coPresence(db, sourceId, 120);
        if (values.isEmpty() && plus.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Relații între indici")
                    .setMessage("Nu există încă încrucișări sau co-prezențe persistate prin CAPTURĂ EXACTĂ.")
                    .setPositiveButton("OK", null).show();
            return;
        }

        StringBuilder out = new StringBuilder();
        if (!values.isEmpty()) {
            out.append("× RELAȚIE STRUCTURAL DEMONSTRATĂ");
            for (IndexCoreCartographyReader.IntersectionSummary value : values) {
                out.append("\n• ").append(value.leftTitle).append(" × ").append(value.rightTitle)
                        .append(" [").append(value.rule).append("]");
                if (!value.relation.isEmpty() && !"GENERIC".equals(value.relation)) {
                    out.append(" • ").append(value.relation);
                }
                out.append(" • ").append(value.occurrences).append(" unități");
                if (value.pageFrom > 0) {
                    out.append(" • pag. ").append(value.pageFrom);
                    if (value.pageTo > value.pageFrom) out.append("–").append(value.pageTo);
                }
            }
        }

        if (!plus.isEmpty()) {
            if (out.length() > 0) out.append("\n\n");
            out.append("+ CO-PREZENȚĂ FĂRĂ RELAȚIE DEMONSTRATĂ");
            for (IndexCoreCartographyReader.CoPresenceSummary value : plus) {
                out.append("\n• ").append(value.leftTitle).append(" + ").append(value.rightTitle)
                        .append(" • ").append(value.occurrences).append(" unități");
                if (value.pageFrom > 0) {
                    out.append(" • pag. ").append(value.pageFrom);
                    if (value.pageTo > value.pageFrom) out.append("–").append(value.pageTo);
                }
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("× ≠ +")
                .setMessage(out.toString().trim())
                .setPositiveButton("ÎNCHIDE", null)
                .show();
    }

    private void showMatrix() {
        List<IndexCoreCartographyReader.MatrixRow> matrix =
                IndexCoreCartographyReader.matrix(db, 180);
        if (matrix.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Matrice multisursă")
                    .setMessage("Nu există încă suficiente capturi exacte pentru matrice.")
                    .setPositiveButton("OK", null).show();
            return;
        }

        Map<String, String> sourceNames = new LinkedHashMap<>();
        for (IndexCoreDatabase.SourceRecord source : db.listSources()) {
            sourceNames.put(source.id, source.displayName());
        }

        StringBuilder out = new StringBuilder();
        for (IndexCoreCartographyReader.MatrixRow row : matrix) {
            out.append("\n\n").append(row.topicTitle.isEmpty() ? row.topicPath : row.topicTitle);
            for (Map.Entry<String, String> source : sourceNames.entrySet()) {
                int count = row.countsBySource.getOrDefault(source.getKey(), 0);
                out.append("\n  • ").append(source.getValue())
                        .append(": ").append(count).append(" zone-paragraf");
                if (count == 0) out.append("  ← GOL");
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Matrice temă × sursă")
                .setMessage(out.toString().trim())
                .setPositiveButton("ÎNCHIDE", null)
                .show();
    }

    private void showIntersection() {
        EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setHint("DOMAIN=HISTORY + RELATION=CAUSE + PRIMARY=PERSON - RELATION=EFFECT\nPAGE=120..190");
        new AlertDialog.Builder(this)
                .setTitle("Intersecție fațete")
                .setMessage("AND pe aceeași apariție. Prefix '-' = excludere. Filtrul indexează, nu generează afirmații.")
                .setView(input)
                .setPositiveButton("APLICĂ", (d, w) -> { query = FacetIntersectionQuery.parse(input.getText().toString()); rebuild(); })
                .setNeutralButton("GOLEȘTE", (d, w) -> { query = FacetIntersectionQuery.parse(""); rebuild(); })
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void showResult(IndexCoreDatabase.SearchResult row) {
        List<IndexCoreOccurrenceReader.Location> locations = IndexCoreOccurrenceReader.list(db, row.entryId, sourceId, 100);
        StringBuilder msg = new StringBuilder();
        msg.append("ID v7: ").append(row.entryId)
                .append("\nCategorie: ").append(row.category)
                .append("\nValidat: ").append(row.validated ? "DA" : "NU")
                .append("\nApariții care satisfac filtrul: ").append(row.occurrences)
                .append("\n\nLOCAȚII ÎN SURSA ACTIVĂ:");
        int shown = 0;
        for (IndexCoreOccurrenceReader.Location location : locations) {
            if (shown++ >= 80) { msg.append("\n…"); break; }
            msg.append("\n• ");
            if (!location.page.isEmpty()) msg.append("pag. ").append(location.page).append(" • ");
            msg.append("P").append(location.paragraphIndex + 1);
            if (!location.outlineTitle.isEmpty()) msg.append(" • ").append(location.outlineTitle);
            if (!location.contextCode.isEmpty()) msg.append(" • ").append(trim(location.contextCode, 100));
        }
        msg.append("\n\nNu se salvează fotografia paginii.");
        new AlertDialog.Builder(this)
                .setTitle(row.canonical)
                .setMessage(msg.toString())
                .setPositiveButton("ÎNCHIDE", null).show();
    }

    private void exportCartography() {
        pendingExportText = HistoricalCartographyExporter.buildSourceMap(db, sourceId);
        if (pendingExportText.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Export")
                    .setMessage("Nu există încă o fișă de cartografiere de exportat.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        IndexCoreDatabase.SourceRecord source = db.source(sourceId);
        String base = source == null ? "sursa" : source.displayName();
        base = base.replaceAll("[^\\p{L}\\p{N}._-]+", "_");
        if (base.isEmpty()) base = "sursa";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "cartografiere_" + base + ".txt");
        startActivityForResult(intent, REQ_EXPORT_CARTOGRAPHY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_EXPORT_CARTOGRAPHY || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null || pendingExportText.isEmpty()) return;
        try {
            OutputStream stream = getContentResolver().openOutputStream(uri, "wt");
            if (stream == null) throw new IllegalStateException("Nu se poate deschide destinația.");
            try (OutputStream out = stream) {
                out.write(pendingExportText.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            new AlertDialog.Builder(this).setTitle("Export")
                    .setMessage("Fișa deterministă de cartografiere a fost exportată.")
                    .setPositiveButton("OK", null).show();
        } catch (Exception failure) {
            new AlertDialog.Builder(this).setTitle("Export nereușit")
                    .setMessage(failure.getMessage() == null ? "Nu s-a putut scrie fișierul." : failure.getMessage())
                    .setPositiveButton("OK", null).show();
        }
    }

    private String compactPages(List<Integer> pages, int maxItems) {
        if (pages == null || pages.isEmpty()) return "—";
        StringBuilder out = new StringBuilder();
        int shown = 0;
        int i = 0;
        while (i < pages.size() && shown < maxItems) {
            int start = pages.get(i);
            int end = start;
            int j = i + 1;
            while (j < pages.size() && pages.get(j) == end + 1) {
                end = pages.get(j);
                j++;
            }
            if (out.length() > 0) out.append(", ");
            out.append(start);
            if (end > start) out.append("–").append(end);
            shown++;
            i = j;
        }
        if (i < pages.size()) out.append(", …");
        return out.toString();
    }

    private int parseInt(String value) {
        try { return Integer.parseInt(value == null ? "" : value.trim()); }
        catch (Exception ignored) { return 0; }
    }

    private EditText field(String hint, String value) {
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint(hint); input.setText(value == null ? "" : value); return input;
    }

    private String trim(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        return value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private TextView text(float size, int color) {
        TextView view = new TextView(this); view.setTextSize(size); view.setTextColor(color); view.setLineSpacing(0f, 1.12f); return view;
    }

    private Button button(String label) { Button b = new Button(this); b.setText(label); b.setTextSize(9.8f); return b; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}