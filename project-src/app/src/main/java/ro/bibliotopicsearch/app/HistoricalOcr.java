package ro.bibliotopicsearch.app;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.OpenableColumns;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Offline OCR for explicitly imported Tesseract models. No download or network.
 * Models are validated in an isolated directory before atomic publication.
 * Native instances are never shared between worker threads. */
final class HistoricalOcr {
    private static final long MAX_MODEL=96L*1024*1024, MAX_LIBRARY=256L*1024*1024;
    private HistoricalOcr(){}
    private static File root(Context c){return new File(c.getNoBackupFilesDir(),"historical_ocr");}
    private static File data(Context c){return new File(root(c),"tessdata");}
    private static void mkdir(File f)throws Exception{
        if(!f.isDirectory()&&!f.mkdirs())throw new java.io.IOException("Director OCR indisponibil.");
    }
    static synchronized List<String> models(Context c){
        List<String> result=new ArrayList<>();File[] list=data(c).listFiles();
        if(list!=null)for(File f:list)if(f.isFile()&&f.length()>0&&f.length()<=MAX_MODEL)
            try{result.add(HistoricalModelName.fromFile(f.getName()));}catch(IllegalArgumentException ignored){}
        try{
            String[] assets=c.getAssets().list("ocr_models");
            if(assets!=null)for(String filename:assets)try{
                String name=HistoricalModelName.fromFile(filename);
                if(!result.contains(name))result.add(name);
            }catch(IllegalArgumentException ignored){}
        }catch(java.io.IOException ignored){}
        Collections.sort(result);return result;
    }
    /** Only extract the selected packaged model. A user's existing model always wins. */
    private static synchronized void ensureModel(Context c,String name)throws java.io.IOException{
        File dir=data(c),target=new File(dir,HistoricalModelName.file(name));
        if(target.isFile()&&target.length()>0)return;
        if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException("Director OCR indisponibil.");
        File pending=File.createTempFile("bundled-",".tmp",dir);
        try{
            long n=0;
            try(InputStream in=c.getAssets().open("ocr_models/"+target.getName());FileOutputStream out=new FileOutputStream(pending)){
                byte[] buffer=new byte[65536];int count;
                while((count=in.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
                    n+=count;if(n>MAX_MODEL)throw new java.io.IOException("Model prea mare.");
                    out.write(buffer,0,count);
                }
                out.getFD().sync();
            }
            if(n==0)throw new java.io.IOException("Model OCR gol: "+name);
            long total=n;File[] existing=dir.listFiles();
            if(existing!=null)for(File file:existing)if(file.isFile()&&!file.equals(target)&&!file.equals(pending))total+=file.length();
            if(total>MAX_LIBRARY)throw new java.io.IOException("Biblioteca OCR depășește 256 MiB.");
            try{android.system.Os.rename(pending.getAbsolutePath(),target.getAbsolutePath());}
            catch(android.system.ErrnoException e){throw new java.io.IOException("Nu s-a putut instala modelul "+name,e);}
        }catch(java.io.FileNotFoundException e){throw new java.io.IOException("Importă modelul OCR "+name+".traineddata",e);}
        finally{pending.delete();}
    }
    private static String displayName(Context c,Uri uri){
        try(Cursor cursor=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst())return cursor.getString(0);
        }
        throw new IllegalArgumentException("Numele modelului nu este disponibil.");
    }
    static synchronized String install(Context c,Uri uri)throws Exception{
        String name=HistoricalModelName.fromFile(displayName(c,uri));
        String filename=HistoricalModelName.file(name);File dir=data(c);mkdir(dir);
        File stage=new File(root(c),"stage-"+UUID.randomUUID()), stageData=new File(stage,"tessdata");
        File pending=new File(stageData,filename),target=new File(dir,filename);
        try{
            mkdir(stageData);long size=0;
            try(InputStream in=c.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(pending)){
                if(in==null)throw new java.io.IOException("Model indisponibil.");
                byte[] buffer=new byte[65536];int n;
                while((n=in.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
                    size+=n;if(size>MAX_MODEL)throw new IllegalArgumentException("Modelul depășește 96 MiB.");
                    out.write(buffer,0,n);
                }
                out.getFD().sync();
            }
            if(size==0)throw new IllegalArgumentException("Model gol.");
            long total=size;File[] installed=dir.listFiles();
            if(installed!=null)for(File f:installed)if(f.isFile()&&!f.equals(target))total+=f.length();
            if(total>MAX_LIBRARY)throw new IllegalArgumentException("Biblioteca OCR depășește 256 MiB.");
            TessBaseAPI probe=new TessBaseAPI();
            try{if(!probe.init(stage.getAbsolutePath()+File.separator,name))throw new IllegalArgumentException("Model Tesseract incompatibil sau corupt.");}
            finally{probe.recycle();}
            if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
            android.system.Os.rename(pending.getAbsolutePath(),target.getAbsolutePath());
            return name;
        }finally{pending.delete();stageData.delete();stage.delete();}
    }
    static String recognize(Context c,String name,Bitmap bitmap){
        return recognize(c,Collections.singletonList(name),3,bitmap);
    }
    static String recognize(Context c,List<String> names,int mode,Bitmap bitmap){
        try(Session session=new Session(c,names,mode)){return session.read(bitmap);}
    }
    static final class Recognition {
        final String text;final List<LocatedMark> lines;
        Recognition(String text,List<LocatedMark> lines){this.text=text;this.lines=Collections.unmodifiableList(new ArrayList<>(lines));}
    }
    static Recognition locate(Context c,List<String> names,int mode,Bitmap bitmap){
        try(Session session=new Session(c,names,mode)){return session.readDetailed(bitmap);}
    }
    static final class Session implements AutoCloseable {
        private final TessBaseAPI api;
        Session(Context c,List<String> names,int mode){
            String expression=HistoricalModelName.expression(names);
            for(String name:names){
                try{ensureModel(c,name);}
                catch(java.io.IOException e){throw new IllegalArgumentException(e.getMessage(),e);}
            }
            if(mode!=3&&mode!=5&&mode!=6&&mode!=7&&mode!=8&&mode!=10&&mode!=11)
                throw new IllegalArgumentException("Mod de segmentare invalid.");
            api=new TessBaseAPI();
            boolean ready=false;
            try{
                if(!api.init(root(c).getAbsolutePath()+File.separator,expression))throw new IllegalArgumentException("Modele OCR incompatibile.");
                api.setPageSegMode(mode);ready=true;
            }finally{if(!ready)api.recycle();}
        }
        String read(Bitmap bitmap){
            if(bitmap==null||bitmap.isRecycled())throw new IllegalArgumentException("Imagine indisponibilă.");
            try{api.setImage(bitmap);String value=api.getUTF8Text();return value==null?"":value;}
            finally{api.clear();}
        }
        Recognition readDetailed(Bitmap bitmap){
            if(bitmap==null||bitmap.isRecycled())throw new IllegalArgumentException("Imagine indisponibilă.");
            List<LocatedMark> marks=new ArrayList<>();
            com.googlecode.tesseract.android.ResultIterator it=null;
            try{
                api.setImage(bitmap);String value=api.getUTF8Text();if(value==null)value="";
                if(!value.trim().isEmpty()){
                    it=api.getResultIterator();
                    if(it!=null){
                        int level=TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE;it.begin();
                        do{
                            String line=it.getUTF8Text(level);android.graphics.Rect box=it.getBoundingRect(level);
                            float score=it.confidence(level);
                            if(line!=null&&!line.trim().isEmpty()&&box!=null&&box.intersect(0,0,bitmap.getWidth(),bitmap.getHeight())){
                                String label=Float.isNaN(score)?"OCR":String.format(java.util.Locale.ROOT,"OCR %.0f",score);
                                marks.add(new LocatedMark(new android.graphics.RectF(box),label,line.trim(),false));
                            }
                        }while(marks.size()<400&&it.next(level));
                    }
                }
                return new Recognition(value,marks);
            }finally{try{if(it!=null)it.delete();}finally{api.clear();}}
        }
        @Override public void close(){api.recycle();}
    }
}
