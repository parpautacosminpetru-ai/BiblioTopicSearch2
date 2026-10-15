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
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Human-controlled historian workbench.
 *
 * Claims are written by the historian. The app only links them to exact,
 * reversible source units and preserves source criticism + decision history.
 */
public final class HistorianWorkbenchActivity extends AppCompatActivity {
    private static final int REQ_EXPORT_DOSSIER = 501;

    private IndexCoreDatabase db;
    private List<HistorianWorkbenchStore.Claim> claims = new ArrayList<>();
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
        claims = new ArrayList<>(HistorianWorkbenchStore.claims(db));
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.setBackgroundColor(Color.rgb(12, 20, 27));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text(18.5f, Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setText("ATELIER ISTORIC");
        top.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button criticism = button("CRITICA SURSEI");
        criticism.setOnClickListener(v -> chooseSourceForCriticism());
        top.addView(criticism, new LinearLayout.LayoutParams(dp(112), dp(42)));

        Button add = button("+ AFIRMAȚIE");
        add.setOnClickListener(v -> editClaim(null));
        top.addView(add, new LinearLayout.LayoutParams(dp(104), dp(42)));

        Button close = button("ÎNAPOI");
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(dp(76), dp(42)));
        root.addView(top);

        LinearLayout second = new LinearLayout(this);
        second.setGravity(Gravity.CENTER_VERTICAL);

        Button audit = button("JURNAL DECIZII");
        audit.setOnClickListener(v -> showAuditTrail());
        second.addView(audit, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button activeSource = button("SURSA ACTIVĂ");
        activeSource.setOnClickListener(v -> editSourceCriticism(IndexCoreSourceRegistry.activeSourceId(this)));
        second.addView(activeSource, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button relations = button("RELAȚII SURSE");
        relations.setOnClickListener(v -> showSourceRelations());
        second.addView(relations, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button export = button("EXPORT DOSAR");
        export.setOnClickListener(v -> exportDossier());
        second.addView(export, new LinearLayout.LayoutParams(0, dp(42), 1f));
        root.addView(second);

        IndexCoreDatabase.Stats coreStats = db.stats();
        int evidenceLinks = HistorianWorkbenchStore.evidenceLinkCount(db);

        TextView principle = text(11.7f, Color.rgb(135, 207, 176));
        principle.setText("AFIRMAȚIILE SUNT ALE ISTORICULUI • aplicația leagă numai dovezi cu proveniență exactă.");
        principle.setPadding(0, dp(5), 0, dp(6));
        root.addView(principle);

        TextView summary = text(12.2f, Color.rgb(207, 221, 230));
        summary.setText(
                "AFIRMAȚII: " + claims.size()
                        + " • LEGĂTURI DOVADĂ: " + evidenceLinks
                        + " • SURSE: " + coreStats.sources
                        + "\nMetodă: afirmație umană → dovezi → convergență / contradicție / nuanță → revenire la sursă."
        );
        summary.setPadding(0, 0, 0, dp(8));
        root.addView(summary);

        if (claims.isEmpty()) {
            TextView empty = text(13f, Color.rgb(219, 228, 234));
            empty.setText(
                    "Nu există încă afirmații de cercetare. Creează una numai când ai o problemă sau o ipoteză "
                            + "pe care vrei să o urmărești. Aplicația nu o formulează în locul tău."
            );
            empty.setPadding(0, dp(24), 0, 0);
            root.addView(empty);
            setContentView(root);
            return;
        }

        List<String> rows = new ArrayList<>();
        for (HistorianWorkbenchStore.Claim claim : claims) rows.add(claimLabel(claim));

        ListView list = new ListView(this);
        list.setBackgroundColor(Color.rgb(18, 27, 35));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                rows
        ) {
            @Override
            public android.view.View getView(
                    int position,
                    android.view.View convertView,
                    ViewGroup parent
            ) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.rgb(235, 241, 245));
                view.setTextSize(12.5f);
                view.setPadding(dp(11), dp(9), dp(11), dp(9));
                view.setMinHeight(dp(76));
                return view;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < claims.size()) showClaim(claims.get(position));
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        setContentView(root);
    }

