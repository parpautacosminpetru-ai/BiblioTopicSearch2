package ro.bibliotopicsearch.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

/** Local image-only correction. OCR receives the exact bitmap rendered later. */
final class PageImageProcessor {
    private static final int MAX_EDGE=2200;
    private static final long MAX_PIXELS=3500000L;
    private PageImageProcessor() {}

    static Bitmap rectify(Bitmap original,float[] quad,int bow,boolean autoStraighten){
        return rectify(original,quad,bow,autoStraighten,64);
    }
    static Bitmap rectifyGlyph(Bitmap original,float[] quad){
        return rectify(original,quad,0,false,8);
    }
    private static Bitmap rectify(Bitmap original,float[] quad,int bow,boolean autoStraighten,int minimum){
        if(original==null||original.isRecycled()||!PageCorrectionMath.validRegion(quad))
            throw new IllegalArgumentException("Colțuri invalide");
        float[] p=quad.clone();int sw=original.getWidth(),sh=original.getHeight();
        for(int i=0;i<4;i++){p[i*2]*=sw-1;p[i*2+1]*=sh-1;}
        double width=Math.max(distance(p,0,1),distance(p,3,2));
        double height=Math.max(distance(p,0,3),distance(p,1,2));
        double scale=Math.min(1d,Math.min(MAX_EDGE/Math.max(width,height),Math.sqrt(MAX_PIXELS/(width*height))));
        int w=Math.max(2,(int)Math.round(width*scale)),h=Math.max(2,(int)Math.round(height*scale));
        if(w<minimum||h<minimum)throw new IllegalArgumentException("Zona selectată este prea mică; apropie camera sau folosește o imagine mai clară.");
        float[] target={0,0,w-1,0,w-1,h-1,0,h-1};
        Matrix matrix=new Matrix();
        if(!matrix.setPolyToPoly(p,0,target,0,4))throw new IllegalArgumentException("Perspectivă invalidă");
        Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(result);c.drawColor(Color.WHITE);
        c.drawBitmap(original,matrix,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
        if(bow!=0){
            Bitmap flat=warpBow(result,bow);result.recycle();result=flat;
        }
        if(autoStraighten){
            float angle=skew(result);
            if(Math.abs(angle)>=.75f){
                Bitmap flat=rotate(result,angle);result.recycle();result=flat;
            }
        }
        return result;
    }

    private static double distance(float[] p,int a,int b){
        return Math.hypot(p[a*2]-p[b*2],p[a*2+1]-p[b*2+1]);
    }

    private static Bitmap warpBow(Bitmap src,int strength){
        int w=src.getWidth(),h=src.getHeight();
        int[] input=new int[w*h],output=new int[w*h];
        src.getPixels(input,0,w,0,0,w,h);
        for(int x=0;x<w;x++){
            float shift=PageCorrectionMath.sourceY(0,x,w,h,strength);
            for(int y=0;y<h;y++){
                float sy=y+shift;int lo=(int)Math.floor(sy);
                if(lo<0||lo>=h){output[y*w+x]=Color.WHITE;continue;}
                int a=input[lo*w+x],b=input[Math.min(h-1,lo+1)*w+x];
                output[y*w+x]=mix(a,b,sy-lo);
            }
        }
        Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        result.setPixels(output,0,w,0,0,w,h);
        return result;
    }

    private static int mix(int a,int b,float f){
        int r=Math.round(((a>>16)&255)*(1-f)+((b>>16)&255)*f);
        int g=Math.round(((a>>8)&255)*(1-f)+((b>>8)&255)*f);
        int bl=Math.round((a&255)*(1-f)+(b&255)*f);
        return 0xff000000|(r<<16)|(g<<8)|bl;
    }

    private static float skew(Bitmap src){
        double scale=Math.min(1d,360d/Math.max(src.getWidth(),src.getHeight()));
        int w=Math.max(1,(int)Math.round(src.getWidth()*scale));
        int h=Math.max(1,(int)Math.round(src.getHeight()*scale));
        Bitmap small=Bitmap.createScaledBitmap(src,w,h,true);
        int[] p=new int[w*h];small.getPixels(p,0,w,0,0,w,h);
        if(small!=src)small.recycle();
        for(int i=0;i<p.length;i++)p[i]=PageCorrectionMath.luminance(p[i]);
        return PageCorrectionMath.estimateSkew(p,w,h);
    }

    private static Bitmap rotate(Bitmap src,float angle){
        int w=src.getWidth(),h=src.getHeight();
        // Same bounded size, white border. Scale enough to avoid clipping corners.
        double a=Math.toRadians(angle);
        float scale=(float)Math.min(w/(Math.abs(w*Math.cos(a))+Math.abs(h*Math.sin(a))),
                h/(Math.abs(w*Math.sin(a))+Math.abs(h*Math.cos(a))));
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);c.drawColor(Color.WHITE);
        Matrix m=new Matrix();m.setScale(scale,scale,w*.5f,h*.5f);m.postRotate(angle,w*.5f,h*.5f);
        c.drawBitmap(src,m,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    /** Estimate paper brightness by local upper percentiles; smoothly
     * interpolate between tiles to reduce gutter shadows and yellowing. */
    static Bitmap historicalContrast(Bitmap src,int strength){
        int w=src.getWidth(),h=src.getHeight();int[] pixels=new int[w*h];
        src.getPixels(pixels,0,w,0,0,w,h);
        for(int i=0;i<pixels.length;i++)pixels[i]=PageCorrectionMath.luminance(pixels[i]);
        int nx=Math.max(2,(w+127)/128),ny=Math.max(2,(h+127)/128);
        float[] paper=new float[nx*ny];
        for(int gy=0;gy<ny;gy++)for(int gx=0;gx<nx;gx++){
            int[] histogram=new int[256];int count=0;
            int x0=gx*w/nx,x1=(gx+1)*w/nx,y0=gy*h/ny,y1=(gy+1)*h/ny;
            for(int y=y0;y<y1;y+=3)for(int x=x0;x<x1;x+=3){histogram[pixels[y*w+x]]++;count++;}
            int threshold=Math.max(1,(int)(count*.85)),sum=0,v=255;
            for(int i=0;i<256;i++){sum+=histogram[i];if(sum>=threshold){v=i;break;}}
            paper[gy*nx+gx]=Math.max(70,v);
        }
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float gx=Math.max(0,Math.min(nx-1,(x+.5f)*nx/w-.5f));
            float gy=Math.max(0,Math.min(ny-1,(y+.5f)*ny/h-.5f));
            int ax=(int)gx,ay=(int)gy,bx=Math.min(nx-1,ax+1),by=Math.min(ny-1,ay+1);
            float fx=gx-ax,fy=gy-ay;
            float bg=(paper[ay*nx+ax]*(1-fx)+paper[ay*nx+bx]*fx)*(1-fy)
                    +(paper[by*nx+ax]*(1-fx)+paper[by*nx+bx]*fx)*fy;
            int v=PageCorrectionMath.enhance(pixels[y*w+x],bg,strength);
            pixels[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
        }
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        out.setPixels(pixels,0,w,0,0,w,h);
        return out;
    }
}
