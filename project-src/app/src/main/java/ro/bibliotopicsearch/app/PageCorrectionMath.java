package ro.bibliotopicsearch.app;

/** Bounded geometric and photometric corrections. All transforms preserve the
 * input pixels' provenance; no text synthesis or guessed glyphs. */
final class PageCorrectionMath {
    private PageCorrectionMath() {}

    static boolean validQuad(float[] q) {
        if(q==null||q.length!=8)return false;
        double area=0;
        for(int i=0;i<4;i++){
            int j=(i+1)%4,k=(i+2)%4;
            float x=q[i*2],y=q[i*2+1];
            if(Float.isNaN(x)||Float.isInfinite(x)||Float.isNaN(y)||Float.isInfinite(y)||x<0||x>1||y<0||y>1)return false;
            double ax=q[j*2]-x,ay=q[j*2+1]-y,bx=q[k*2]-q[j*2],by=q[k*2+1]-q[j*2+1];
            if(ax*ax+ay*ay<.0025||ax*by-ay*bx<.004)return false;
            area+=x*q[j*2+1]-y*q[j*2];
        }
        return area*.5>=.12;
    }

    /** Signed vertical bow. Positive raises a line sagging in the centre.
     * The edge positions are unchanged. */
    static float sourceY(float y,float x,int width,int height,int strength) {
        float t=2f*x/Math.max(1,width-1)-1f;
        float amplitude=Math.max(-100,Math.min(100,strength))*.0008f*height;
        return y+amplitude*(1f-t*t);
    }

    static int luminance(int rgb) {
        return (77*((rgb>>16)&255)+150*((rgb>>8)&255)+29*(rgb&255)+128)>>8;
    }

    /** Relative-to-local-paper contrast, without hard thresholding. */
    static int enhance(int gray,float paper,int strength) {
        float corrected=gray*245f/Math.max(70f,paper);
        float ink=255f-Math.min(255f,corrected);
        float s=Math.max(0,Math.min(100,strength))/100f;
        int value=Math.round(255f-Math.min(255f,ink*(1f+1.25f*s)));
        return Math.max(0,Math.min(255,value));
    }

    /** Estimate small skew by maximizing horizontal projection contrast.
     * A weak or ambiguous response is ignored. Input is a downscaled Y image. */
    static float estimateSkew(int[] gray,int w,int h) {
        if(gray==null||gray.length!=w*h||w<48||h<48)return 0f;
        int x0=w/12,x1=w-w/12,y0=h/10,y1=h-h/10;
        long sum=0;int n=0;
        for(int y=y0;y<y1;y+=2)for(int x=x0;x<x1;x+=2){sum+=gray[y*w+x];n++;}
        if(n==0)return 0f;
        float mean=sum/(float)n;
        int[] xs=new int[w*h],ys=new int[w*h],weights=new int[w*h];int count=0;
        for(int y=y0;y<y1;y+=2)for(int x=x0;x<x1;x+=2){
            int v=gray[y*w+x];int weight=Math.round(mean-v-12);
            if(weight>12){xs[count]=x;ys[count]=y;weights[count]=Math.min(100,weight);count++;}
        }
        if(count<80||count>n*.60)return 0f;
        double baseline=projection(xs,ys,weights,count,w,h,0),best=baseline;float angle=0;
        for(int k=-24;k<=24;k++){
            float candidate=k*.5f;
            if(k==0)continue;
            double score=projection(xs,ys,weights,count,w,h,candidate);
            if(score>best){best=score;angle=candidate;}
        }
        if(Math.abs(angle)>=12f||best<baseline*1.12||Math.abs(angle)<.75f)return 0f;
        // Return the corrective rotation, opposite to the measured text slope.
        return -angle;
    }

    private static double projection(int[] x,int[] y,int[] weights,int count,int w,int h,float degrees){
        double slope=Math.tan(Math.toRadians(degrees));
        double[] bins=new double[h+2*w+4];int offset=w+2;
        for(int i=0;i<count;i++){
            double p=y[i]-slope*(x[i]-w*.5)+offset;int lo=(int)Math.floor(p);
            if(lo<0||lo+1>=bins.length)continue;
            double f=p-lo;bins[lo]+=weights[i]*(1-f);bins[lo+1]+=weights[i]*f;
        }
        double score=0;
        for(int i=2;i<bins.length-2;i++){
            double smooth=(bins[i-1]+2*bins[i]+bins[i+1])*.25;
            score+=smooth*smooth;
        }
        return score;
    }
}
