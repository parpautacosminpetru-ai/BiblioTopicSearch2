package ro.bibliotopicsearch.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AtomicFile;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Local, isolated catalogs. A sample is never a certified transcription. */
final class GlyphLibrary {
    private static final int MAX_SAMPLES=2048;
    private GlyphLibrary(){}
    static GlyphShape shape(Bitmap input){
        double scale=Math.min(1,384d/Math.max(input.getWidth(),input.getHeight()));
        int w=Math.max(8,(int)Math.round(input.getWidth()*scale)),h=Math.max(8,(int)Math.round(input.getHeight()*scale));
        Bitmap bitmap=Bitmap.createScaledBitmap(input,w,h,true);
        try{
            int[] pixels=new int[w*h];bitmap.getPixels(pixels,0,w,0,0,w,h);
            for(int i=0;i<pixels.length;i++){int p=pixels[i];pixels[i]=(77*((p>>>16)&255)+150*((p>>>8)&255)+29*(p&255))>>>8;}
            return GlyphShape.extract(pixels,w,h);
        }finally{if(bitmap!=input)bitmap.recycle();}
    }
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"glyph-catalog-v1.json"));}
    private static JSONArray read(Context c)throws Exception{
        try{return new JSONArray(new String(file(c).readFully(),StandardCharsets.UTF_8));}
        catch(java.io.FileNotFoundException e){return new JSONArray();}
    }
    private static void write(Context c,JSONArray records)throws Exception{
        AtomicFile target=file(c);FileOutputStream out=null;
        try{out=target.startWrite();out.write(records.toString().getBytes(StandardCharsets.UTF_8));target.finishWrite(out);}
        catch(Exception e){if(out!=null)target.failWrite(out);throw e;}
    }
    static String validate(String s,String what){
        if(s==null||s.trim().isEmpty()||s.length()>128)throw new IllegalArgumentException(what+": 1–128 caractere.");
        LiteralTransliterator.validateUnicode(s);return s;
    }
    static synchronized void add(Context c,String system,String label,GlyphShape shape)throws Exception{
        validate(system,"Sistem");validate(label,"Semn");
        JSONArray a=read(c);if(a.length()>=MAX_SAMPLES)throw new IllegalArgumentException("Catalogul a atins limita de 2048 exemple.");
        JSONObject item=new JSONObject();item.put("system",system);item.put("label",label);item.put("aspect",shape.aspect);
        item.put("cells",Base64.encodeToString(shape.cells,Base64.NO_WRAP));a.put(item);write(c,a);
    }
    static synchronized List<GlyphShape.Example> examples(Context c,String system)throws Exception{
        validate(system,"Sistem");JSONArray a=read(c);List<GlyphShape.Example> list=new ArrayList<>();
        for(int i=0;i<a.length();i++){JSONObject item=a.getJSONObject(i);if(!system.equals(item.getString("system")))continue;
            list.add(new GlyphShape.Example(item.getString("label"),new GlyphShape(Base64.decode(item.getString("cells"),Base64.NO_WRAP),item.getDouble("aspect"))));
        }
        return list;
    }
    static synchronized List<String> labels(Context c,String system)throws Exception{
        java.util.LinkedHashSet<String> names=new java.util.LinkedHashSet<>();for(GlyphShape.Example e:examples(c,system))names.add(e.label);return new ArrayList<>(names);
    }
    static synchronized void remove(Context c,String system,String label)throws Exception{
        JSONArray a=read(c),out=new JSONArray();for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);if(!system.equals(x.getString("system"))||!label.equals(x.getString("label")))out.put(x);}write(c,out);
    }
}
