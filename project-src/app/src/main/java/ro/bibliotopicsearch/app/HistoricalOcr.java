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
        Collections.sort(result);return result;
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
    static final class Session implements AutoCloseable {
        private final TessBaseAPI api;
        Session(Context c,List<String> names,int mode){
            String expression=HistoricalModelName.expression(names);
            for(String name:names){
                File file=new File(data(c),HistoricalModelName.file(name));
                if(!file.isFile())throw new IllegalArgumentException("Model lipsă: "+name);
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
        @Override public void close(){api.recycle();}
    }
}
