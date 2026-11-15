package ro.bibliotopicsearch.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Offline universal index workshop. Does not rewrite a topic map or interpret
 * OCR sentences. Every promotion from inbox is an explicit researcher action.
 */
public final class UniversalIndexActivity extends AppCompatActivity {
    private static final int REQ_IMPORT = 790;
    private static final int REQ_EXPORT = 791;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView counts;
    private TextView status;
    private volatile boolean closing;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        render();
        refreshCounts();
    }

    @Override protected void onDestroy() {
        closing = true;
        worker.shutdownNow();
        super.onDestroy();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(12, 20, 28));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(26), dp(18), dp(30));
        scroll.addView(root);

        addText(root, "INDEX UNIVERSAL", 24, Color.WHITE);
        addText(root, "Registru offline • ID canonic unic • variante sub același concept • fără interpretare", 13,
                Color.rgb(173, 206, 188));
        counts = addText(root, "Se citesc indicii…", 15, Color.WHITE);
        status = addText(root, "Importul nu modifică tematicile existente.", 13,
                Color.rgb(195, 205, 217));

        addButton(root, "IMPORTĂ INDICI CSV", () -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[]{"text/csv", "text/plain", "application/csv"});
            startActivityForResult(i, REQ_IMPORT);
        });

        addButton(root, "ADAUGĂ CONCEPT / INDICE MANUAL", this::manualConcept);

        addButton(root, "INBOX OCR • APROBĂ / RESPINGE", this::showInbox);

        addButton(root, "GENEREAZĂ INDEX CRONOLOGIC", () -> {
            new AlertDialog.Builder(this)
                    .setTitle("Cronologie deterministă")
                    .setMessage("Adaugă anii 1–2100 și secolele I–XXI drept indici. "
                            + "O cifră detectată poate fi și număr de pagină; "
                            + "asocierea istorică trebuie verificată de cercetător. "
                            + "La repetare, aceleași ID-uri nu se dublează.")
                    .setNegativeButton("ANULEAZĂ", null)
                    .setPositiveButton("GENEREAZĂ", (d,w) ->
                            background("Generez indexul cronologic…",
                                    () -> UniversalIndexStore.get(this).seedChronology()))
                    .show();
        });

        addButton(root, "EXPORTĂ INDEXUL CSV / COPIE DE SIGURANȚĂ", () -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("text/csv");
            i.putExtra(Intent.EXTRA_TITLE, "BiblioTopicSearch_index_universal.csv");
            startActivityForResult(i, REQ_EXPORT);
        });

        addText(root, "FORMAT CSV (UTF-8)", 16, Color.WHITE);
        addText(root, "id,label,category,aliases,source", 13, Color.rgb(186, 221, 247));
        addText(root, "GND:1234,Persoană istorică,PERSOANĂ,\"nume alternativ|alt nume\",GND\n"
                        + "Formele asociate folosesc separatorul |. CSV acceptă și ; sau TAB.\n"
                        + "Identificatorii proveniți din autorități diferite rămân separați; "
                        + "nu se unesc automat omonimele.", 12,
                Color.rgb(167, 181, 192));

        addText(root, "NOTĂ METODOLOGICĂ", 15, Color.WHITE);
        addText(root, "Un candidat OCR nu este un indice validat. "
                        + "Un indice detectat nu demonstrează relevanță istorică. "
                        + "Citatele OCR se verifică față de imaginea originală.", 12,
                Color.rgb(210, 185, 157));
        setContentView(scroll);
    }

    private void manualConcept() {
        LinearLayout pane = new LinearLayout(this);
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.setPadding(dp(18), dp(6), dp(18), 0);
        EditText id = edit(pane, "ID canonic (ex: PERSONAL:0001)");
        EditText title = edit(pane, "Nume / concept");
        EditText category = edit(pane, "Categorie (ex: INSTITUȚIE)");
        EditText aliases = edit(pane, "Forme alternative separate cu |");
        new AlertDialog.Builder(this)
                .setTitle("Indice canonic nou")
                .setView(pane)
                .setNegativeButton("ANULEAZĂ", null)
                .setPositiveButton("SALVEAZĂ", (dialog,which) -> {
                    String key = id.getText().toString().trim();
                    String name = title.getText().toString().trim();
                    if (key.isEmpty() || name.isEmpty()) {
                        toast("ID-ul și numele sunt obligatorii."); return;
                    }
                    background("Salvez indicele…", () -> UniversalIndexStore.get(this)
                            .addCanonical(key,name,category.getText().toString(),
                                    "UTILIZATOR",aliases.getText().toString()));
                }).show();
    }

    private void showInbox() {
        background("Citesc inboxul…", () -> {
            List<UniversalIndexStore.Pending> list =
                    UniversalIndexStore.get(this).pending(150);
            ui(() -> {
                if (list.isEmpty()) {
                    message("INBOX OCR", "Nu există candidați propuși. "
                            + "Din detector: INDICI → CANDIDAȚI → ADAUGĂ ÎN INBOX.");
                    return;
                }
                String[] titles = new String[list.size()];
                for (int i = 0; i < titles.length; i++) {
                    UniversalIndexStore.Pending item = list.get(i);
                    titles[i] = item.original + " • " + item.kind + " • " + item.seenCount;
                }
                new AlertDialog.Builder(this).setTitle("INBOX • " + list.size() + " candidați")
                        .setItems(titles, (d,index) -> inspectCandidate(list.get(index)))
                        .setNegativeButton("ÎNCHIDE",null).show();
            });
        });
    }

    private void inspectCandidate(UniversalIndexStore.Pending c) {
        new AlertDialog.Builder(this)
                .setTitle(c.original)
                .setMessage(c.kind + " • " + c.seenCount + " confirmări în inbox\n\n"
                        + "CITAT OCR:\n«" + c.quote + "»\n\n"
                        + "Citatul poate conține erori OCR. Verifică pagina.")
                .setPositiveButton("APROBĂ", (d,w) -> approveCandidate(c))
                .setNeutralButton("RESPINGE", (d,w) -> background("Resping…",
                        () -> UniversalIndexStore.get(this).reject(c)))
                .setNegativeButton("ÎNCHIDE",null).show();
    }

    private void approveCandidate(UniversalIndexStore.Pending c) {
        LinearLayout pane = new LinearLayout(this);
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.setPadding(dp(18), dp(6), dp(18), 0);
        EditText id = edit(pane, "ID canonic");
        id.setText("OCR:" + c.kind.replaceAll("\\W+", "_") + ":" +
                Integer.toHexString(UniversalIndexStore.fold(c.original).hashCode()));
        EditText category = edit(pane, "Categorie");
        category.setText(c.kind.contains("AN") || c.kind.contains("SECOL")
                ? "CRONOLOGIE" : "CONCEPT");
        new AlertDialog.Builder(this).setTitle("Validează indicele")
                .setView(pane)
                .setMessage("Confirmarea este decizia cercetătorului, nu o deducție automată.")
                .setNegativeButton("ANULEAZĂ",null)
                .setPositiveButton("APROBĂ", (d,w) ->
                        background("Adaug indicele…", () ->
                                UniversalIndexStore.get(this).approve(c,
                                        id.getText().toString(),category.getText().toString())))
                .show();
    }

    private static EditText edit(LinearLayout parent, String hint) {
        EditText e = new EditText(parent.getContext());
        e.setHint(hint);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.rgb(155, 168, 181));
        e.setTextSize(14);
        parent.addView(e,new LinearLayout.LayoutParams(-1,-2));
        return e;
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int request, int result, @Nullable Intent data) {
        super.onActivityResult(request,result,data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == REQ_IMPORT) {
            background("Import din fișier…", () -> {
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalStateException("Nu pot deschide fișierul");
                    UniversalIndexStore.ImportResult value = UniversalIndexStore.get(this)
                            .importCsv(new InputStreamReader(input, StandardCharsets.UTF_8));
                    ui(() -> toast("Procesate: " + value.processed + " • invalide: " + value.invalid));
                }
            });
        } else if (request == REQ_EXPORT) {
            background("Export index…", () -> {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IllegalStateException("Nu pot salva fișierul");
                    try (BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(out,StandardCharsets.UTF_8))) {
                        UniversalIndexStore.get(this).exportCsv(writer);
                    }
                }
            });
        }
    }

    private interface Task { void execute() throws Exception; }

    private void background(String text, Task task) {
        ui(() -> status.setText(text));
        worker.execute(() -> {
            try {
                task.execute();
                ui(() -> {
                    status.setText("Operația s-a încheiat. Repornește detecția pentru reîncărcarea indexului.");
                    refreshCounts();
                });
            } catch (Exception error) {
                android.util.Log.e("BiblioUniversal", "Index operation failed", error);
                ui(() -> status.setText("Operație nereușită: " + error.getMessage()));
            }
        });
    }

    private void refreshCounts() {
        worker.execute(() -> {
            try {
                UniversalIndexStore.Counts c = UniversalIndexStore.get(this).counts();
                ui(() -> counts.setText(c.concepts + " indici canonici • " + c.variants
                        + " variante • " + c.pending + " candidați în inbox"));
            } catch (Exception error) {
                ui(() -> counts.setText("Indexul nu a putut fi citit."));
            }
        });
    }

    private void ui(Runnable action) {
        runOnUiThread(() -> { if (!closing && !isFinishing()) action.run(); });
    }

    private void message(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("ÎNCHIDE",null).show();
    }
    private void toast(String value) {
        Toast.makeText(this,value,Toast.LENGTH_LONG).show();
    }
    private void addButton(LinearLayout parent,String text,Runnable click) {
        Button b = new Button(this);
        b.setText(text);b.setAllCaps(false);b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(46,97,109)));
        b.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));
        lp.topMargin=dp(9);parent.addView(b,lp);
    }
    private TextView addText(LinearLayout parent,String value,int size,int color) {
        TextView t=new TextView(this);
        t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin=dp(13);parent.addView(t,lp);return t;
    }
    private int dp(int value) {return Math.round(getResources().getDisplayMetrics().density*value);}
}
