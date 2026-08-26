package ro.bibliotopicsearch.app;

import android.graphics.Color;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** User validation and multi-criteria organization surface for the deterministic index. */
public final class LivingIndexActivity extends AppCompatActivity {
    private LivingIndexStore.State state;
    private LivingIndexOrganizer.Index organizer;
    private List<LivingIndexStore.Entry> rows = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LivingIndexRuntime.reload(this);
        rebuild();
    }

    private void rebuild() {
        state = LivingIndexRuntime.state();
        organizer = LivingIndexOrganizer.build(state);
        rows = new ArrayList<>();
        rows.addAll(state.inbox());
        for (LivingIndexStore.Entry entry : state.validated()) if (!rows.contains(entry)) rows.add(entry);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.setBackgroundColor(Color.rgb(14, 22, 29));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("INDEX VIU • AUTO ORGANIZAT");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17.5f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button groups = button("GRUPURI");
        groups.setOnClickListener(v -> showDimensions());
        top.addView(groups, new LinearLayout.LayoutParams(dp(88), dp(42)));

        Button mode = button(AppPrefs.indexMode(this) == AppPrefs.IndexMode.SOURCE ? "SURSA" : "CERCETARE");
        mode.setOnClickListener(v -> chooseMode());
        top.addView(mode, new LinearLayout.LayoutParams(dp(90), dp(42)));

        Button source = button("META");
        source.setOnClickListener(v -> editSource());
        top.addView(source, new LinearLayout.LayoutParams(dp(68), dp(42)));

        Button close = button("ÎNAPOI");
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(dp(78), dp(42)));
        root.addView(top);

        TextView summary = text();
        summary.setText(
                "MOD: " + (AppPrefs.indexMode(this) == AppPrefs.IndexMode.SOURCE
                        ? "SURSA/EXAMEN • colectare automată din OCR"
                        : "CERCETARE • tema/întrebarea filtrează indexul")
                        + "\nINBOX: " + state.inbox().size()
                        + "  •  VALIDATE: " + state.validated().size()
                        + "  •  CRITERII ACTIVE: " + organizer.activeDimensions()
                        + "  •  MULTI-CRITERIU: " + organizer.multiCriteriaEntries()
                        + "\nPAGINA LIVE: " + valueOr(LivingIndexRuntime.currentPage(), "—")
                        + "  •  aceeași intrare poate fi simultan tip+domeniu+rol+timp+loc+relație+nivel."
        );
        summary.setPadding(0, dp(4), 0, dp(8));
        root.addView(summary);

        List<String> display = new ArrayList<>();
        for (LivingIndexStore.Entry entry : rows) display.add(rowText(entry));

        ListView list = new ListView(this);
        list.setDividerHeight(dp(1));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, display) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                LivingIndexStore.Entry entry = rows.get(position);
                view.setTextColor(Color.WHITE);
                view.setTextSize(12.7f);
                view.setPadding(dp(12), dp(9), dp(12), dp(9));
                view.setBackgroundColor(entry.validated()
                        ? Color.rgb(24, 39, 48)
                        : Color.rgb(55, 45, 27));
                return view;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> showEntry(rows.get(position)));
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void showDimensions() {
        List<LivingIndexOrganizer.Dimension> dimensions = new ArrayList<>();
        for (LivingIndexOrganizer.Dimension dimension : LivingIndexOrganizer.Dimension.values()) {
            if (!organizer.groups(dimension).isEmpty()) dimensions.add(dimension);
        }
        if (dimensions.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Grupuri").setMessage("Indexul nu are încă suficiente criterii.").setPositiveButton("OK", null).show();
            return;
        }
        String[] labels = new String[dimensions.size()];
        for (int i = 0; i < dimensions.size(); i++) {
            LivingIndexOrganizer.Dimension dimension = dimensions.get(i);
            labels[i] = dimension.name() + " • " + organizer.groups(dimension).size() + " grupuri";
        }
        new AlertDialog.Builder(this)
                .setTitle("Organizare pe criteriu")
                .setItems(labels, (dialog, which) -> showGroups(dimensions.get(which)))
                .setNegativeButton("ÎNCHIDE", null)
                .show();
    }

    private void showGroups(LivingIndexOrganizer.Dimension dimension) {
        Map<String, List<LivingIndexStore.Entry>> groups = organizer.groups(dimension);
        List<String> names = new ArrayList<>(groups.keySet());
        names.sort((a, b) -> Integer.compare(groups.get(b).size(), groups.get(a).size()));
        String[] labels = new String[names.size()];
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            labels[i] = name + "  •  " + groups.get(name).size();
        }
        new AlertDialog.Builder(this)
                .setTitle(dimension.name())
                .setItems(labels, (dialog, which) -> showGroupEntries(dimension, names.get(which), groups.get(names.get(which))))
                .setNegativeButton("ÎNAPOI", null)
                .show();
    }

    private void showGroupEntries(
            LivingIndexOrganizer.Dimension dimension,
            String group,
            List<LivingIndexStore.Entry> entries
    ) {
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (LivingIndexStore.Entry entry : entries) {
            if (shown++ >= 60) { out.append("\n…"); break; }
            out.append(entry.code()).append(" • ").append(entry.canonical()).append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle(dimension.name() + " = " + group)
                .setMessage(out.toString().trim())
                .setPositiveButton("ÎNCHIDE", null)
                .show();
    }

    private void chooseMode() {
        String[] choices = {
                "SURSA / EXAMEN — indexează intern; fără persistența textului paginii",
                "CERCETARE — tema/întrebarea externă + dosar de dovezi"
        };
        new AlertDialog.Builder(this)
                .setTitle("Modul aceleiași bare")
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        AppPrefs.setIndexMode(this, AppPrefs.IndexMode.SOURCE);
                        TopicMatcher.setResearchQuery("");
                    } else {
                        AppPrefs.setIndexMode(this, AppPrefs.IndexMode.RESEARCH);
                        TopicMatcher.setResearchQuery(AppPrefs.storedResearchQuery(this));
                    }
                    rebuild();
                })
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private String rowText(LivingIndexStore.Entry entry) {
        String status = entry.validated() ? entry.category().name() : "INBOX";
        List<String> criteria = LivingIndexOrganizer.criteriaForEntry(entry);
        return entry.code() + "  •  " + status + "  •  x" + entry.recurrence()
                + "\n" + entry.canonical()
                + "\nref: " + entry.refs().size() + "  •  criterii: " + criteria.size()
                + "  •  " + compactCriteria(criteria, 3);
    }

    private void showEntry(LivingIndexStore.Entry entry) {
        StringBuilder details = new StringBuilder();
        details.append("COD: ").append(entry.code())
                .append("\nCATEGORIE PRIMARĂ: ").append(entry.category())
                .append("\nVALIDAT: ").append(entry.validated() ? "DA" : "NU")
                .append("\nRECURENȚĂ: ").append(entry.recurrence())
                .append("\nALIASE: ").append(entry.aliases());

        List<String> criteria = LivingIndexOrganizer.criteriaForEntry(entry);
        if (!criteria.isEmpty()) {
            details.append("\n\nCRITERII SUPRAPUSE:");
            int shown = 0;
            for (String criterion : criteria) {
                if (shown++ >= 28) { details.append("\n…"); break; }
                details.append("\n• ").append(criterion);
            }
        }

        if (!entry.refs().isEmpty()) {
            details.append("\n\nREFERINȚE (fără imagine):");
            int start = Math.max(0, entry.refs().size() - 16);
            for (int i = start; i < entry.refs().size(); i++) {
                LivingIndexStore.Ref ref = entry.refs().get(i);
                details.append("\n• sursă ").append(ref.sourceId())
                        .append(" • P").append(ref.paragraphIndex() + 1);
                if (!ref.page().isEmpty()) details.append(" • pag. ").append(ref.page());
                if (!ref.contextCode().isEmpty()) details.append(" • ").append(ref.contextCode());
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(entry.canonical())
                .setMessage(details.toString())
                .setNeutralButton(entry.validated() ? "RECLASIFICĂ" : "VALIDEAZĂ", (d, w) -> chooseCategory(entry))
                .setPositiveButton("ÎNCHIDE", null)
                .show();
    }

    private void chooseCategory(LivingIndexStore.Entry entry) {
        List<LivingIndexStore.Category> categories = new ArrayList<>();
        for (LivingIndexStore.Category category : LivingIndexStore.Category.values()) {
            if (category != LivingIndexStore.Category.INBOX) categories.add(category);
        }
        String[] labels = new String[categories.size()];
        for (int i = 0; i < categories.size(); i++) labels[i] = categories.get(i).name();

        new AlertDialog.Builder(this)
                .setTitle("Categoria pentru „" + entry.canonical() + "”")
                .setItems(labels, (dialog, which) -> {
                    LivingIndexRuntime.validate(entry.id(), categories.get(which));
                    rebuild();
                })
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void editSource() {
        long id = LivingIndexRuntime.sourceId();
        if (id <= 0) id = System.currentTimeMillis();
        final long sourceId = id;
        ResearchWorkspaceStore.SourceMeta old = ResearchWorkspaceStore.load(this).source(sourceId);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), 0);
        EditText title = new EditText(this);
        title.setHint("Titlul sursei/cărții");
        title.setText(old.title());
        box.addView(title);
        EditText author = new EditText(this);
        author.setHint("Autor");
        author.setText(old.author());
        box.addView(author);
        EditText locator = new EditText(this);
        locator.setHint("Ediție / volum / interval pagini / bibliotecă");
        locator.setText(old.locator());
        box.addView(locator);

        new AlertDialog.Builder(this)
                .setTitle("Sursa indexului")
                .setMessage("Metadatele sunt separate de text; nu se salvează fotografia paginii.")
                .setView(box)
                .setPositiveButton("SALVEAZĂ", (d, w) -> ResearchWorkspaceStore.setSource(
                        this,
                        sourceId,
                        title.getText().toString(),
                        author.getText().toString(),
                        locator.getText().toString()
                ))
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private String compactCriteria(List<String> values, int max) {
        if (values == null || values.isEmpty()) return "—";
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (String value : values) {
            if (value.startsWith("PRIMARY=")) continue;
            if (count++ >= max) { out.append(" +…"); break; }
            if (out.length() > 0) out.append(" | ");
            out.append(value);
        }
        return out.length() == 0 ? "—" : out.toString();
    }

    private TextView text() {
        TextView view = new TextView(this);
        view.setTextColor(Color.rgb(210, 222, 230));
        view.setTextSize(12.3f);
        view.setLineSpacing(0f, 1.12f);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(10.2f);
        return button;
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
