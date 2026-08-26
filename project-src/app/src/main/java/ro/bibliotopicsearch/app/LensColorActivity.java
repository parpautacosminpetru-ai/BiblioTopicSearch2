package ro.bibliotopicsearch.app;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** Four stable visual roles; the user decides the actual colors. */
public final class LensColorActivity extends AppCompatActivity {
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rebuild();
    }

    private void rebuild() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(14, 23, 30));

        TextView title = new TextView(this);
        title.setText("CULORI LUPĂ");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Rolurile rămân aceleași; tu alegi culorile. LUPĂ afișează maximum trei roluri simultan.");
        hint.setTextColor(Color.rgb(195, 211, 221));
        hint.setTextSize(12);
        hint.setPadding(0, dp(6), 0, dp(12));
        root.addView(hint);

        addRole("ȚINTĂ / cuvânt căutat", LensPalette.Role.TARGET);
        addRole("SUBIECT / ancoră", LensPalette.Role.SUBJECT);
        addRole("FUNCȚIE / indiciu", LensPalette.Role.FUNCTION);
        addRole("RĂSPUNS direct", LensPalette.Role.ANSWER);

        Button reset = button("RESETEAZĂ CULORILE");
        reset.setOnClickListener(v -> { LensPalette.reset(this); rebuild(); });
        root.addView(reset, lp());

        Button close = button("ÎNAPOI");
        close.setOnClickListener(v -> finish());
        root.addView(close, lp());
        setContentView(root);
    }

    private void addRole(String label, LensPalette.Role role) {
        int color = LensPalette.get(this, role);
        Button b = button(label);
        b.setBackgroundTintList(ColorStateList.valueOf(color));
        b.setOnClickListener(v -> ColorPickerDialog.show(this, LensPalette.get(this, role), selected -> {
            LensPalette.set(this, role, selected);
            rebuild();
        }));
        root.addView(b, lp());
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(0, dp(4), 0, dp(4));
        return lp;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
