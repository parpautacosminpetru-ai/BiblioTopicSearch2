package ro.bibliotopicsearch.app;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Optional actual recordings, keyed by system and exact rule source. */
final class PaleographyAudioFiles {
    private static final long MAX_CLIP=2L*1024*1024,MAX_TOTAL=64L*1024*1024;
    private static final int MAX_CLIPS=512,MAX_DURATION_MS=10000;
    private PaleographyAudioFiles(){}
    private static File root(Context c){return new File(c.getFilesDir(),"paleography_sounds");}
    private static File directory(Context c,String id){
        if(id==null||!id.matches("[0-9a-fA-F-]{36}"))throw new IllegalArgumentException("Sistem invalid.");
        return new File(root(c),id);
    }
    private static String hash(String source){
        try{
            byte[] data=MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder(64);
            for(byte value:data){int v=value&255;out.append("0123456789abcdef".charAt(v>>>4));out.append("0123456789abcdef".charAt(v&15));}
            return out.toString();
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static File path(Context c,String id,String source){return new File(directory(c,id),hash(source)+".audio");}
    static Map<String,File> available(Context c,String id,LiteralTransliterator engine){
        if(id==null||id.isEmpty()||engine==null)return Collections.emptyMap();
        File dir=directory(c,id);String[] files=dir.list();
        if(files==null||files.length==0)return Collections.emptyMap();
        java.util.Set<String> names=new java.util.HashSet<>(java.util.Arrays.asList(files));
        Map<String,File> out=new HashMap<>();
        for(LiteralTransliterator.Rule rule:engine.rules()){
            String name=hash(rule.source)+".audio";
            if(names.contains(name))out.put(rule.source,new File(dir,name));
        }
        return Collections.unmodifiableMap(out);
    }
    private static long bytes(File file){
        if(file.isFile())return file.length();
        File[] children=file.listFiles();long size=0;
        if(children!=null)for(File child:children)size+=bytes(child);
        return size;
    }
    static synchronized void attach(Context c,String id,String source,Uri uri)throws Exception{
        LiteralTransliterator engine=TransliterationStore.engine(c,id);
        boolean present=false;
        for(LiteralTransliterator.Rule rule:engine.rules())if(rule.source.equals(source)){present=true;break;}
        if(!present)throw new IllegalArgumentException("Caracterul/grupul nu există ca sursă în sistem.");
        File dir=directory(c,id);
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("Director audio indisponibil.");
        File target=path(c,id,source);
        String[] names=dir.list((d,name)->name.endsWith(".audio"));
        if(!target.isFile()&&names!=null&&names.length>=MAX_CLIPS)throw new IllegalArgumentException("Maximum 512 înregistrări per sistem.");
        File temp=File.createTempFile("clip-",".tmp",dir);
        try{
            try(InputStream stream=c.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(temp)){
                if(stream==null)throw new IllegalArgumentException("Fișier audio indisponibil.");
                byte[] buffer=new byte[8192];long size=0;int n;
                while((n=stream.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
                    size+=n;if(size>MAX_CLIP)throw new IllegalArgumentException("Sunetul depășește 2 MiB.");
                    out.write(buffer,0,n);
                }
                out.getFD().sync();
            }
            MediaMetadataRetriever info=new MediaMetadataRetriever();
            try{
                info.setDataSource(temp.getAbsolutePath());
                String duration=info.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                long ms=duration==null?0:Long.parseLong(duration);
                if(ms<=0||ms>MAX_DURATION_MS||"yes".equals(info.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                        ||!"yes".equals(info.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)))
                    throw new IllegalArgumentException("Alege o înregistrare audio de maximum 10 secunde.");
            }finally{info.release();}
            if(bytes(root(c))-target.length()>MAX_TOTAL)throw new IllegalArgumentException("Biblioteca audio depășește 64 MiB.");
            if(!temp.renameTo(target))throw new java.io.IOException("Sunetul nu a putut fi salvat.");
        }finally{if(temp.exists())temp.delete();}
    }
    static synchronized void remove(Context c,String id,String source){File f=path(c,id,source);if(f.exists()&&!f.delete())throw new IllegalStateException("Sunetul nu a putut fi eliminat.");}
    static synchronized void removeSystem(Context c,String id){
        File dir=directory(c,id);File[] children=dir.listFiles();
        if(children!=null)for(File f:children)f.delete();
        dir.delete();
    }
}
