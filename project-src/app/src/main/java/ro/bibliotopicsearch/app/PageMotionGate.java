package ro.bibliotopicsearch.app;

/** Compare small luminance thumbnails. Brightness changes alone should not
 * move a highlight. A change of actual page texture invalidates old frames.
 * This heuristic is deliberately independent of recognized text. */
final class PageMotionGate {
    private int[] reference;
    private long lastCheck=Long.MIN_VALUE;

    synchronized void reset() { reference=null;lastCheck=Long.MIN_VALUE; }

    synchronized boolean shouldSample(long now) {
        if(lastCheck!=Long.MIN_VALUE && now-lastCheck<110L)return false;
        lastCheck=now;return true;
    }

    synchronized boolean changed(int[] sample) {
        if(sample==null||sample.length<32)return false;
        if(reference==null||reference.length!=sample.length){reference=sample.clone();return false;}
        double aMean=0,bMean=0;
        for(int i=0;i<sample.length;i++){aMean+=reference[i];bMean+=sample[i];}
        aMean/=sample.length;bMean/=sample.length;
        double aTexture=0,bTexture=0;
        for(int i=0;i<sample.length;i++){
            aTexture+=Math.abs(reference[i]-aMean);
            bTexture+=Math.abs(sample[i]-bMean);
        }
        aTexture/=sample.length;bTexture/=sample.length;
        // Compensate for ordinary exposure changes, not disappearance of text.
        double gain=Math.max(.65,Math.min(1.55,aTexture/Math.max(1,bTexture)));
        double difference=0;
        for(int i=0;i<sample.length;i++)
            difference+=Math.abs((reference[i]-aMean)-(sample[i]-bMean)*gain);
        difference/=sample.length;
        boolean moved=difference>6.5 && difference/Math.max(12,(aTexture+bTexture)/2)>0.48;
        if(moved)reference=sample.clone();
        return moved;
    }
}
