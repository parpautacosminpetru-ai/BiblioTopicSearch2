package ro.bibliotopicsearch.app;

/** Geometry along the OCR line baseline instead of screen X/Y. Keeps
 * neighbouring columns and rows separate even for a tilted page. */
final class LineGeometry {
    final float ux,uy,vx,vy,height;
    final float left,right,top,bottom;
    private LineGeometry(float ux,float uy,float left,float right,float top,float bottom){
        this.ux=ux;this.uy=uy;vx=-uy;vy=ux;
        this.left=left;this.right=right;this.top=top;this.bottom=bottom;height=bottom-top;
    }
    static LineGeometry from(float[] p){
        if(p==null||p.length!=8)return null;
        for(float value:p)if(Float.isNaN(value)||Float.isInfinite(value))return null;
        float dx=(p[2]-p[0]+p[4]-p[6])*.5f,dy=(p[3]-p[1]+p[5]-p[7])*.5f;
        float length=(float)Math.hypot(dx,dy);
        if(length<2)return null;
        float ux=dx/length,uy=dy/length;
        // Preserve direction and reject upside-down or near-vertical lines.
        if(ux<.65f)return null;
        float l=Float.MAX_VALUE,r=-Float.MAX_VALUE,t=Float.MAX_VALUE,b=-Float.MAX_VALUE;
        for(int i=0;i<4;i++){
            float x=p[i*2],y=p[i*2+1],u=x*ux+y*uy,v=-x*uy+y*ux;
            l=Math.min(l,u);r=Math.max(r,u);t=Math.min(t,v);b=Math.max(b,v);
        }
        if(r-l<2||b-t<2)return null;
        return new LineGeometry(ux,uy,l,r,t,b);
    }

    boolean consecutive(float ax,float ay,float bx,float by,float firstWidth,float secondWidth){
        float du=(bx-ax)*ux+(by-ay)*uy,dv=(bx-ax)*vx+(by-ay)*vy;
        float gap=du-(firstWidth+secondWidth)*.5f;
        return du>0&&gap<=height*2.5f&&Math.abs(dv)<=height*.95f;
    }

    boolean nextLine(LineGeometry next,float[] points){
        if(next==null||points==null||points.length!=8)return false;
        if(ux*next.ux+uy*next.uy<.978f)return false;
        float l=Float.MAX_VALUE,r=-Float.MAX_VALUE,t=Float.MAX_VALUE,b=-Float.MAX_VALUE;
        for(int i=0;i<4;i++){
            float x=points[i*2],y=points[i*2+1],u=x*ux+y*uy,v=x*vx+y*vy;
            l=Math.min(l,u);r=Math.max(r,u);t=Math.min(t,v);b=Math.max(b,v);
        }
        float h=Math.max(height,b-t);
        float gap=t-bottom;
        float overlap=Math.min(right,r)-Math.max(left,l);
        return t>top&&gap>=-h/3&&gap<=h*1.25f&&overlap>=Math.min(right-left,r-l)*.5f&&Math.abs(l-left)<=h*1.6f;
    }
}
