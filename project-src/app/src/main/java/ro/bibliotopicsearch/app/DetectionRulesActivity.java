package ro.bibliotopicsearch.app;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

/** Deterministic detection contract readable in the installed app. */
public final class DetectionRulesActivity extends AppCompatActivity {
    private LinearLayout content;
    private final int bg = Color.rgb(13, 22, 30);
    private final int foreground = Color.rgb(235, 242, 247);
    private final int muted = Color.rgb(177, 195, 211);
    private final int highlight = Color.rgb(136, 221, 180);

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(24), dp(18), dp(35));
        scroll.addView(content);
        title("REGULI DE DETECȚIE", 24, foreground);
        text("BiblioTopicSearch 2.3 • OCR local, fără interpretare AI în detector", muted, 14);

        int profiles = BundledThemeInstaller.installedMapCount(this);
        List<MapProfile> selected = TopicLibraryStore.selectedForScan(this);
        int nodes = 0, terms = 0;
        for (MapProfile profile : selected) {
            TopicMap map = TopicMapStore.parseForProfile(this, profile.id, profile.name, profile.rawText);
            for (TopicNode node : map.nodes) {
                if (!node.enabled) continue;
                nodes++;
                terms += node.terms.size();
            }
        }
        title("ACOPERIRE REALĂ", 17, highlight);
        text("Tematici universitare incluse: " + profiles + "/7\n"
                + "Selectate pentru detector: " + selected.size() + "\n"
                + "Subiecte active: " + nodes + "\n"
                + "Asocieri cu expresii active: " + terms
                + "\nToate cele 144 de subiecte incluse au minimum 10 expresii.",
                foreground, 15);

        title("1. CE ESTE O POTRIVIRE", 17, highlight);
        text("Aplicația caută nume, denumiri alternative, evenimente, instituții, titluri de izvoare, "
                + "date și concepte într-un singur cadru OCR. Recunoaște sintagme care trec "
                + "peste o singură ruptură de rând din același bloc de text. "
                + "Ignorarea diacriticelor este activată implicit. "
                + "Nu deduce automat semnificația unui pasaj care nu conține indicii recunoscute.",
                foreground, 15);

        title("2. CE ÎNSEAMNĂ SEMNELE", 17, highlight);
        text("! TERMEN DISTINCTIV\nPotrivirea textuală integrală și exactă permite o vibrație directă. "
                + "Exemplu: !Căderea Constantinopolului.",
                foreground, 15);
        text("? sau ~ INDICIU CONTEXTUAL\nApare evidențiat. Nu vibrează singur. "
                + "Două indicii DIFERITE din ACELAȘI SUBIECT declanșează modul Echilibrat; "
                + "trei indicii declanșează modul Strict.",
                foreground, 15);
        text("- TERMEN VIZUAL\nEste încadrat, fără să contribuie vreodată la vibrație; "
                + "folosit mai ales la bibliografii și referințe.", foreground, 15);
        text("FĂRĂ SEMN (hărți personale)\nExplorare: un termen cu minimum 5 caractere; "
                + "Echilibrat: expresie de minimum 2 cuvinte; "
                + "Strict: expresie de minimum 3 cuvinte. "
                + "Această regulă nu schimbă modul de funcționare al semnelor !, ? și -.",
                foreground, 15);

        title("3. TREI MODURI DE VIBRAȚIE", 17, highlight);
        text("EXPLORARE — sensibil pentru termeni neetichetați; indicii ? tot au nevoie "
                + "de asociere în același subiect.\n"
                + "ECHILIBRAT (implicit) — ! sau două indicii contextuale distincte în același subiect.\n"
                + "STRICT — ! sau trei indicii contextuale distincte în același subiect.\n"
                + "Expresiile neetichetate de 2/3 cuvinte funcționează conform regulii de mai sus.",
                foreground, 15);

        title("4. CÂND NU VIBREAZĂ", 17, highlight);
        text("Un an singur (de exemplu 1453), un singur indiciu ?, bibliografia marcată -, "
                + "o formă doar asemănătoare/derivată din cea căutată sau două indicii "
                + "găsite în subiecte DIFERITE nu determină vibrația. "
                + "Pot rămâne totuși evidențiate vizual.", foreground, 15);

        title("5. FĂRĂ VIBRAȚII MULTIPLE", 17, highlight);
        text("Aceeași apariție OCR este evidențiată o singură dată chiar dacă interesează "
                + "mai multe subiecte. Se emite maximum un impuls pe evaluare; există "
                + "un interval global de 0,95 secunde. Un semnal trebuie să lipsească "
                + "din cel puțin două cadre OCR și să treacă minimum 2,2 secunde "
                + "pentru a se rearma.", foreground, 15);

        title("6. LIMITA ACOPERIRII EXHAUSTIVE", 17, highlight);
        text("Au fost indexate toate cele 144 de subiecte din cele 9 documente, "
                + "cu expresii alternative și contexte istorice. Este o acoperire "
                + "LEXICALĂ amplă, nu o garanție că fiecare paragraf relevant va fi recunoscut. "
                + "Calitatea luminii, tiparul, limba, ortografia veche și termenii care lipsesc "
                + "din vocabular pot produce omisiuni. "
                + "Dacă un pasaj e relevant și n-a fost încadrat, adaugă o expresie în editorul tematicii.",
                foreground, 15);

        Button close = new Button(this);
        close.setText("ÎNAPOI");
        close.setTextColor(Color.WHITE);
        close.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(39, 119, 97)));
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(18);
        content.addView(close, lp);
        setContentView(scroll);
    }

    private void title(String message, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setPadding(0, dp(20), 0, dp(6));
        content.addView(view);
    }

    private void text(String message, int color, int sp) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1.0f);
        view.setGravity(Gravity.START);
        view.setPadding(0, 0, 0, dp(6));
        content.addView(view);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
