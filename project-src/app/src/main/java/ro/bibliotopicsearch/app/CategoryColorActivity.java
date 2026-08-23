package ro.bibliotopicsearch.app;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public final class CategoryColorActivity extends AppCompatActivity {
    private LinearLayout categoryContainer;
    private TextView swapStatus;
    private SwapTarget pendingSwap;

    private static final class SwapTarget {
        final TopicNode node;
        final boolean builtIn;

        SwapTarget(TopicNode node, boolean builtIn) {
            this.node = node;
            this.builtIn = builtIn;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        refreshCategories();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCategories();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(28));
        root.setBackgroundColor(Color.rgb(244, 240, 230));
        scroll.addView(root);

        root.addView(heading("CULORI CATEGORII", 22));

        TextView help = body(
                "Fiecare categorie poate avea propria culoare, indiferent dacă aparține hărții custom, " +
                "stratului TEXTUAL sau stratului SEMANTIC. Apasă CULOARE pentru paletă RGB/HEX. " +
                "Pentru a interschimba două culori, apasă ↔ pe prima categorie și apoi ↔ pe a doua."
        );
        help.setPadding(0, dp(5), 0, dp(10));
        root.addView(help);

        swapStatus = body("↔ Nicio categorie selectată pentru schimb.");
        swapStatus.setTextColor(Color.rgb(53, 92, 125));
        swapStatus.setPadding(0, 0, 0, dp(8));
        root.addView(swapStatus);

        categoryContainer = new LinearLayout(this);
        categoryContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(categoryContainer);

        setContentView(scroll);
    }

    private void refreshCategories() {
        if (categoryContainer == null) return;
        categoryContainer.removeAllViews();

        MapProfile active = TopicLibraryStore.getActive(this);
        TopicMap custom = TopicMapStore.load(this);
        TopicMap textual = BuiltInMaps.textual(this);
        TopicMap semantic = BuiltInMaps.semantic(this);
        BuiltInColorStore.apply(this, textual);
        BuiltInColorStore.apply(this, semantic);

        addSection("CUSTOM • " + active.name, custom, false);
        addSection("TEXTUAL", textual, true);
        addSection("SEMANTIC", semantic, true);
    }

    private void addSection(String title, TopicMap map, boolean builtIn) {
        TextView section = heading(title, 17);
        section.setPadding(0, dp(14), 0, dp(5));
        categoryContainer.addView(section);

        int visibleCount = 0;
        if (map != null) {
            for (TopicNode node : map.nodes) {
                if (node == null) continue;
                if (builtIn && !node.enabled) continue;
                categoryContainer.addView(categoryRow(node, builtIn));
                visibleCount++;
            }
        }

        if (visibleCount == 0) {
            TextView empty = body("Nu există categorii în acest strat.");
            empty.setPadding(0, dp(4), 0, dp(6));
            categoryContainer.addView(empty);
        }
    }

    private View categoryRow(TopicNode node, boolean builtIn) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(8));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(10));
        background.setStroke(dp(2), withAlpha(node.color, 150));
        card.setBackground(background);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(dp(Math.max(0, node.level - 1) * 9), dp(4), 0, dp(4));
        card.setLayoutParams(cardLp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextSize(24);
        dot.setTextColor(node.color);
        dot.setGravity(Gravity.CENTER);
        top.addView(dot, new LinearLayout.LayoutParams(dp(34), dp(46)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = heading(node.title, 14);
        TextView path = body(node.path);
        path.setTextSize(10.5f);
        path.setMaxLines(1);
        labels.addView(title);
        labels.addView(path);
        top.addView(labels, new LinearLayout.LayoutParams(0, dp(48), 1f));
        card.addView(top);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button color = new Button(this);
        color.setText("CULOARE");
        color.setAllCaps(false);
        color.setTextColor(contrastText(node.color));
        color.setBackgroundTintList(ColorStateList.valueOf(node.color));
        actions.addView(color, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button swap = new Button(this);
        swap.setText("↔");
        swap.setTextSize(18);
        swap.setAllCaps(false);
        swap.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(53, 92, 125)));
        swap.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams swapLp = new LinearLayout.LayoutParams(dp(64), dp(44));
        swapLp.setMargins(dp(6), 0, 0, 0);
        actions.addView(swap, swapLp);
        card.addView(actions);

        color.setOnClickListener(v -> ColorPickerDialog.show(this, node.color, selected -> {
            persistColor(new SwapTarget(node, builtIn), selected);
            pendingSwap = null;
            updateSwapStatus();
            refreshCategories();
            toast("Culoarea categoriei a fost salvată.");
        }));

        swap.setOnClickListener(v -> handleSwap(new SwapTarget(node, builtIn)));
        return card;
    }

    private void handleSwap(SwapTarget selected) {
        if (pendingSwap == null) {
            pendingSwap = selected;
            updateSwapStatus();
            return;
        }

        if (sameTarget(pendingSwap, selected)) {
            pendingSwap = null;
            updateSwapStatus();
            toast("Schimbul a fost anulat.");
            return;
        }

        int firstColor = pendingSwap.node.color;
        int secondColor = selected.node.color;
        persistColor(pendingSwap, secondColor);
        persistColor(selected, firstColor);
        pendingSwap = null;
        updateSwapStatus();
        refreshCategories();
        toast("Culorile celor două categorii au fost interschimbate.");
    }

    private boolean sameTarget(SwapTarget a, SwapTarget b) {
        return a != null && b != null
                && a.builtIn == b.builtIn
                && a.node != null && b.node != null
                && a.node.path.equals(b.node.path);
    }

    private void persistColor(SwapTarget target, int color) {
        if (target == null || target.node == null) return;
        if (target.builtIn) {
            BuiltInColorStore.setColor(this, target.node.path, color);
        } else {
            TopicMapStore.setNodeColor(this, target.node.path, color);
        }
        target.node.color = color;
    }

    private void updateSwapStatus() {
        if (swapStatus == null) return;
        if (pendingSwap == null || pendingSwap.node == null) {
            swapStatus.setText("↔ Nicio categorie selectată pentru schimb.");
        } else {
            swapStatus.setText("↔ Prima categorie: " + pendingSwap.node.title + " • alege a doua categorie.");
        }
    }

    private int contrastText(int color) {
        double luminance = 0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color);
        return luminance >= 165 ? Color.rgb(25, 31, 36) : Color.WHITE;
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private TextView heading(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setTextColor(Color.rgb(23, 32, 42));
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(Color.rgb(70, 75, 82));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
    }
}
