package ro.bibliotopicsearch.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/** A local, literal transcription workspace. Not coupled to semantic
 * dictionaries, the word matcher or OCR geometry. */
public final class PaleographyActivity extends AppCompatActivity {
    private static final int IMPORT_RULES=81,IMPORT_TEXT=82,SAVE_TEXT=83,SAVE_RULES=84,IMPORT_SOUND=85;
    private static final AtomicReference<Draft> DRAFT=new AtomicReference<>();
    private static final class Draft {
        final String text,origin;
        Draft(String text,String origin){this.text=text;this.origin=origin;}
    }
    static void open(Activity activity,String text,String origin){
        DRAFT.set(new Draft(text==null?"":text,origin));
        activity.startActivity(new Intent(activity,PaleographyActivity.class));
    }

    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Spinner systems;
    private EditText source;
    private TextView origin,status,output;
    private Button importRules,manage,importText,apply,copy,save,missing,live;
    private List<TransliterationStore.Profile> profiles=Collections.emptyList();
    private String selectedId="";
    private LiteralTransliterator.Result current;
    private String pendingExport;
    private String pendingClipSystem,pendingClipSource;
    private static final int MAX_PREVIEW=60000;
    private boolean busy,loading,internalInput;
    private long inputRevision;

    @Override protected void onCreate(@Nullable Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(12),dp(16),dp(24));root.setBackgroundColor(Color.rgb(246,248,251));
        scroll.addView(root);
        root.addView(label("Paleografie",24));
        LinearLayout systemBar=row();
        systems=new Spinner(this);
        systemBar.addView(systems,new LinearLayout.LayoutParams(0,dp(48),1));
        manage=button("Sisteme");systemBar.addView(manage,new LinearLayout.LayoutParams(dp(110),dp(48)));
        root.addView(systemBar);
        importRules=button("Importă sistem");
        root.addView(importRules,new LinearLayout.LayoutParams(-1,dp(48)));
        live=button("Pe cameră");
        root.addView(live,new LinearLayout.LayoutParams(-1,dp(48)));
        live.setOnClickListener(v->{
            if(busy||selectedId.isEmpty())return;
            TransliterationStore.select(this,selectedId);
            AppPrefs.setPaleographyLive(this,true);
            AppPrefs.setOcrEnabled(this,true);
            finish();
        });
        origin=label("Original",15);root.addView(origin);
        source=new EditText(this);
        source.setTextSize(18);
        source.setTextColor(Color.rgb(20,27,36));
        source.setHint("Text original");
        source.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        source.setGravity(Gravity.TOP|Gravity.START);source.setMinLines(4);source.setMaxLines(10);
        root.addView(source,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout textBar=row();
        importText=button("Importă text");
        apply=button("Transliterează");
        textBar.addView(importText,new LinearLayout.LayoutParams(0,dp(48),1));
        textBar.addView(apply,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(textBar);
        status=label("",14);root.addView(status);
        root.addView(label("Transliterare",16));
        output=label("",19);
        output.setTextIsSelectable(true);output.setMinHeight(dp(100));
        output.setPadding(dp(10),dp(12),dp(10),dp(12));output.setBackgroundColor(Color.WHITE);
        root.addView(output,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout resultBar=row();
        copy=button("Copiază");save=button("Exportă");missing=button("Neacoperite");
        for(Button b:new Button[]{copy,save,missing})resultBar.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(resultBar);
        importRules.setOnClickListener(v->pick(IMPORT_RULES));
        importText.setOnClickListener(v->pick(IMPORT_TEXT));
        manage.setOnClickListener(v->showManage());
        apply.setOnClickListener(v->transliterate());
        copy.setOnClickListener(v->{
            if(current==null)return;
            ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("Transliterare",current.text));
        });
        save.setOnClickListener(v->{if(current!=null)export(SAVE_TEXT,current.text,"Transliterare.txt");});
        missing.setOnClickListener(v->showMissing());
        source.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                inputRevision++;clearResult();
                if(!internalInput)origin.setText("Original");
            }
            @Override public void afterTextChanged(Editable e){}
        });
        systems.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(AdapterView<?> p){}
            @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id){
                if(loading||profiles.isEmpty()||position>=profiles.size())return;
                String next=profiles.get(position).id;
                if(!next.equals(selectedId)){
                    selectedId=next;TransliterationStore.select(PaleographyActivity.this,next);inputRevision++;clearResult();
                }
                updateButtons();
            }
        });
        setContentView(scroll);UiInsets.apply(scroll);
        reload(TransliterationStore.selected(this));
        Draft draft=DRAFT.getAndSet(null);
        if(draft!=null){
            setInput(draft.text,draft.origin==null?"Original":draft.origin);
        }
        if(profiles.isEmpty())status.setText("Importă sau creează un sistem.");
    }

    private void reload(String wanted){
        loading=true;profiles=TransliterationStore.list(this);
        List<String> labels=new ArrayList<>();
        for(TransliterationStore.Profile p:profiles)labels.add(p.toString());
        if(labels.isEmpty())labels.add("Niciun sistem");
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        systems.setAdapter(adapter);
        int selected=0;
        for(int i=0;i<profiles.size();i++)if(profiles.get(i).id.equals(wanted)){selected=i;break;}
        selectedId=profiles.isEmpty()?"":profiles.get(selected).id;
        systems.setSelection(selected);loading=false;
        TransliterationStore.select(this,selectedId);
        inputRevision++;clearResult();updateButtons();
    }
    private TransliterationStore.Profile selected(){
        for(TransliterationStore.Profile p:profiles)if(p.id.equals(selectedId))return p;
        return null;
    }
    private void setInput(String text,String label){
        if(text.length()>LiteralTransliterator.MAX_INPUT){status.setText("Text prea lung; importă un fragment mai mic.");return;}
        internalInput=true;source.setText(text);internalInput=false;origin.setText(label);
    }
    private void clearResult(){
        current=null;output.setText("");status.setText("");updateButtons();
    }
    private void updateButtons(){
        if(apply==null)return;
        systems.setEnabled(!busy&&!profiles.isEmpty());
        importRules.setEnabled(!busy);manage.setEnabled(!busy);importText.setEnabled(!busy);
        source.setEnabled(!busy);
        apply.setEnabled(!busy&&!selectedId.isEmpty());
        live.setEnabled(!busy&&!selectedId.isEmpty());
        copy.setEnabled(!busy&&current!=null);save.setEnabled(!busy&&current!=null);
        missing.setEnabled(!busy&&current!=null&&current.unmappedCharacters>0);
    }
    private void setBusy(boolean value){busy=value;updateButtons();}
    private boolean active(){return !isFinishing()&&!isDestroyed();}
    private void fail(Exception e){
        runOnUiThread(()->{if(active()){setBusy(false);status.setText(e.getMessage()==null?"Operațiune nereușită.":e.getMessage());}});
    }

    private void transliterate(){
        if(busy||selectedId.isEmpty())return;
        final String raw=source.getText().toString(),id=selectedId;final long revision=inputRevision;
        clearResult();setBusy(true);status.setText("Se aplică regulile…");
        worker.execute(()->{
            try{
                LiteralTransliterator.Result result=TransliterationStore.engine(this,id).apply(raw);
                runOnUiThread(()->{
                    if(!active())return;
                    setBusy(false);
                    if(revision!=inputRevision||!id.equals(selectedId))return;
                    current=result;
                    int end=Math.min(result.text.length(),MAX_PREVIEW);
                    if(end<result.text.length()&&end>0&&Character.isHighSurrogate(result.text.charAt(end-1)))end--;
                    output.setText(result.text.substring(0,end));
                    status.setText(result.substitutions+" substituții · "+result.unmappedNonWhitespace+
                            " caractere fără regulă (păstrate)"+(end<result.text.length()?" · Previzualizare parțială; export complet":""));
                    updateButtons();
                });
            }catch(Exception e){fail(e);}
        });
    }

    private void showMissing(){
        if(current==null)return;
        StringBuilder info=new StringBuilder();
        for(Map.Entry<Integer,Integer> e:current.unmapped.entrySet()){
            if(info.length()>0)info.append('\n');
            info.append(LiteralTransliterator.describe(e.getKey())).append(" × ").append(e.getValue());
        }
        if(current.otherUnmapped>0)info.append("\nAlte apariții: ").append(current.otherUnmapped);
        int whitespace=current.unmappedCharacters-current.unmappedNonWhitespace;
        if(whitespace>0)info.append("\nSpații/separatoare păstrate: ").append(whitespace);
        new AlertDialog.Builder(this).setTitle("Caractere fără regulă").setMessage(info.toString())
                .setPositiveButton("Închide",null).show();
    }

    private void showManage(){
        TransliterationStore.Profile profile=selected();
        String[] options=profile==null?new String[]{"Creează sistem","Formatul fișierului"}:
                new String[]{"Creează sistem","Editează sistemul","Exportă sistemul","Șterge sistemul","Formatul fișierului","Sunete înregistrate"};
        new AlertDialog.Builder(this).setTitle("Sisteme").setItems(options,(d,which)->{
            if(which==0){showEditor(null,"source\ttarget\n");return;}
            if(profile==null||which==4){showFormat();return;}
            if(which==5){showSoundEditor(profile);return;}
            if(which==1){
                setBusy(true);
                worker.execute(()->{try{
                    String table=TransliterationStore.table(this,profile.id);
                    runOnUiThread(()->{if(active()){setBusy(false);showEditor(profile,table);}});
                }catch(Exception e){fail(e);}});
            }else if(which==2){
                setBusy(true);
                worker.execute(()->{try{
                    String table=TransliterationStore.table(this,profile.id);
                    runOnUiThread(()->{if(active()){setBusy(false);export(SAVE_RULES,table,"Sistem.tsv");}});
                }catch(Exception e){fail(e);}});
            }else if(which==3){
                new AlertDialog.Builder(this).setTitle("Ștergi sistemul?").setMessage(profile.name)
                        .setNegativeButton("Renunță",null).setPositiveButton("Șterge",(dialog,w)->{
                            setBusy(true);worker.execute(()->{try{
                                TransliterationStore.delete(this,profile.id);
                                PaleographyAudioFiles.removeSystem(this,profile.id);
                                runOnUiThread(()->{if(active()){setBusy(false);reload("");}});
                            }catch(Exception e){fail(e);}});
                        }).show();
            }
        }).setNegativeButton("Închide",null).show();
    }

    private void showSoundEditor(TransliterationStore.Profile profile){
        EditText exact=new EditText(this);
        exact.setHint("Caracterul sau grupul-sursă exact");
        exact.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Sunet · "+profile.name)
                .setView(exact).setNegativeButton("Renunță",null)
                .setNeutralButton("Elimină",null).setPositiveButton("Atașează",null).create();
        d.setOnShowListener(v->{
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
                String source=exact.getText().toString();
                if(source.isEmpty()){exact.setError("Introdu sursa exactă.");return;}
                pendingClipSource=source;pendingClipSystem=profile.id;d.dismiss();
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("audio/*");
                startActivityForResult(intent,IMPORT_SOUND);
            });
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(w->{
                String source=exact.getText().toString();
                if(source.isEmpty()){exact.setError("Introdu sursa exactă.");return;}
                d.dismiss();setBusy(true);
                worker.execute(()->{try{
                    PaleographyAudioFiles.remove(this,profile.id,source);
                    runOnUiThread(()->{if(active()){setBusy(false);status.setText("Sunet eliminat.");}});
                }catch(Exception e){fail(e);}});
            });
        });
        d.show();
    }

    private void showFormat(){
        String message="UTF-8: TSV, CSV sau JSON.\n\n"
                +"TSV/CSV: source, target, sound (opțional), language (opțional).\n"
                +"Exemplu CSV: Ж,Zh,je,ro-RO\n"
                +"Sunetul scris se sintetizează numai cu o voce locală; limba implicită este ro-RO. Fără sunet definit nu se ghicește.\n"
                +"Sisteme → Sunete înregistrate: atașează un fișier audio scurt la sursa exactă. Înregistrarea are prioritate și este stocată separat de tabelul exportat.\n"
                +"JSON: {\"name\":\"Sistem\",\"rules\":[{\"source\":\"Ж\",\"target\":\"Zh\"}]}\n\n"
                +"Regulile sunt sensibile la majuscule. Caracterele neacoperite rămân neschimbate. "
                +"Pentru spațiu, TAB, rând nou ori simboluri speciale poți folosi JSON sau escapările TSV.";
        new AlertDialog.Builder(this).setTitle("Format sistem").setMessage(message).setPositiveButton("Închide",null).show();
    }
    private void showEditor(TransliterationStore.Profile profile,String table){
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),0,dp(16),dp(8));
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Numele sistemului");
        if(profile!=null)name.setText(profile.name);
        content.addView(name);
        EditText rules=new EditText(this);
        rules.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        rules.setGravity(Gravity.TOP|Gravity.START);rules.setTextSize(15);rules.setMinLines(6);rules.setMaxLines(12);
        rules.setText(table);content.addView(rules,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog editor=new AlertDialog.Builder(this).setTitle(profile==null?"Sistem nou":"Editează sistemul").setView(content)
                .setNeutralButton("Format",null)
                .setNegativeButton("Renunță",null).setPositiveButton("Salvează",null).create();
        editor.setOnShowListener(d->{
            editor.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->showFormat());
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                final String raw=rules.getText().toString(),title=name.getText().toString(),id=profile==null?null:profile.id;
                editor.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                setBusy(true);
                worker.execute(()->{
                    try{
                        TransliterationStore.Imported data=TransliterationStore.parse(raw,title);
                        String saved=TransliterationStore.save(this,id,data.name,data.rules);
                        runOnUiThread(()->{if(active()){editor.dismiss();setBusy(false);reload(saved);status.setText(data.rules.size()+" reguli salvate.");}});
                    }catch(Exception error){
                        runOnUiThread(()->{if(active()){setBusy(false);editor.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            rules.setError(error.getMessage()==null?"Sistem invalid.":error.getMessage());}});
                    }
                });
            });
        });
        editor.show();
    }

    private void importSystem(String raw,String name,String id){
        setBusy(true);status.setText("Se verifică sistemul…");
        worker.execute(()->{
            try{
                TransliterationStore.Imported data=TransliterationStore.parse(raw,name);
                String saved=TransliterationStore.save(this,id,data.name,data.rules);
                runOnUiThread(()->{if(active()){setBusy(false);reload(saved);status.setText(data.rules.size()+" reguli importate.");}});
            }catch(Exception e){fail(e);}
        });
    }
    private void pick(int request){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(request==IMPORT_RULES?"*/*":"text/*");
        startActivityForResult(intent,request);
    }
    private void export(int request,String raw,String filename){
        pendingExport=raw;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(request==SAVE_RULES?"text/tab-separated-values":"text/plain");
        intent.putExtra(Intent.EXTRA_TITLE,filename);
        startActivityForResult(intent,request);
    }
    @Override protected void onActivityResult(int request,int result,@Nullable Intent data){
        super.onActivityResult(request,result,data);
        if(request==IMPORT_SOUND){
            final String id=pendingClipSystem,source=pendingClipSource;
            pendingClipSystem=null;pendingClipSource=null;
            if(result!=RESULT_OK||data==null||data.getData()==null||id==null||source==null)return;
            final Uri uri=data.getData();setBusy(true);
            worker.execute(()->{try{
                PaleographyAudioFiles.attach(this,id,source,uri);
                runOnUiThread(()->{if(active()){setBusy(false);status.setText("Sunet atașat.");}});
            }catch(Exception e){fail(e);}});
            return;
        }
        if(request!=IMPORT_RULES&&request!=IMPORT_TEXT&&request!=SAVE_TEXT&&request!=SAVE_RULES)return;
        if(result!=RESULT_OK||data==null||data.getData()==null){pendingExport=null;return;}
        final Uri uri=data.getData();
        if(request==SAVE_TEXT||request==SAVE_RULES){
            String raw=pendingExport;pendingExport=null;if(raw==null)return;
            setBusy(true);worker.execute(()->{try(OutputStream stream=getContentResolver().openOutputStream(uri,"wt")){
                if(stream==null)throw new IllegalStateException("Fișier indisponibil.");
                stream.write(raw.getBytes(StandardCharsets.UTF_8));
                runOnUiThread(()->{if(active()){setBusy(false);status.setText("Exportat.");}});
            }catch(Exception e){fail(e);}});
            return;
        }
        setBusy(true);
        worker.execute(()->{
            try{
                String raw=readUtf8(uri,request==IMPORT_RULES?TransliterationTable.MAX_FILE_BYTES:800000);
                if(request==IMPORT_RULES){
                    TransliterationStore.Imported imported=TransliterationStore.parse(raw,name(uri));
                    String saved=TransliterationStore.save(this,null,imported.name,imported.rules);
                    runOnUiThread(()->{if(active()){setBusy(false);reload(saved);status.setText(imported.rules.size()+" reguli importate.");}});
                }else{
                    if(raw.length()>LiteralTransliterator.MAX_INPUT)throw new IllegalArgumentException("Text prea lung.");
                    runOnUiThread(()->{if(active()){setBusy(false);setInput(raw,"Text importat");}});
                }
            }catch(Exception e){fail(e);}
        });
    }
    private String name(Uri uri){
        try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){
                String n=cursor.getString(0);if(n!=null)return n.replaceFirst("\\.[^.]+$","");
            }
        }catch(RuntimeException ignored){}
        return "Sistem importat";
    }
    private String readUtf8(Uri uri,int max)throws Exception{
        try(InputStream stream=getContentResolver().openInputStream(uri);
                ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(stream==null)throw new IllegalArgumentException("Fișier indisponibil.");
            byte[] buffer=new byte[8192];int n;
            while((n=stream.read(buffer))!=-1){
                if(out.size()+n>max)throw new IllegalArgumentException("Fișier prea mare.");
                out.write(buffer,0,n);
            }
            try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(out.toByteArray())).toString();}
            catch(java.nio.charset.CharacterCodingException e){throw new IllegalArgumentException("Alege un fișier text UTF-8 (TSV, CSV sau JSON).");}
        }
    }
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private TextView label(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(25,34,45));t.setPadding(0,dp(5),0,dp(5));return t;}
    private Button button(String value){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(Color.WHITE);b.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(40,77,115)));return b;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
