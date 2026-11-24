package ro.bibliotopicsearch.app;

/** A model is an explicit local file, never a path or a Tesseract language expression. */
final class HistoricalModelName {
    private HistoricalModelName(){}
    static String fromFile(String filename){
        if(filename==null||!filename.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,39}\\.traineddata"))
            throw new IllegalArgumentException("Alege un fișier .traineddata cu nume simplu (ex. Cyrillic.traineddata sau rus.traineddata).");
        return filename.substring(0,filename.length()-".traineddata".length());
    }
    static String file(String name){
        if(name==null)throw new IllegalArgumentException("Model lipsă.");
        String filename=name+".traineddata";
        fromFile(filename);
        return filename;
    }
    static String expression(java.util.List<String> names){
        if(names==null||names.isEmpty()||names.size()>3)throw new IllegalArgumentException("Selectează între 1 și 3 modele OCR.");
        java.util.LinkedHashSet<String> unique=new java.util.LinkedHashSet<>();
        for(String name:names){file(name);if(!unique.add(name))throw new IllegalArgumentException("Model duplicat.");}
        return androidFreeJoin(unique);
    }
    private static String androidFreeJoin(java.util.Set<String> names){
        StringBuilder out=new StringBuilder();for(String name:names){if(out.length()>0)out.append('+');out.append(name);}return out.toString();
    }
}