    private String claimLabel(HistorianWorkbenchStore.Claim claim) {
        StringBuilder out = new StringBuilder();
        out.append('[').append(statusRo(claim.status)).append("] ")
                .append(claim.displayTitle())
                .append("\nDOVEZI ").append(claim.evidenceCount)
                .append(" • SURSE ").append(claim.sourceCount)
                .append(" • +").append(claim.supports)
                .append(" / −").append(claim.contradicts)
                .append(" / ≈").append(claim.nuances);
        if (claim.exemplifies > 0) out.append(" • EX ").append(claim.exemplifies);
        if (claim.contextualizes > 0) out.append(" • CTX ").append(claim.contextualizes);
        return out.toString();
    }

    private void showClaim(HistorianWorkbenchStore.Claim claim) {
        if (claim == null) return;
        String[] actions = {
                "VEZI DOVEZILE (" + claim.evidenceCount + ")",
                "＋ ATAȘEAZĂ DOVADĂ DIN CARTOGRAFIE",
                "SCHIMBĂ STAREA",
                "EDITEAZĂ AFIRMAȚIA",
                "ȘTERGE AFIRMAȚIA"
        };

        new AlertDialog.Builder(this)
                .setTitle(claim.displayTitle())
                .setMessage(
                        claim.statement
                                + "\n\nSTARE: " + statusRo(claim.status)
                                + "\nSURSE: " + claim.sourceCount
                                + "\nSUPORT: " + claim.supports
                                + " • CONTRA: " + claim.contradicts
                                + " • NUANȚE: " + claim.nuances
                                + "\nEXEMPLE: " + claim.exemplifies
                                + " • CONTEXT: " + claim.contextualizes
                                + "\n\nStarea este aleasă de cercetător; numărătoarea nu decide adevărul afirmației."
                )
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) showEvidence(claim);
                    else if (which == 1) chooseEvidenceSource(claim);
                    else if (which == 2) chooseClaimStatus(claim);
                    else if (which == 3) editClaim(claim);
                    else if (which == 4) confirmDeleteClaim(claim);
                })
                .setNegativeButton("ÎNCHIDE", null)
                .show();
    }

    private void editClaim(HistorianWorkbenchStore.Claim existing) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(6), dp(18), 0);

        EditText title = singleInput(
                "Titlu scurt / problemă",
                existing == null ? "" : existing.title
        );
        EditText statement = multilineInput(
                existing == null ? "" : existing.statement,
                "Formulează tu afirmația / ipoteza istorică."
        );
        box.addView(title);
        box.addView(statement);

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Afirmație nouă" : "Editează afirmația")
                .setMessage("Aplicația stochează formularea ta; nu o completează și nu o rescrie.")
                .setView(box)
                .setPositiveButton("SALVEAZĂ", (dialog, which) -> {
                    String s = statement.getText().toString().trim();
                    if (s.isEmpty()) return;
                    if (existing == null) {
                        HistorianWorkbenchStore.createClaim(
                                db, title.getText().toString(), s
                        );
                    } else {
                        HistorianWorkbenchStore.updateClaim(
                                db, existing.id, title.getText().toString(), s
                        );
                    }
                    rebuild();
                })
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void chooseClaimStatus(HistorianWorkbenchStore.Claim claim) {
        HistorianWorkbenchStore.ClaimStatus[] values =
                HistorianWorkbenchStore.ClaimStatus.values();
        String[] labels = new String[values.length];
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            labels[i] = statusRo(values[i]);
            if (values[i] == claim.status) checked = i;
        }
        final int initial = checked;
        final int[] selected = {initial};

        new AlertDialog.Builder(this)
                .setTitle("Stare metodologică")
                .setMessage("Aceasta este evaluarea ta, nu o concluzie automată.")
                .setSingleChoiceItems(labels, checked, (d, which) -> selected[0] = which)
                .setPositiveButton("SALVEAZĂ", (d, w) -> {
                    HistorianWorkbenchStore.setClaimStatus(
                            db, claim.id, values[selected[0]]
                    );
                    rebuild();
                })
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void confirmDeleteClaim(HistorianWorkbenchStore.Claim claim) {
        new AlertDialog.Builder(this)
                .setTitle("Ștergi afirmația?")
                .setMessage(
                        "Afirmația și legăturile ei de evidență vor fi șterse. "
                                + "Cartografierea surselor rămâne neschimbată."
                )
                .setPositiveButton("ȘTERGE", (d, w) -> {
                    HistorianWorkbenchStore.deleteClaim(db, claim.id);
                    rebuild();
                })
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void chooseEvidenceSource(HistorianWorkbenchStore.Claim claim) {
        List<IndexCoreDatabase.SourceRecord> sources =
                new ArrayList<>(db.listSources());
        if (sources.isEmpty()) {
            message("Dovezi", "Nu există încă surse cartografiate.");
            return;
        }

        String[] labels = new String[sources.size()];
        for (int i = 0; i < sources.size(); i++) {
            IndexCoreDatabase.SourceRecord source = sources.get(i);
            labels[i] = source.displayName()
                    + (source.author.isEmpty() ? "" : "\n" + source.author);
        }

        new AlertDialog.Builder(this)
                .setTitle("1/3 — Alege sursa")
                .setItems(labels, (d, which) -> chooseEvidenceTopic(
                        claim, sources.get(which)
                ))
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void chooseEvidenceTopic(
            HistorianWorkbenchStore.Claim claim,
            IndexCoreDatabase.SourceRecord source
    ) {
        List<IndexCoreCartographyReader.TopicSummary> topics =
                IndexCoreCartographyReader.topicsForSource(db, source.id, 500);
        if (topics.isEmpty()) {
            message(
                    "Dovezi",
                    "Sursa nu are încă indici proveniți din CAPTURĂ EXACTĂ."
            );
            return;
        }

        String[] labels = new String[topics.size()];
        for (int i = 0; i < topics.size(); i++) {
            IndexCoreCartographyReader.TopicSummary topic = topics.get(i);
            labels[i] = (topic.topicTitle.isEmpty() ? topic.topicPath : topic.topicTitle)
                    + " • " + topic.units + " zone • " + topic.pages + " pagini";
        }

        new AlertDialog.Builder(this)
                .setTitle("2/3 — Alege indicele")
                .setMessage(source.displayName())
                .setItems(labels, (d, which) -> chooseEvidenceUnit(
                        claim, source, topics.get(which)
                ))
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void chooseEvidenceUnit(
            HistorianWorkbenchStore.Claim claim,
            IndexCoreDatabase.SourceRecord source,
            IndexCoreCartographyReader.TopicSummary topic
    ) {
        List<IndexCoreCartographyReader.ExtractionItem> items =
                IndexCoreCartographyReader.extractionQueue(
                        db, source.id, topic.topicPath, 1200
                );
        if (items.isEmpty()) {
            message("Dovezi", "Nu există unități pentru acest indice.");
            return;
        }

        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            IndexCoreCartographyReader.ExtractionItem item = items.get(i);
            StringBuilder row = new StringBuilder();
            if (!item.page.isEmpty()) row.append("pag. ").append(item.page).append(" • ");
            row.append("P").append(item.paragraphIndex + 1);
            if (item.sentenceIndex >= 0) row.append(" S").append(item.sentenceIndex + 1);
            if (item.clauseIndex >= 0) row.append(" C").append(item.clauseIndex + 1);
            if (!item.outlineTitle.isEmpty()) row.append(" • ").append(item.outlineTitle);
            if (!item.surface.isEmpty()) row.append("\n„").append(trim(item.surface, 120)).append("”");
            if (!item.crossings.isEmpty()) row.append("\n× ").append(trim(item.crossings, 90));
            labels[i] = row.toString();
        }

        new AlertDialog.Builder(this)
                .setTitle("3/3 — Alege unitatea")
                .setMessage(
                        source.displayName() + "\n"
                                + (topic.topicTitle.isEmpty() ? topic.topicPath : topic.topicTitle)
                )
                .setItems(labels, (d, which) -> chooseEvidenceRole(
                        claim, items.get(which), source
                ))
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void chooseEvidenceRole(
            HistorianWorkbenchStore.Claim claim,
            IndexCoreCartographyReader.ExtractionItem item,
            IndexCoreDatabase.SourceRecord source
    ) {
        HistorianWorkbenchStore.EvidenceRole[] roles =
                HistorianWorkbenchStore.EvidenceRole.values();
        String[] labels = new String[roles.length];
        for (int i = 0; i < roles.length; i++) labels[i] = evidenceRoleRo(roles[i]);

        new AlertDialog.Builder(this)
                .setTitle("Rolul dovezii")
                .setMessage(
                        "SURSA: " + source.displayName()
                                + (item.page.isEmpty() ? "" : "\nPAGINA: " + item.page)
                                + "\nUNITATE: P" + (item.paragraphIndex + 1)
                                + "\n\nRolul este atribuit de tine."
                )
                .setItems(labels, (d, which) -> addEvidenceNote(
                        claim, item, roles[which]
                ))
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void addEvidenceNote(
            HistorianWorkbenchStore.Claim claim,
            IndexCoreCartographyReader.ExtractionItem item,
            HistorianWorkbenchStore.EvidenceRole role
    ) {
        EditText note = multilineInput(
                "",
                "Notă critică opțională: de ce servește această unitate afirmației?"
        );
        note.setMinLines(4);

        new AlertDialog.Builder(this)
                .setTitle(evidenceRoleRo(role))
                .setMessage(
                        "Legătura păstrează ID-ul unității și poate reveni la sursă. "
                                + "Textul afirmației și cartografierea nu sunt modificate."
                )
                .setView(note)
                .setPositiveButton("ATAȘEAZĂ", (d, w) -> {
                    HistorianWorkbenchStore.linkEvidence(
                            db,
                            claim.id,
                            item.unitId,
                            role,
                            note.getText().toString()
                    );
                    rebuild();
                })
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void showEvidence(HistorianWorkbenchStore.Claim claim) {
        List<HistorianWorkbenchStore.Evidence> evidence =
                HistorianWorkbenchStore.evidence(db, claim.id);
        if (evidence.isEmpty()) {
            message(
                    "Dovezi",
                    "Afirmația nu are încă dovezi atașate. Acest lucru este păstrat explicit."
            );
            return;
        }

        String[] labels = new String[evidence.size()];
        for (int i = 0; i < evidence.size(); i++) {
            HistorianWorkbenchStore.Evidence item = evidence.get(i);
            StringBuilder row = new StringBuilder();
            row.append(evidenceRoleRo(item.role))
                    .append(" • ")
                    .append(item.sourceTitle.isEmpty() ? item.sourceId : item.sourceTitle);
            if (!item.page.isEmpty()) row.append(" • pag. ").append(item.page);
            row.append(" • P").append(item.paragraphIndex + 1);
            if (!item.surfaces.isEmpty()) row.append("\n„").append(trim(item.surfaces, 130)).append("”");
            if (!item.note.isEmpty()) row.append("\nNOTĂ: ").append(trim(item.note, 100));
            labels[i] = row.toString();
        }

        new AlertDialog.Builder(this)
                .setTitle(claim.displayTitle() + " • dovezi")
                .setItems(labels, (d, which) -> showEvidenceDetail(
                        claim, evidence.get(which)
                ))
                .setNegativeButton("ÎNCHIDE", null)
                .show();
    }

    private void showEvidenceDetail(
            HistorianWorkbenchStore.Claim claim,
            HistorianWorkbenchStore.Evidence item
    ) {
        StringBuilder msg = new StringBuilder();
        msg.append("ROL: ").append(evidenceRoleRo(item.role))
                .append("\nSURSA: ").append(
                        item.sourceTitle.isEmpty() ? item.sourceId : item.sourceTitle
                );
        if (!item.sourceAuthor.isEmpty()) msg.append("\nAUTOR: ").append(item.sourceAuthor);
        if (!item.page.isEmpty()) msg.append("\nPAGINA: ").append(item.page);
        msg.append("\nPARAGRAF: P").append(item.paragraphIndex + 1);
        if (item.sentenceIndex >= 0) msg.append(" • S").append(item.sentenceIndex + 1);
        if (item.clauseIndex >= 0) msg.append(" • C").append(item.clauseIndex + 1);
        if (!item.outlineTitle.isEmpty()) msg.append("\nSECȚIUNE: ").append(item.outlineTitle);
        if (!item.function.isEmpty()) msg.append("\nFUNCȚIE: ").append(item.function);
        if (!item.relation.isEmpty()) msg.append("\nRELAȚIE: ").append(item.relation);
        if (!item.topics.isEmpty()) msg.append("\nINDICI: ").append(item.topics);
        if (!item.surfaces.isEmpty()) msg.append("\nDOVEZI LEXICALE: ").append(item.surfaces);
        if (!item.note.isEmpty()) msg.append("\n\nNOTA TA\n").append(item.note);
        msg.append("\n\nUNIT ID: ").append(item.unitId);

        new AlertDialog.Builder(this)
                .setTitle("Dovadă reversibilă")
                .setMessage(msg.toString())
                .setPositiveButton("ELIMINĂ LEGĂTURA", (d, w) -> {
                    HistorianWorkbenchStore.unlinkEvidence(
                            db, claim.id, item.unitId, item.role
                    );
                    rebuild();
                })
                .setNegativeButton("ÎNCHIDE", null)
                .show();
    }

    private void chooseSourceForCriticism() {
        List<IndexCoreDatabase.SourceRecord> sources =
                new ArrayList<>(db.listSources());
        if (sources.isEmpty()) {
            message("Critica sursei", "Nu există surse în registru.");
            return;
        }
        String[] labels = new String[sources.size()];
        for (int i = 0; i < sources.size(); i++) {
            IndexCoreDatabase.SourceRecord source = sources.get(i);
            HistorianWorkbenchStore.SourceCriticism criticism =
                    HistorianWorkbenchStore.sourceCriticism(db, source.id);
            labels[i] = (criticism.isEmpty() ? "○ " : "● ")
                    + source.displayName()
                    + (source.author.isEmpty() ? "" : "\n" + source.author);
        }

        new AlertDialog.Builder(this)
                .setTitle("Critica sursei")
                .setMessage("● = fișă critică existentă")
                .setItems(labels, (d, which) -> editSourceCriticism(
                        sources.get(which).id
                ))
                .setNegativeButton("ÎNCHIDE", null)
                .show();
    }

    private void editSourceCriticism(String sourceId) {
        IndexCoreDatabase.SourceRecord source = db.source(sourceId);
        if (source == null) {
            message("Critica sursei", "Sursa nu mai este disponibilă.");
            return;
        }
        HistorianWorkbenchStore.SourceCriticism old =
                HistorianWorkbenchStore.sourceCriticism(db, sourceId);

        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(16), dp(6), dp(16), dp(8));

        EditText sourceClass = singleInput("Tip / statut sursă", old.sourceClass);
        EditText genre = singleInput("Gen / formă documentară", old.genre);
        EditText dating = singleInput("Datare / perioadă", old.dating);
        EditText origin = singleInput("Loc / mediu de producere", old.origin);
        EditText language = singleInput("Limbă", old.language);
        EditText provenance = multilineInput(old.provenance, "Proveniență / origine materială / arhivă");
        provenance.setMinLines(3);
        EditText transmission = multilineInput(old.transmission, "Transmitere / tradiție textuală / manuscrise");
        transmission.setMinLines(3);
        EditText audience = multilineInput(old.audience, "Public / destinatar / funcție");
        audience.setMinLines(3);
        EditText proximity = multilineInput(old.proximity, "Raport temporal și pozițional față de evenimente");
        proximity.setMinLines(3);
        EditText perspective = multilineInput(old.perspective, "Poziționare / perspectivă / interese observate");
        perspective.setMinLines(3);
        EditText authenticity = multilineInput(old.authenticity, "Autenticitate / integritate / probleme de atribuire");
        authenticity.setMinLines(3);
        EditText edition = multilineInput(old.editionNotes, "Ediție / traducere / aparat critic");
        edition.setMinLines(3);
        EditText notes = multilineInput(old.researchNotes, "Observații metodologice ale cercetătorului");
        notes.setMinLines(4);

        fields.addView(sourceClass);
        fields.addView(genre);
        fields.addView(dating);
        fields.addView(origin);
        fields.addView(language);
        fields.addView(provenance);
        fields.addView(transmission);
        fields.addView(audience);
        fields.addView(proximity);
        fields.addView(perspective);
        fields.addView(authenticity);
        fields.addView(edition);
        fields.addView(notes);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(fields);

        new AlertDialog.Builder(this)
                .setTitle("Fișă critică — " + source.displayName())
                .setMessage(
                        "Câmpurile sunt observațiile tale. Aplicația nu atribuie automat credibilitate sau intenții."
                )
                .setView(scroll)
                .setPositiveButton("SALVEAZĂ", (d, w) -> {
                    HistorianWorkbenchStore.saveSourceCriticism(
                            db,
                            new HistorianWorkbenchStore.SourceCriticism(
                                    sourceId,
                                    sourceClass.getText().toString(),
                                    genre.getText().toString(),
                                    dating.getText().toString(),
                                    origin.getText().toString(),
                                    language.getText().toString(),
                                    provenance.getText().toString(),
                                    transmission.getText().toString(),
                                    audience.getText().toString(),
                                    proximity.getText().toString(),
                                    perspective.getText().toString(),
                                    authenticity.getText().toString(),
                                    edition.getText().toString(),
                                    notes.getText().toString(),
                                    System.currentTimeMillis()
                            )
                    );
                    rebuild();
                })
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void showSourceRelations() {
        List<HistorianSourceRelationsStore.Relation> values =
                HistorianSourceRelationsStore.list(db);
        String[] labels = new String[values.size() + 1];
        for (int i = 0; i < values.size(); i++) {
            HistorianSourceRelationsStore.Relation value = values.get(i);
            labels[i] = sourceLabel(value.leftTitle, value.leftSourceId)
                    + "  → " + relationRo(value.type) + " →  "
                    + sourceLabel(value.rightTitle, value.rightSourceId)
                    + (value.note.isEmpty() ? "" : "\n" + trim(value.note, 110));
        }
        labels[labels.length - 1] = "＋ RELAȚIE NOUĂ";

        new AlertDialog.Builder(this)
                .setTitle("Relații între surse / ediții / martori")
                .setItems(labels, (d, which) -> {
                    if (which == values.size()) chooseRelationLeftSource();
                    else showSourceRelation(values.get(which));
                })
                .setNegativeButton("ÎNCHIDE", null)
                .show();
    }

    private void showSourceRelation(HistorianSourceRelationsStore.Relation value) {
        if (value == null) return;
        new AlertDialog.Builder(this)
                .setTitle(relationRo(value.type))
                .setMessage(
                        sourceLabel(value.leftTitle, value.leftSourceId)
                                + "\n→ " + relationRo(value.type) + " →\n"
                                + sourceLabel(value.rightTitle, value.rightSourceId)
                                + (value.note.isEmpty() ? "" : "\n\nNOTĂ\n" + value.note)
                )
                .setPositiveButton("ȘTERGE RELAȚIA", (d, w) -> {
                    HistorianSourceRelationsStore.remove(
                            db, value.leftSourceId, value.rightSourceId, value.type
                    );
                    rebuild();
                })
                .setNegativeButton("ÎNCHIDE", null)
                .show();
    }

    private void chooseRelationLeftSource() {
        List<IndexCoreDatabase.SourceRecord> sources =
                new ArrayList<>(db.listSources());
        if (sources.size() < 2) {
            message("Relații între surse", "Sunt necesare cel puțin două surse în registru.");
            return;
        }
        String[] labels = new String[sources.size()];
        for (int i = 0; i < sources.size(); i++) {
            labels[i] = sources.get(i).displayName()
                    + (sources.get(i).author.isEmpty() ? "" : "\n" + sources.get(i).author);
        }
        new AlertDialog.Builder(this)
                .setTitle("1/3 — Sursa de plecare")
                .setItems(labels, (d, which) -> chooseRelationType(sources.get(which), sources))
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void chooseRelationType(
            IndexCoreDatabase.SourceRecord left,
            List<IndexCoreDatabase.SourceRecord> allSources
    ) {
        HistorianSourceRelationsStore.RelationType[] types =
                HistorianSourceRelationsStore.RelationType.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) labels[i] = relationRo(types[i]);

        new AlertDialog.Builder(this)
                .setTitle("2/3 — Tipul relației")
                .setMessage(left.displayName())
                .setItems(labels, (d, which) ->
                        chooseRelationRightSource(left, types[which], allSources))
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void chooseRelationRightSource(
            IndexCoreDatabase.SourceRecord left,
            HistorianSourceRelationsStore.RelationType type,
            List<IndexCoreDatabase.SourceRecord> allSources
    ) {
        List<IndexCoreDatabase.SourceRecord> candidates = new ArrayList<>();
        for (IndexCoreDatabase.SourceRecord source : allSources) {
            if (!source.id.equals(left.id)) candidates.add(source);
        }
        String[] labels = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            labels[i] = candidates.get(i).displayName()
                    + (candidates.get(i).author.isEmpty() ? "" : "\n" + candidates.get(i).author);
        }

        new AlertDialog.Builder(this)
                .setTitle("3/3 — Sursa legată")
                .setMessage(left.displayName() + "\n→ " + relationRo(type) + " →")
                .setItems(labels, (d, which) ->
                        saveSourceRelation(left, candidates.get(which), type))
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private void saveSourceRelation(
            IndexCoreDatabase.SourceRecord left,
            IndexCoreDatabase.SourceRecord right,
            HistorianSourceRelationsStore.RelationType type
    ) {
        EditText note = multilineInput(
                "",
                "Notă opțională: ediție, filiație, dependență, motivul relației."
        );
        note.setMinLines(4);
        new AlertDialog.Builder(this)
                .setTitle(relationRo(type))
                .setMessage(
                        left.displayName() + "\n→ " + relationRo(type) + " →\n"
                                + right.displayName()
                )
                .setView(note)
                .setPositiveButton("SALVEAZĂ", (d, w) -> {
                    HistorianSourceRelationsStore.save(
                            db, left.id, right.id, type, note.getText().toString()
                    );
                    rebuild();
                })
                .setNegativeButton("ANULEAZĂ", null)
                .show();
    }

    private String relationRo(HistorianSourceRelationsStore.RelationType type) {
        if (type == null) return "ALTĂ RELAȚIE";
        switch (type) {
            case SAME_WORK: return "ACELEIAȘI OPERE";
            case EDITION_OF: return "EDIȚIE A";
            case TRANSLATION_OF: return "TRADUCERE A";
            case MANUSCRIPT_OF: return "MARTOR / MANUSCRIS AL";
            case COPY_OF: return "COPIE A";
            case DERIVED_FROM: return "DERIVATĂ DIN";
            case CITES: return "CITEAZĂ";
            case RESPONDS_TO: return "RĂSPUNDE LA";
            case CONTEMPORARY_WITH: return "CONTEMPORANĂ CU";
            case OTHER:
            default: return "ALTĂ RELAȚIE";
        }
    }

    private String sourceLabel(String title, String id) {
        return title == null || title.trim().isEmpty() ? id : title;
    }

    private void showAuditTrail() {
        List<HistorianWorkbenchStore.Audit> values =
                HistorianWorkbenchStore.auditTrail(db, 250);
        if (values.isEmpty()) {
            message("Jurnal decizii", "Nu există încă decizii metodologice înregistrate.");
            return;
        }

        StringBuilder out = new StringBuilder();
        DateFormat format = DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT
        );
        for (HistorianWorkbenchStore.Audit value : values) {
            out.append("\n• ")
                    .append(format.format(new Date(value.createdAt)))
                    .append(" • ").append(value.entityType)
                    .append(" • ").append(value.action);
            if (!value.detail.isEmpty()) out.append("\n  ").append(trim(value.detail, 180));
        }

        TextView body = text(11.5f, Color.rgb(220, 230, 237));
        body.setText(out.toString().trim());
        body.setPadding(dp(14), dp(8), dp(14), dp(12));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);

        new AlertDialog.Builder(this)
                .setTitle("Jurnal decizii metodologice")
                .setView(scroll)
                .setPositiveButton("ÎNCHIDE", null)
                .show();
    }

    private void exportDossier() {
        pendingExportText = HistorianResearchExporter.build(this, db);
        if (pendingExportText.isEmpty()) {
            message("Export", "Nu există un dosar de cercetare de exportat.");
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "dosar_cercetare_bibliotopicsearch.txt");
        startActivityForResult(intent, REQ_EXPORT_DOSSIER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_EXPORT_DOSSIER || resultCode != RESULT_OK
                || data == null || pendingExportText.isEmpty()) return;
        Uri uri = data.getData();
        if (uri == null) return;

        try {
            OutputStream stream = getContentResolver().openOutputStream(uri, "wt");
            if (stream == null) throw new IllegalStateException("Nu se poate deschide destinația.");
            try (OutputStream out = stream) {
                out.write(pendingExportText.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            message("Export", "Dosarul reproductibil de cercetare a fost exportat.");
        } catch (Exception failure) {
            message(
                    "Export nereușit",
                    failure.getMessage() == null ? "Nu s-a putut scrie fișierul." : failure.getMessage()
            );
        }
    }

    private String statusRo(HistorianWorkbenchStore.ClaimStatus status) {
        if (status == null) return "DESCHISĂ";
        switch (status) {
            case PROVISIONAL: return "PROVIZORIE";
            case SUPPORTED: return "SUSȚINUTĂ";
            case DISPUTED: return "DISPUTATĂ";
            case RETIRED: return "RETRASĂ";
            case OPEN:
            default: return "DESCHISĂ";
        }
    }

    private String evidenceRoleRo(HistorianWorkbenchStore.EvidenceRole role) {
        if (role == null) return "CONTEXTUALIZEAZĂ";
        switch (role) {
            case SUPPORTS: return "SUSȚINE";
            case CONTRADICTS: return "CONTRAZICE";
            case NUANCES: return "NUANȚEAZĂ";
            case EXEMPLIFIES: return "EXEMPLIFICĂ";
            case CONTEXTUALIZES:
            default: return "CONTEXTUALIZEAZĂ";
        }
    }

    private EditText singleInput(String hint, String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        return input;
    }

    private EditText multilineInput(String value, String hint) {
        EditText input = new EditText(this);
        input.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(7);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        return input;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(9.5f);
        return button;
    }

    private TextView text(float size, int color) {
        TextView view = new TextView(this);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.12f);
        return view;
    }

    private void message(String title, String body) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton("OK", null)
                .show();
    }

    private String trim(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("\\s+", " ").trim();
        if (clean.length() <= max) return clean;
        return clean.substring(0, Math.max(1, max - 1)).trim() + "…";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
