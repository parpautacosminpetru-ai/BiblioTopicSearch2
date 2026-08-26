package ro.bibliotopicsearch.app;

import android.graphics.Color;
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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Persistent evidence workspace. The app organizes; the user writes the synthesis.
 * Source evidence remains immutable and user notes live in ResearchWorkspaceStore.
 */
public final class ResearchWorkspaceActivity extends AppCompatActivity {
    private ResearchWorkspaceEngine.Workspace workspace;
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
        workspace = ResearchWorkspaceEngine.build(this);
        buildUi();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(13, 20, 27));
        root.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(workspace.state().projectTitle());
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setOnClickListener(v -> editProjectTitle());
        top.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button synthesis = compactButton("SINTEZA TA");
        synthesis.setOnClickListener(v -> editSynthesis());
        top.addView(synthesis, new LinearLayout.LayoutParams(dp(108), dp(42)));

        Button sources = compactButton("SURSE");
        sources.setOnClickListener(v -> showSources());
        top.addView(sources, new LinearLayout.LayoutParams(dp(82), dp(42)));

        Button close = compactButton("ÎNAPOI");
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(dp(82), dp(42)));
        root.addView(top);

        TextView mode = bodyText();
        mode.setText("MOD: ORGANIZARE EVIDENȚĂ • aplicația nu scrie concluzii; sinteza îți aparține.");
        mode.setTextColor(Color.rgb(135, 207, 176));
        mode.setPadding(0, dp(2), 0, dp(7));
        root.addView(mode);

        TextView summary = bodyText();
        summary.setText(summaryText());
        summary.setPadding(0, 0, 0, dp(9));
        root.addView(summary);

        if (workspace.isEmpty()) {
            TextView empty = bodyText();
            empty.setText("Nu există încă sesiuni. Scanează cu OCR LIVE și oprește OCR pentru a finaliza prima sursă.");
            empty.setPadding(0, dp(25), 0, 0);
            root.addView(empty);
            setContentView(root);
            return;
        }

        List<String> rows = new ArrayList<>();
        for (ResearchWorkspaceEngine.DossierGroup group : workspace.groups()) rows.add(groupRow(group));

        ListView list = new ListView(this);
        list.setDividerHeight(dp(1));
        list.setBackgroundColor(Color.rgb(18, 27, 35));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, rows) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.rgb(235, 241, 245));
                view.setTextSize(12.3f);
                view.setPadding(dp(11), dp(9), dp(11), dp(9));
                view.setMinHeight(dp(78));
                return view;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < workspace.groups().size()) showGroup(workspace.groups().get(position));
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private String summaryText() {
        return "SURSE/SESIUNI: " + workspace.sourceCount()
                + "  •  DOSARE: " + workspace.groups().size()
                + "  •  DOVEZI: " + workspace.evidenceCount()
                + "\nLACUNE MATRICE: " + workspace.totalGaps()
                + "  •  TENSIUNI CANDIDATE: " + workspace.tensionCandidates()
                + "  •  PIN: " + workspace.pinnedCount();
    }

    private String groupRow(ResearchWorkspaceEngine.DossierGroup group) {
        StringBuilder out = new StringBuilder();
        out.append(group.head().isEmpty() ? "?" : group.head())
                .append("\nSURSE ").append(group.sourceCount())
                .append(" • DOVEZI ").append(group.evidence().size())
                .append(" • AXE ").append(group.axes().size());
        if (group.convergenceCount() > 0) out.append(" • CONV ").append(group.convergenceCount());
        if (group.tensionCandidateCount() > 0) out.append(" • TENS ").append(group.tensionCandidateCount());
        if (group.pinnedCount() > 0) out.append(" • PIN ").append(group.pinnedCount());
        out.append("\nACOPERIT: ").append(group.answeredSlots());
        if (!group.gaps().isEmpty()) out.append("  •  LIPSEȘTE: ").append(group.gaps());
        return out.toString();
    }

    private void showGroup(ResearchWorkspaceEngine.DossierGroup group) {
        List<ResearchWorkspaceEngine.EvidenceItem> evidence = group.evidence();
        String[] labels = new String[evidence.size()];
        for (int i = 0; i < evidence.size(); i++) labels[i] = evidenceLabel(evidence.get(i));

        String title = group.head() + " • " + evidence.size() + " dovezi";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("AXE: " + group.axes()
                        + "\nACOPERIT: " + group.answeredSlots()
                        + "\nLACUNE: " + group.gaps()
                        + "\nCONVERGENȚE: " + group.convergenceCount()
                        + "\nTENSIUNI CANDIDATE: " + group.tensionCandidateCount())
                .setItems(labels, (dialog, which) -> {
                    if (which >= 0 && which < evidence.size()) showEvidence(evidence.get(which));
                })
                .setNegativeButton("ÎNCHIDE", null)
                .show();
    }

    private String evidenceLabel(ResearchWorkspaceEngine.EvidenceItem item) {
        StringBuilder out = new StringBuilder();
        if (item.pinned()) out.append("★ ");
        out.append(item.relation().name())
                .append(" • ").append(item.sourceTitle())
                .append(" • P").append(item.paragraphIndex() + 1)
                .append("\n").append(ellipsize(item.raw(), 150));
        if (!item.userNote().trim().isEmpty()) out.append("\nNOTĂ: ").append(ellipsize(item.userNote(), 70));
        return out.toString();
    }

    private void showEvidence(ResearchWorkspaceEngine.EvidenceItem item) {
        StringBuilder details = new StringBuilder();
        details.append("SURSA: ").append(item.sourceTitle());
        if (!item.sourceAuthor().isEmpty()) details.append("\nAUTOR: ").append(item.sourceAuthor());
        if (!item.locator().isEmpty()) details.append("\nLOCALIZARE: ").append(item.locator());
        details.append("\nPARAGRAF: P").append(item.paragraphIndex() + 1)
                .append("\nRELAȚIE: ").append(item.relation().name())
                .append("\nÎNCREDERE: ").append((int) Math.round(item.confidence() * 100.0)).append("%")
                .append("\n\nEVIDENȚĂ ORIGINALĂ\n").append(item.raw());
        if (!item.userNote().trim().isEmpty()) details.append("\n\nNOTA TA\n").append(item.userNote());

        TextView content = bodyText();
        content.setText(details.toString());
        content.setPadding(dp(16), dp(8), dp(16), dp(8));
        new AlertDialog.Builder(this)
                .setTitle(item.pinned() ? "★ Evidență" : "Evidență")
                .setView(content)
                .setPositiveButton("NOTĂ", (dialog, which) -> editEvidenceNote(item))
                .setNeutralButton(item.pinned() ? "SCOATE PIN" : "PIN", (dialog, which) -> {
                    ResearchWorkspaceStore.setPinned(this, item.id(), !item.pinned());
                    rebuild();
                })
                .setNegativeButton("ÎNCHIDE", null)
                .show();
    }

    private void editEvidenceNote(ResearchWorkspaceEngine.EvidenceItem item) {
        EditText input = multilineInput(item.userNote());
        new AlertDialog.Builder(this)
                .setTitle("Nota ta • evidența rămâne neschimbată")
                .setView(input)
                .setPositiveButton("SALVEAZĂ", (dialog, which) -> {
                    ResearchWorkspaceStore.setEvidenceNote(this, item.id(), input.getText().toString());
                    rebuild();
                })
                .setNeutralButton("ȘTERGE NOTA", (dialog, which) -> {
                    ResearchWorkspaceStore.setEvidenceNote(this, item.id(), "");
                    rebuild();
                })
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void editSynthesis() {
        EditText input = multilineInput(workspace.state().synthesisDraft());
        input.setHint("Scrie aici sinteza ta. Aplicația nu completează automat acest câmp.");
        new AlertDialog.Builder(this)
                .setTitle("SINTEZA TA")
                .setMessage("Text scris exclusiv de tine; dovezile sursă rămân separate și nemodificate.")
                .setView(input)
                .setPositiveButton("SALVEAZĂ", (dialog, which) -> {
                    ResearchWorkspaceStore.setSynthesisDraft(this, input.getText().toString());
                    rebuild();
                })
                .setNegativeButton("ÎNCHIDE", null)
                .show();
    }

    private void editProjectTitle() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(workspace.state().projectTitle());
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("Titlu proiect")
                .setView(input)
                .setPositiveButton("SALVEAZĂ", (dialog, which) -> {
                    ResearchWorkspaceStore.setProjectTitle(this, input.getText().toString());
                    rebuild();
                })
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void showSources() {
        List<OnePassSemanticOrganizer.Snapshot> sessions = workspace.sessions();
        String[] labels = new String[sessions.size()];
        for (int i = 0; i < sessions.size(); i++) {
            OnePassSemanticOrganizer.Snapshot snapshot = sessions.get(i);
            ResearchWorkspaceStore.SourceMeta meta = workspace.state().source(snapshot.startedAt());
            String name = meta.title().isEmpty() ? snapshot.globalSubject() : meta.title();
            if (name == null || name.trim().isEmpty()) name = "Sursa";
            String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(snapshot.finishedAt()));
            labels[i] = name + "\n" + date + " • " + snapshot.uniqueParagraphs() + " paragrafe";
        }
        new AlertDialog.Builder(this)
                .setTitle("Surse / sesiuni")
                .setItems(labels, (dialog, which) -> {
                    if (which >= 0 && which < sessions.size()) editSource(sessions.get(which));
                })
                .setNegativeButton("ÎNCHIDE", null)
                .show();
    }

    private void editSource(OnePassSemanticOrganizer.Snapshot snapshot) {
        ResearchWorkspaceStore.SourceMeta meta = workspace.state().source(snapshot.startedAt());
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(5), dp(18), 0);

        EditText title = singleInput("Titlu carte / sursă", meta.title());
        EditText author = singleInput("Autor", meta.author());
        EditText locator = singleInput("Localizare / ediție / pagini", meta.locator());
        box.addView(title); box.addView(author); box.addView(locator);

        new AlertDialog.Builder(this)
                .setTitle("Metadate sursă")
                .setMessage("Aceste date se atașează provenienței; textul OCR nu este modificat.")
                .setView(box)
                .setPositiveButton("SALVEAZĂ", (dialog, which) -> {
                    ResearchWorkspaceStore.setSource(this, snapshot.startedAt(),
                            title.getText().toString(), author.getText().toString(), locator.getText().toString());
                    rebuild();
                })
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private EditText multilineInput(String value) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(8);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setText(value == null ? "" : value);
        input.setSelection(input.getText().length());
        return input;
    }

    private EditText singleInput(String hint, String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        return input;
    }

    private Button compactButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(10);
        return button;
    }

    private TextView bodyText() {
        TextView view = new TextView(this);
        view.setTextColor(Color.rgb(220, 230, 237));
        view.setTextSize(12.3f);
        view.setLineSpacing(0f, 1.12f);
        return view;
    }

    private String ellipsize(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, Math.max(1, max - 1)).trim() + "…";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
