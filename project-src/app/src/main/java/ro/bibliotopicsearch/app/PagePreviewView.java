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
    interface OnMark {void onMark(int index);}
    private List<LocatedMark> located=Collections.emptyList();
    private OnMark markListener;
    private int pressedMark=-1;
    private final Paint imagePaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame=new RectF();
    private Bitmap bitmap;
    private List<MatchHit> hits=Collections.emptyList();
    private List<PaleographyLine> paleography=Collections.emptyList();
    private final PaleographyOverlayRenderer paleographyRenderer=new PaleographyOverlayRenderer();
    private PaleographyLine speaking;
    private LiteralTransliterator.RuleMatch spokenCue;
    private float[] quad={0,0,1,0,1,1,0,1};
    private boolean editing,regionMode,regionSelected,regionDragging;
    private float anchorX,anchorY;
    private int dragged=-1;
    private OnHits listener;

    PagePreviewView(Context context){super(context);setBackgroundColor(Color.rgb(21,27,35));}
    void setListener(OnHits listener){this.listener=listener;}
    void showLocated(Bitmap bitmap,List<LocatedMark> marks,OnMark listener){
        showResult(bitmap,Collections.emptyList());located=new ArrayList<>(marks);markListener=listener;invalidate();
    }
    private void resetLocated(){located=Collections.emptyList();markListener=null;pressedMark=-1;}
    void showOriginal(Bitmap bitmap,float[] corners){
        resetLocated();
        this.bitmap=bitmap;quad=corners.clone();editing=true;regionMode=false;regionSelected=false;hits=Collections.emptyList();paleography=Collections.emptyList();invalidate();
    }
    void selectRegion(Bitmap bitmap){
        showOriginal(bitmap,new float[]{0,0,1,0,1,1,0,1});regionMode=true;regionSelected=false;invalidate();
    }
    boolean hasRegionSelection(){return editing&&regionMode&&regionSelected;}
    void showResult(Bitmap bitmap,List<MatchHit> hits){
        resetLocated();this.bitmap=bitmap;this.hits=hits;editing=false;paleography=Collections.emptyList();invalidate();
    }
    void showPaleography(Bitmap bitmap,List<PaleographyLine> lines){
        resetLocated();this.bitmap=bitmap;hits=Collections.emptyList();paleography=lines;editing=false;invalidate();
    }
    void setSpeaking(PaleographyLine line,LiteralTransliterator.RuleMatch cue){
        speaking=line;spokenCue=cue;invalidate();
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
            float fineStroke=Math.min(stroke,Math.max(.5f,Math.min(box.width(),box.height())*.35f));
            if(palette.size()==1){
                ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(fineStroke);ink.setColor(color);
                canvas.drawRect(box,ink);ink.setStyle(Paint.Style.FILL);
            }else{
                float step=box.width()/palette.size(),left=box.left;
                for(int c:palette.values()){ink.setColor(c);canvas.drawRect(left,box.top,left+step,box.top+fineStroke,ink);
                    canvas.drawRect(left,box.bottom-fineStroke,left+step,box.bottom,ink);left+=step;}
            }
        }
        paleographyRenderer.draw(canvas,paleography,speaking,spokenCue);
        canvas.restore();
        // Draw labels in display coordinates so small boxes don't obscure original ink.
        if(!located.isEmpty()){
            int save=canvas.save();canvas.clipRect(frame);
            float sx=frame.width()/bitmap.getWidth(),sy=frame.height()/bitmap.getHeight();
            ink.setTextSize(dp(10));ink.setTypeface(android.graphics.Typeface.DEFAULT);
            for(LocatedMark mark:located){
                RectF q=new RectF(frame.left+mark.bounds.left*sx,frame.top+mark.bounds.top*sy,
                        frame.left+mark.bounds.right*sx,frame.top+mark.bounds.bottom*sy);
                ink.setColor(mark.confirmed?0xff31d49e:0xffffcb66);ink.setStrokeWidth(dp(1.4f));ink.setStyle(Paint.Style.STROKE);canvas.drawRect(q,ink);
                ink.setStyle(Paint.Style.FILL);
                // Only display a short label; full candidate or transcription appears on tap.
                String label=mark.label;int cp=label.codePointCount(0,label.length());
                if(cp>14)label=label.substring(0,label.offsetByCodePoints(0,14))+"…";
                float baseline=Math.max(frame.top+dp(10),q.top-dp(2)),right=Math.min(frame.right,q.left+ink.measureText(label)+dp(4));
                if(right-q.left>=dp(8)){
                    canvas.drawRect(q.left,baseline-dp(11),right,baseline+dp(2),ink);
                    ink.setColor(0xff17202b);canvas.drawText(label,q.left+dp(2),baseline,ink);
                }
            }
            canvas.restoreToCount(save);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!isEnabled()||!layoutFrame())return false;
        float x=event.getX(),y=event.getY();
        if(editing&&regionMode){
            int action=event.getActionMasked();
            float nx=Math.max(0,Math.min(1,(x-frame.left)/frame.width()));
            float ny=Math.max(0,Math.min(1,(y-frame.top)/frame.height()));
            if(action==MotionEvent.ACTION_DOWN){
                if(!frame.contains(x,y))return false;
                anchorX=nx;anchorY=ny;regionDragging=true;regionSelected=false;
                getParent().requestDisallowInterceptTouchEvent(true);return true;
            }
            if(regionDragging&&(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP)){
                float l=Math.min(nx,anchorX),r=Math.max(nx,anchorX),t=Math.min(ny,anchorY),b=Math.max(ny,anchorY);
                float[] next={l,t,r,t,r,b,l,b};
                if((r-l)*bitmap.getWidth()>=8&&(b-t)*bitmap.getHeight()>=8){quad=next;regionSelected=true;invalidate();}
                else regionSelected=false;
                if(action==MotionEvent.ACTION_UP){regionDragging=false;getParent().requestDisallowInterceptTouchEvent(false);performClick();}
                return true;
            }
            if(action==MotionEvent.ACTION_CANCEL){regionDragging=false;regionSelected=false;getParent().requestDisallowInterceptTouchEvent(false);}
            return true;
        }
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
        }else if(!located.isEmpty()&&markListener!=null){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_UP){
                float px=(x-frame.left)*bitmap.getWidth()/frame.width(),py=(y-frame.top)*bitmap.getHeight()/frame.height();
                float margin=dp(6)*bitmap.getWidth()/frame.width();int best=-1;double distance=Double.MAX_VALUE;
                for(int i=0;i<located.size();i++){
                    RectF q=located.get(i).bounds;
                    if(px<q.left-margin||px>q.right+margin||py<q.top-margin||py>q.bottom+margin)continue;
                    double d=Math.hypot(px-q.centerX(),py-q.centerY());if(d<distance){distance=d;best=i;}
                }
                if(action==MotionEvent.ACTION_DOWN){pressedMark=best;return true;}
                if(best>=0&&best==pressedMark){performClick();markListener.onMark(best);}pressedMark=-1;return true;
            }
            return true;
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
