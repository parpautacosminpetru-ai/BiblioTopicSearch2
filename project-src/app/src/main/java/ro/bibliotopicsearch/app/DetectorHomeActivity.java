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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

/** Simple entry point; the full historian workspace remains under Advanced. */
public final class DetectorHomeActivity extends AppCompatActivity {
    private LinearLayout root;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        BundledThemeInstaller.ensureInstalled(this);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(12, 20, 27));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(32), dp(20), dp(24));
        scroll.addView(root);

        TextView title = text("BiblioTopicSearch 2", 27, Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);
        TextView intro = text("Ctrl+F pentru cărți fizice • OCR live • offline", 14,
                Color.rgb(166, 205, 188));
        intro.setPadding(0, dp(8), 0, dp(22));
        root.addView(intro);

        List<MapProfile> selected = TopicLibraryStore.selectedForScan(this);
        int nodes = 0;
        int terms = 0;
        for (MapProfile profile : selected) {
            TopicMap map = TopicMapStore.parseForProfile(
                    this, profile.id, profile.name, profile.rawText);
            for (TopicNode node : map.nodes) {
                if (!node.enabled) continue;
                nodes++;
                terms += Math.max(1, node.terms.size());
            }
        }
        root.addView(text(selected.size() + " tematici active • " + nodes
                + " noduri • " + terms + " termeni", 16, Color.WHITE));

        Button detector = button("PORNEȘTE DETECTORUL", true);
        detector.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        addButton(detector);

        Button themes = button("ALEGE TEMATICILE", true);
        themes.setOnClickListener(v -> startActivity(new Intent(this, TopicLibraryActivity.class)));
        addButton(themes);

        TextView mode = text("Alertare: " + AppPrefs.getAlertMode(this).name()
                + " • fără vibrații repetate", 13, Color.rgb(175, 192, 207));
        mode.setPadding(0, dp(12), 0, dp(10));
        root.addView(mode);

        Button settings = button("REGLAJE ȘI VIBRAȚII", false);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        addButton(settings);

        Button advanced = button("CERCETARE AVANSATĂ", false);
        advanced.setOnClickListener(v -> startActivity(new Intent(this, ResearchHomeActivity.class)));
        addButton(advanced);

        TextView note = text("Detectorul semnalează potriviri de text, nu certifică "
                + "relevanța istorică sau examinarea integrală a cărții.", 12,
                Color.rgb(149, 163, 176));
        note.setPadding(0, dp(18), 0, 0);
        root.addView(note);
        setContentView(scroll);
    }

    private void addButton(Button b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        lp.topMargin = dp(10);
        root.addView(b, lp);
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(
                primary ? Color.rgb(31, 125, 100) : Color.rgb(52, 76, 97)));
        return b;
    }

    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }
}
