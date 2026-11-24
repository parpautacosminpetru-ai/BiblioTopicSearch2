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
        String filename=name+".traineddata";
        fromFile(filename);
        return filename;
    }
}
