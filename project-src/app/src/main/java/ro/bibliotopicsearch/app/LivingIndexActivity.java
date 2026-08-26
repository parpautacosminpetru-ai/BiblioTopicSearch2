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

/** User validation surface for the deterministic, ever-growing index. */
public final class LivingIndexActivity extends AppCompatActivity {
    private LivingIndexStore.State state;
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
        title.setText("INDEX VIU • DETERMINIST");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button source = button("SURSĂ");
        source.setOnClickListener(v -> editSource());
        top.addView(source, new LinearLayout.LayoutParams(dp(82), dp(42)));

        Button close = button("ÎNAPOI");
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(dp(88), dp(42)));
        root.addView(top);

        TextView summary = text();
        summary.setText(
                "INBOX: " + state.inbox().size()
                        + "  •  VALIDATE: " + state.validated().size()
                        + "  •  PAGINA LIVE: " + valueOr(LivingIndexRuntime.currentPage(), "—")
                        + "\nNecunoscutele intră în INBOX. După ce le validezi o dată, codul/categoria devin detector permanent."
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
                view.setTextSize(13f);
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

    private String rowText(LivingIndexStore.Entry entry) {
        String status = entry.validated() ? entry.category().name() : "INBOX";
        return entry.code() + "  •  " + status + "  •  x" + entry.recurrence()
                + "\n" + entry.canonical()
                + "\nreferințe: " + entry.refs().size();
    }

    private void showEntry(LivingIndexStore.Entry entry) {
        StringBuilder details = new StringBuilder();
        details.append("COD: ").append(entry.code())
                .append("\nCATEGORIE: ").append(entry.category())
                .append("\nVALIDAT: ").append(entry.validated() ? "DA" : "NU")
                .append("\nRECURENȚĂ: ").append(entry.recurrence())
                .append("\nALIASE: ").append(entry.aliases());
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

    private TextView text() {
        TextView view = new TextView(this);
        view.setTextColor(Color.rgb(210, 222, 230));
        view.setTextSize(12.5f);
        view.setLineSpacing(0f, 1.12f);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(10.5f);
        return button;
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
