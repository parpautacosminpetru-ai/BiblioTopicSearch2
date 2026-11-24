package ro.bibliotopicsearch.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PagePreviewView extends View {
    interface OnHits {void onHits(List<MatchHit> hits);}
    private final Paint imagePaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame=new RectF();
    private Bitmap bitmap;
    private List<MatchHit> hits=Collections.emptyList();
    private float[] quad={0,0,1,0,1,1,0,1};
    private boolean editing;
    private int dragged=-1;
    private OnHits listener;

    PagePreviewView(Context context){super(context);setBackgroundColor(Color.rgb(21,27,35));}
    void setListener(OnHits listener){this.listener=listener;}
    void showOriginal(Bitmap bitmap,float[] corners){
        this.bitmap=bitmap;quad=corners.clone();editing=true;hits=Collections.emptyList();invalidate();
    }
    void showResult(Bitmap bitmap,List<MatchHit> hits){
        this.bitmap=bitmap;this.hits=hits;editing=false;invalidate();
    }
    float[] corners(){return quad.clone();}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    private boolean layoutFrame(){
        if(bitmap==null||bitmap.isRecycled()||getWidth()<=0||getHeight()<=0)return false;
        float inset=editing?dp(18):0;
        float scale=Math.min(Math.max(1,getWidth()-2*inset)/bitmap.getWidth(),
                Math.max(1,getHeight()-2*inset)/bitmap.getHeight());
        float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
        frame.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
        return true;
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(!layoutFrame())return;
        canvas.drawBitmap(bitmap,null,frame,imagePaint);
        if(editing){
            Path p=new Path();
            for(int i=0;i<4;i++){
                float x=frame.left+quad[i*2]*frame.width(),y=frame.top+quad[i*2+1]*frame.height();
                if(i==0)p.moveTo(x,y);else p.lineTo(x,y);
            }
            p.close();ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(dp(2));ink.setColor(0xff42e7c1);canvas.drawPath(p,ink);
            ink.setStyle(Paint.Style.FILL);
            for(int i=0;i<4;i++){
                float x=frame.left+quad[i*2]*frame.width(),y=frame.top+quad[i*2+1]*frame.height();
                ink.setColor(Color.BLACK);canvas.drawCircle(x,y,dp(10),ink);
                ink.setColor(0xff42e7c1);canvas.drawCircle(x,y,dp(7),ink);
            }
            return;
        }
        canvas.save();canvas.clipRect(frame);
        canvas.translate(frame.left,frame.top);canvas.scale(frame.width()/bitmap.getWidth(),frame.height()/bitmap.getHeight());
        Map<String,RectF> boxes=new LinkedHashMap<>();
        Map<String,Map<String,Integer>> colors=new LinkedHashMap<>();
        for(MatchHit h:hits)for(RectF rect:h.segments){
            String key=rect.left+":"+rect.top+":"+rect.right+":"+rect.bottom;
            boxes.putIfAbsent(key,rect);
            colors.computeIfAbsent(key,k->new java.util.TreeMap<>()).putIfAbsent(h.node.profileId,h.node.color);
        }
        float stroke=dp(2)*bitmap.getWidth()/frame.width();
        for(Map.Entry<String,RectF> entry:boxes.entrySet()){
            RectF box=entry.getValue();Map<String,Integer> palette=colors.get(entry.getKey());
            int color=palette.values().iterator().next();
            ink.setStyle(Paint.Style.FILL);ink.setColor((color&0xffffff)|0x30000000);canvas.drawRect(box,ink);
            if(palette.size()==1){
                ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(stroke);ink.setColor(color);
                canvas.drawRect(box,ink);ink.setStyle(Paint.Style.FILL);
            }else{
                float step=box.width()/palette.size(),left=box.left;
                for(int c:palette.values()){ink.setColor(c);canvas.drawRect(left,box.top,left+step,box.top+stroke,ink);
                    canvas.drawRect(left,box.bottom-stroke,left+step,box.bottom,ink);left+=step;}
            }
        }
        canvas.restore();
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!isEnabled()||!layoutFrame())return false;
        float x=event.getX(),y=event.getY();
        if(editing){
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
                float best=dp(34);
                for(int i=0;i<4;i++){
                    float distance=(float)Math.hypot(x-frame.left-quad[i*2]*frame.width(),y-frame.top-quad[i*2+1]*frame.height());
                    if(distance<best){best=distance;dragged=i;}
                }
                if(dragged>=0){getParent().requestDisallowInterceptTouchEvent(true);return true;}
                return false;
            }
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&dragged>=0){
                float[] next=quad.clone();
                next[dragged*2]=Math.max(0,Math.min(1,(x-frame.left)/frame.width()));
                next[dragged*2+1]=Math.max(0,Math.min(1,(y-frame.top)/frame.height()));
                if(PageCorrectionMath.validQuad(next)){quad=next;invalidate();}
                return true;
            }
            if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){
                dragged=-1;getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;
            }
        }else if(event.getActionMasked()==MotionEvent.ACTION_UP&&frame.contains(x,y)&&listener!=null){
            float px=(x-frame.left)*bitmap.getWidth()/frame.width(),py=(y-frame.top)*bitmap.getHeight()/frame.height();
            List<MatchHit> found=new ArrayList<>();
            for(MatchHit h:hits)for(RectF rect:h.segments)if(rect.contains(px,py)){found.add(h);break;}
            if(!found.isEmpty())listener.onHits(found);performClick();return true;
        }
        return !editing;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
