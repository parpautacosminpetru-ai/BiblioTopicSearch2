package ro.bibliotopicsearch.app;

final class PaleographyAudioProgress {
    private int completed;
    private boolean finished;
    int start(int size){return Math.min(completed,Math.max(0,size));}
    void completeThrough(int exclusive){completed=Math.max(completed,exclusive);}
    boolean done(){return finished;}
    void finish(){finished=true;}
}
