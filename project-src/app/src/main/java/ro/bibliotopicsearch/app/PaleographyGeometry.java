package ro.bibliotopicsearch.app;

/** No fabricated character coordinates: the four OCR line corners define
 * a single display surface, even if the replacement has a different length. */
final class PaleographyGeometry {
    private PaleographyGeometry() {}
    static boolean valid(float[] q,float l,float t,float r,float b){
        if(q==null||q.length!=8||r<=l||b<=t)return false;
        double area=0;
        float tolerance=Math.max(3f,(b-t)*.18f);
        for(int i=0;i<4;i++){
            int j=(i+1)%4,k=(i+2)%4;
            float x=q[2*i],y=q[2*i+1];
            if(Float.isNaN(x)||Float.isInfinite(x)||Float.isNaN(y)||Float.isInfinite(y)||
                    x<l-tolerance||x>r+tolerance||y<t-tolerance||y>b+tolerance)return false;
            float dx=q[2*j]-x,dy=q[2*j+1]-y;
            float ex=q[2*k]-q[2*j],ey=q[2*k+1]-q[2*j+1];
            if(dx*dx+dy*dy<4f||dx*ey-dy*ex<=1f)return false;
            area+=x*q[2*j+1]-y*q[2*j];
        }
        float w=width(q),h=height(q);
        return w>=3f&&h>=3f&&area*.5>=Math.max(4f,w*h*.40f);
    }
    static float width(float[] q){return (distance(q,0,1)+distance(q,3,2))*.5f;}
    static float height(float[] q){return (distance(q,0,3)+distance(q,1,2))*.5f;}
    private static float distance(float[] q,int a,int b){return (float)Math.hypot(q[a*2]-q[b*2],q[a*2+1]-q[b*2+1]);}
}
