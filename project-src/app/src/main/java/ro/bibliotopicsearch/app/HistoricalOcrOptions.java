package ro.bibliotopicsearch.app;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import java.util.ArrayList;
import java.util.List;

/** User chooses scripts and layout. There is no inference of historical language. */
final class HistoricalOcrOptions {
    interface Choice{void selected(List<String> models,int mode);}
    private static final String[] LABELS={"Pagină automată","Text vertical","Bloc uniform","Rând","Cuvânt","Semn izolat","Text dispersat"};
    private static final int[] MODES={3,5,6,7,8,10,11};
    private HistoricalOcrOptions(){}
    static void choose(Context c,List<String> models,Choice result){
        if(models.isEmpty()){new AlertDialog.Builder(c).setTitle("Modele OCR").setMessage("Importă mai întâi unul sau mai multe modele .traineddata în Pagină / OCR istoric.").setPositiveButton("Închide",null).show();return;}
        boolean[] checked=new boolean[models.size()];checked[0]=true;
        new AlertDialog.Builder(c).setTitle("Sisteme de scriere (1–3 modele)")
            .setMultiChoiceItems(models.toArray(new String[0]),checked,(dialog,index,on)->{
                checked[index]=on;int n=0;for(boolean b:checked)if(b)n++;
                if(n>3){checked[index]=false;((AlertDialog)dialog).getListView().setItemChecked(index,false);}
            }).setNegativeButton("Renunță",null).setPositiveButton("Continuă",(d,w)->{
                List<String> selected=new ArrayList<>();for(int i=0;i<checked.length;i++)if(checked[i])selected.add(models.get(i));
                if(selected.isEmpty()){choose(c,models,result);return;}
                new AlertDialog.Builder(c).setTitle("Așezarea scrierii")
                    .setItems(LABELS,(dialog,which)->result.selected(selected,MODES[which]))
                    .setNegativeButton("Renunță",null).show();
            }).show();
    }
}
