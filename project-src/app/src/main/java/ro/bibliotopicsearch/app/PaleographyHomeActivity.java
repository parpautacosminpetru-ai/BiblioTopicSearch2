package ro.bibliotopicsearch.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/** A camera-independent launcher; no permission or transliteration prerequisite. */
public final class PaleographyHomeActivity extends AppCompatActivity{
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(16),dp(16),dp(16));root.setBackgroundColor(Color.rgb(246,248,251));
        scroll.addView(root);
        TextView title=new TextView(this);title.setText("Biblio Paleografie");title.setTextSize(26);root.addView(title);
        TextView subtitle=new TextView(this);subtitle.setText("1.5.0 · OCR local · segmentare vizuală");subtitle.setTextSize(16);root.addView(subtitle);
        add(root,"CAMERĂ OCR",()->startActivity(new Intent(this,LiveHistoricalActivity.class)));
        add(root,"DESCHIDE IMAGINE",()->startActivity(new Intent(this,PageReviewActivity.class).putExtra(PageReviewActivity.OPEN_IMAGE,true)));
        add(root,"MODELE ȘI CATALOG DE SEMNE",()->startActivity(new Intent(this,PageReviewActivity.class).putExtra(PageReviewActivity.OPEN_MODELS,true)));
        add(root,"TEXT ȘI REGULI",()->PaleographyActivity.open(this,"","Original"));
        add(root,"CĂUTARE TEMATICĂ BIBLIO",()->startActivity(new Intent(this,MainActivity.class)));
        TextView note=new TextView(this);note.setText("Alege scrierea în camera OCR. Modelele de bază sunt incluse; scrierile rare se pot adăuga. Niciun model nu certifică automat lectura unui manuscris.");note.setTextSize(15);root.addView(note);
        setContentView(scroll);UiInsets.apply(scroll);
    }
    private void add(LinearLayout root,String name,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(name);b.setTextSize(16);b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(40,77,115)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(62));p.topMargin=dp(9);p.bottomMargin=dp(4);
        root.addView(b,p);b.setOnClickListener(v->action.run());
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
