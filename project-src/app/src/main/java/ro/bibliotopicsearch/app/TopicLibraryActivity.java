package ro.bibliotopicsearch.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TopicLibraryActivity extends AppCompatActivity {
    private LinearLayout libraryContainer;
    private TextView summary;
    private EditText filterEdit;
    private TextView pagination;
    private Button previous,next;
    private int pageIndex;
    private static final int PAGE_SIZE=32;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(28));
        root.setBackgroundColor(Color.rgb(246, 248, 251));
        scroll.addView(root);

        TextView title = heading("Biblioteca de teme", 24);
        root.addView(title);

        summary = body("");
        summary.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(summary);

        filterEdit = new EditText(this);
        filterEdit.setHint("Caută hartă sau folder");
        filterEdit.setSingleLine(true);
        root.addView(filterEdit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        filterEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { pageIndex=0;refresh(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        LinearLayout buttons = row();
        Button newMap = button("+ Hartă");
        Button editActive = button("Toate OCR");
        Button none=button("Nicio hartă OCR");
        buttons.addView(newMap, weighted());
        buttons.addView(editActive, weighted());
        buttons.addView(none,weighted());
        none.setOnClickListener(v->{TopicLibraryStore.setScanProfiles(this,java.util.Collections.emptySet());refresh();});
        root.addView(buttons);

        newMap.setOnClickListener(v -> showCreateDialog());
        editActive.setOnClickListener(v -> {
            java.util.Set<String> ids = new java.util.LinkedHashSet<>();
            for (MapProfile profile : TopicLibraryStore.list(this)) ids.add(profile.id);
            TopicLibraryStore.setScanProfiles(this, ids);
            refresh();
        });

        LinearLayout navigation=row();previous=button("◀");next=button("▶");pagination=body("");
        pagination.setGravity(Gravity.CENTER);
        navigation.addView(previous,new LinearLayout.LayoutParams(dp(52),dp(44)));
        navigation.addView(pagination,new LinearLayout.LayoutParams(0,dp(44),1));
        navigation.addView(next,new LinearLayout.LayoutParams(dp(52),dp(44)));root.addView(navigation);
        previous.setOnClickListener(v->{pageIndex=Math.max(0,pageIndex-1);refresh();});
        next.setOnClickListener(v->{pageIndex++;refresh();});
        libraryContainer = new LinearLayout(this);
        libraryContainer.setOrientation(LinearLayout.VERTICAL);
        libraryContainer.setPadding(0, dp(12), 0, 0);
        root.addView(libraryContainer);

        setContentView(scroll);
        UiInsets.apply(scroll);
    }

    private void refresh() {
        List<MapProfile> maps = TopicLibraryStore.list(this);
        MapProfile active = TopicLibraryStore.getActive(this);
        java.util.Set<String> selected = new java.util.LinkedHashSet<>();
        for (MapProfile profile : TopicLibraryStore.scanProfiles(this, maps)) selected.add(profile.id);
        summary.setText(maps.size() + " hărți · " + selected.size() + " OCR");
        libraryContainer.removeAllViews();

        String query = filterEdit == null ? "" : filterEdit.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        java.util.List<MapProfile> filtered=new java.util.ArrayList<>();
        for (MapProfile profile : maps) {
            if (!query.isEmpty()) {
                String haystack = LiteralText.canonical(profile.folder+" "+profile.name,true);
                if (!haystack.contains(LiteralText.canonical(query,true))) continue;
            }
            filtered.add(profile);
        }
        int pageCount=Math.max(1,(filtered.size()+PAGE_SIZE-1)/PAGE_SIZE);
        pageIndex=Math.min(Math.max(0,pageIndex),pageCount-1);
        pagination.setText(filtered.size()+" hărți · "+(pageIndex+1)+"/"+pageCount);
        previous.setEnabled(pageIndex>0);next.setEnabled(pageIndex+1<pageCount);
        Map<String, java.util.List<MapProfile>> groups = new LinkedHashMap<>();
        int from=pageIndex*PAGE_SIZE,to=Math.min(filtered.size(),from+PAGE_SIZE);
        for(int i=from;i<to;i++){
            MapProfile profile=filtered.get(i);
            groups.computeIfAbsent(profile.folder, ignored -> new java.util.ArrayList<>()).add(profile);
        }
        if(filtered.isEmpty())libraryContainer.addView(body("Nicio hartă găsită."));

        for (Map.Entry<String, java.util.List<MapProfile>> entry : groups.entrySet()) {
            TextView folder = heading("▾  " + entry.getKey(), 17);
            folder.setPadding(dp(4), dp(12), 0, dp(4));
            libraryContainer.addView(folder);
            for (MapProfile profile : entry.getValue()) {
                libraryContainer.addView(mapCard(profile, profile.id.equals(active.id), selected));
            }
        }
    }

    private View mapCard(MapProfile profile, boolean active, java.util.Set<String> selected) {
        boolean scanning = selected.contains(profile.id);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(active ? 2 : 1), active ? profile.color : Color.rgb(213, 208, 197));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(4), 0, dp(6));
        card.setLayoutParams(lp);

        LinearLayout header = row();
        Button color = colorButton(profile.color, "Culoarea hărții " + profile.name);
        header.addView(color, new LinearLayout.LayoutParams(dp(48), dp(48)));
        color.setOnClickListener(v -> ColorPickerDialog.show(this, profile.color, chosen -> {
            TopicLibraryStore.setMapColor(this, profile.id, chosen);
            refresh();
        }));
        TextView title = heading(profile.name, 16);
        title.setTextColor(active ? Color.rgb(36, 59, 83) : Color.rgb(23, 32, 42));
        title.setPadding(dp(8), 0, dp(4), 0);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setMaxLines(2);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView badge = smallBadge(scanning ? "OCR ✓" : "OCR —", scanning);
        header.addView(badge);
        card.addView(header);
        if(profile.id.startsWith(HistoricalLexicon.ID_PREFIX)){
            TextView count=body(countMapTerms(profile.rawText)+" termeni / expresii");
            count.setPadding(dp(8),0,0,dp(4));card.addView(count);
        }

        LinearLayout actions = row();
        Button activate = button(scanning ? "Oprește OCR" : "Pornește OCR");
        Button edit = button("Editează");
        Button more = button("⋯");
        actions.addView(activate, weighted());
        actions.addView(edit, weighted());
        actions.addView(more, new LinearLayout.LayoutParams(dp(58), dp(44)));
        card.addView(actions);

        activate.setOnClickListener(v -> {
            java.util.Set<String> changed = new java.util.LinkedHashSet<>(selected);
            if (!changed.add(profile.id)) changed.remove(profile.id);
            TopicLibraryStore.setScanProfiles(this, changed);
            refresh();
        });
        edit.setOnClickListener(v -> {
            TopicLibraryStore.setActive(this, profile.id);
            startActivity(new Intent(this, MapEditorActivity.class));
        });
        more.setOnClickListener(v -> showMapMenu(profile));

        return card;
    }

    private int countMapTerms(String raw){
        int total=0;
        for(String line:raw.split("\\r?\\n")){
            line=line.trim();if(!line.isEmpty()&&!line.startsWith("#"))total+=MapTermSyntax.decode(line).size();
        }
        return total;
    }

    private void showCreateDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(4), dp(18), 0);
        EditText name = new EditText(this);
        name.setHint("Numele hărții");
        name.setSingleLine(true);
        EditText folder = new EditText(this);
        folder.setHint("Folder");
        folder.setSingleLine(true);
        folder.setText("General");
        box.addView(name);
        box.addView(folder);
        final int[] chosenColor = { MapColorPolicy.defaultColor(java.util.UUID.randomUUID().toString()) };
        Button color = button("Culoare");
        paintColorButton(color, chosenColor[0]);
        color.setContentDescription("Alege culoarea hărții noi");
        box.addView(color, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        color.setOnClickListener(v -> ColorPickerDialog.show(this, chosenColor[0], selected -> {
            chosenColor[0] = selected;
            paintColorButton(color, selected);
        }));

        new AlertDialog.Builder(this)
                .setTitle("Hartă nouă")
                .setView(box)
                .setNegativeButton("Renunță", null)
                .setPositiveButton("Creează", (d, which) -> {
                    String id = TopicLibraryStore.create(
                            this,
                            name.getText().toString(),
                            folder.getText().toString(),
                            "", chosenColor[0]
                    );
                    TopicLibraryStore.setActive(this, id);
                    startActivity(new Intent(this, MapEditorActivity.class));
                })
                .show();
    }

    private void showMapMenu(MapProfile profile) {
        String[] options = {"Mută / redenumește", "Duplică", "Șterge"};
        new AlertDialog.Builder(this)
                .setTitle(profile.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showRenameDialog(profile);
                    else if (which == 1) {
                        String id = TopicLibraryStore.duplicate(this, profile.id);
                        if (id != null) TopicLibraryStore.setActive(this, id);
                        refresh();
                    } else if (which == 2) confirmDelete(profile);
                })
                .show();
    }

    private void showRenameDialog(MapProfile profile) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(4), dp(18), 0);
        EditText name = new EditText(this);
        name.setText(profile.name);
        name.setSingleLine(true);
        EditText folder = new EditText(this);
        folder.setText(profile.folder);
        folder.setSingleLine(true);
        box.addView(name);
        box.addView(folder);

        new AlertDialog.Builder(this)
                .setTitle("Mută / redenumește")
                .setView(box)
                .setNegativeButton("Renunță", null)
                .setPositiveButton("Salvează", (d, which) -> {
                    TopicLibraryStore.update(
                            this,
                            profile.id,
                            name.getText().toString(),
                            folder.getText().toString(),
                            profile.rawText
                    );
                    refresh();
                })
                .show();
    }

    private void confirmDelete(MapProfile profile) {
        new AlertDialog.Builder(this)
                .setTitle("Ștergi harta?")
                .setMessage("„" + profile.name + "” va fi ștearsă local. Trebuie să rămână cel puțin o hartă.")
                .setNegativeButton("Nu", null)
                .setPositiveButton("Șterge", (d, which) -> {
                    if (!TopicLibraryStore.delete(this, profile.id)) {
                        toast("Nu poți șterge singura hartă rămasă.");
                    }
                    refresh();
                })
                .show();
    }

    private Button colorButton(int color, String description) {
        Button button = button("●");
        button.setTextSize(18);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setContentDescription(description);
        paintColorButton(button, color);
        return button;
    }

    private void paintColorButton(Button button, int color) {
        button.setBackgroundTintList(ColorStateList.valueOf(color));
        button.setTextColor(MapColorPolicy.contrastText(color));
    }

    private TextView smallBadge(String text, boolean active) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(10);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setTextColor(active ? Color.WHITE : Color.rgb(62, 69, 76));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(active ? Color.rgb(53, 92, 125) : Color.rgb(236, 232, 222));
        bg.setCornerRadius(dp(14));
        view.setBackground(bg);
        return view;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(53, 92, 125)));
        return button;
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
        view.setTextSize(14);
        view.setTextColor(Color.rgb(62, 69, 76));
        return view;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
    }
}
