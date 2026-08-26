package ro.bibliotopicsearch.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Displays the latest finalized one-pass semantic organization. */
public final class OrganizedSessionActivity extends AppCompatActivity {
    private OnePassSemanticOrganizer.Snapshot snapshot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        snapshot = OrganizedSessionStore.loadLatest(this);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(15, 22, 29));
        root.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Organizare One-Pass");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button close = new Button(this);
        close.setText("ÎNAPOI");
        close.setTextSize(11);
        close.setOnClickListener(v -> finish());
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(96), dp(42)));
        root.addView(titleRow);

        if (snapshot == null || snapshot.paragraphs().isEmpty()) {
            TextView empty = bodyText();
            empty.setText("Nu există încă o sesiune finalizată. Pornește OCR LIVE, parcurge textul o singură dată, apoi pune OCR în pauză.");
            empty.setPadding(0, dp(30), 0, 0);
            root.addView(empty);
            setContentView(root);
            return;
        }

        TextView summary = bodyText();
        summary.setText(summaryText(snapshot));
        summary.setPadding(0, dp(5), 0, dp(10));
        root.addView(summary);

        List<String> rows = new ArrayList<>();
        for (OnePassSemanticOrganizer.Paragraph paragraph : snapshot.paragraphs()) {
            rows.add(rowText(paragraph));
        }

        ListView list = new ListView(this);
        list.setDividerHeight(dp(1));
        list.setBackgroundColor(Color.rgb(20, 29, 38));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                rows
        ) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.rgb(235, 241, 245));
                view.setTextSize(12.5f);
                view.setPadding(dp(12), dp(10), dp(12), dp(10));
                view.setMinHeight(dp(78));
                return view;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < snapshot.paragraphs().size()) {
                showParagraph(snapshot.paragraphs().get(position));
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        setContentView(root);
    }

    private String summaryText(OnePassSemanticOrganizer.Snapshot value) {
        String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(value.finishedAt()));
        StringBuilder out = new StringBuilder();
        out.append("FINALIZAT: ").append(date)
                .append("\nPARAGRAFE UNICE: ").append(value.uniqueParagraphs())
                .append("  •  CLAIMS: ").append(value.claimCount())
                .append("  •  MAX L").append(value.maxDepth())
                .append("\nDUPLICATE OCR ABSORBITE: ").append(value.duplicatesMerged())
                .append("  •  CADRE: ").append(value.framesObserved());
        if (!value.globalSubject().isEmpty()) {
            out.append("\nSUBIECT GLOBAL: ").append(value.globalSubject());
        }
        if (!value.query().isEmpty()) {
            out.append("\nCERCETARE: ").append(value.query());
        }
        if (!value.bestAnswerSegment().isEmpty()) {
            out.append("\nR MAX ")
                    .append((int) Math.round(value.bestAnswerScore() * 100.0))
                    .append("%: ")
                    .append(value.bestAnswerSegment());
        }
        return out.toString();
    }

    private String rowText(OnePassSemanticOrganizer.Paragraph paragraph) {
        StringBuilder out = new StringBuilder();
        out.append("P").append(paragraph.index() + 1)
                .append("  •  L").append(paragraph.depth())
                .append("  •  ").append(paragraph.link().name())
                .append("\nS: ").append(paragraph.subject().isEmpty() ? "?" : paragraph.subject())
                .append("  •  F: ").append(functionLabel(paragraph.function()));
        if (!paragraph.answerSegment().isEmpty()) {
            out.append("  •  R ")
                    .append((int) Math.round(paragraph.answerScore() * 100.0))
                    .append("%");
        }
        out.append("\n").append(ellipsize(paragraph.text(), 180));
        return out.toString();
    }

    private void showParagraph(OnePassSemanticOrganizer.Paragraph paragraph) {
        StringBuilder details = new StringBuilder();
        details.append("NIVEL: L").append(paragraph.depth())
                .append("\nRELAȚIE CARTOGRAFICĂ: ").append(paragraph.link().name())
                .append("\nPĂRINTE: ")
                .append(paragraph.parentIndex() < 0 ? "—" : "P" + (paragraph.parentIndex() + 1))
                .append("\nSUBIECT: ").append(paragraph.subject())
                .append("\nFUNCȚIE: ").append(functionLabel(paragraph.function()))
                .append("\nÎNCREDERE S/F: ")
                .append((int) Math.round(paragraph.subjectConfidence() * 100.0))
                .append("% / ")
                .append((int) Math.round(paragraph.functionConfidence() * 100.0))
                .append("%")
                .append("\nSTABILITATE OCR: ").append(paragraph.sightings()).append(" apariții")
                .append("\n\nTEXT\n").append(paragraph.text());

        if (!paragraph.answerSegment().isEmpty()) {
            details.append("\n\nRĂSPUNS EXPLICIT • ")
                    .append((int) Math.round(paragraph.answerScore() * 100.0))
                    .append("% • ").append(paragraph.answerIntent().name())
                    .append("\n").append(paragraph.answerSegment());
        }

        if (!paragraph.claims().isEmpty()) {
            details.append("\n\nCLAIMS / PROPOZIȚII");
            int number = 1;
            for (OnePassSemanticOrganizer.Claim claim : paragraph.claims()) {
                details.append("\n\n").append(number++).append(") ")
                        .append(claim.relation().name())
                        .append(" • ")
                        .append((int) Math.round(claim.confidence() * 100.0)).append("%")
                        .append("\n").append(claim.raw());
                if (!claim.operators().isEmpty()) {
                    details.append("\nOP: ").append(claim.operators());
                }
                if (!claim.slots().isEmpty()) {
                    details.append("\nSLOTURI:");
                    for (Map.Entry<SemanticGraph.Slot, String> slot : claim.slots().entrySet()) {
                        details.append(" ").append(slot.getKey().name())
                                .append("=").append(slot.getValue()).append(";");
                    }
                }
            }
        }

        TextView content = bodyText();
        content.setText(details.toString());
        content.setPadding(dp(18), dp(12), dp(18), dp(12));
        new AlertDialog.Builder(this)
                .setTitle("P" + (paragraph.index() + 1) + " • L" + paragraph.depth())
                .setView(content)
                .setPositiveButton("ÎNCHIDE", null)
                .show();
    }

    private TextView bodyText() {
        TextView view = new TextView(this);
        view.setTextColor(Color.rgb(222, 231, 237));
        view.setTextSize(12.5f);
        view.setLineSpacing(0f, 1.12f);
        return view;
    }

    private String functionLabel(UniversalDetectionLexicon.Function function) {
        if (function == null) return "NEDETERMINATĂ";
        return function.name().replace('_', ' ');
    }

    private String ellipsize(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("\\s+", " ").trim();
        if (clean.length() <= max) return clean;
        return clean.substring(0, Math.max(1, max - 1)).trim() + "…";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
