package ro.bibliotopicsearch.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Evidențiere discretă, un termen o dată. Culoarea arată tipul informației. */
public final class AllMatchesView extends View {
    public interface OnHitTapListener {void onHitTap(MatchHit hit);}
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<MatchHit> hits=Collections.emptyList();
    private MultiThemeEngine.Scan scan;
    private String selectedCategory=CategoryIndex.ALL;
    private OnHitTapListener listener;
    private MatchHit pressed;

    public AllMatchesView(Context context){super(context);setWillNotDraw(false);}
    public void setOnHitTapListener(OnHitTapListener listener){this.listener=listener;}
    public void clearHits(){
        hits=Collections.emptyList();scan=null;pressed=null;invalidate();
    }
    public void setHits(List<MatchHit> list,MultiThemeEngine.Scan scan,String category){
        hits=list==null?Collections.emptyList():new ArrayList<>(list);
        this.scan=scan;
        this.selectedCategory=category==null?CategoryIndex.ALL:category;
        invalidate();
    }
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(hits.isEmpty())return;
        for(MatchHit hit:hits){
            RectF box=hit.box;
            if(box==null)continue;
            String id=scan==null?CategoryIndex.ALTE:
                    scan.displayCategory(hit,selectedCategory);
            int color=CategoryIndex.category(id).color;
            pen.setColor(color);
            pen.setStrokeWidth(dp(1.7f));
            pen.setStyle(Paint.Style.STROKE);
            float y=Math.min(getHeight()-dp(1),box.bottom+dp(1));
            canvas.drawLine(Math.max(0,box.left),y,
                    Math.min(getWidth(),box.right),y,pen);
            if(scan!=null&&scan.themeCount(hit)>1){
                pen.setStyle(Paint.Style.FILL);
                float cx=Math.min(getWidth()-dp(3),box.right+dp(2));
                canvas.drawCircle(cx,y,dp(2.3f),pen);
            }
        }
    }
    private MatchHit find(float x,float y){
        for(int i=hits.size()-1;i>=0;i--){
            MatchHit hit=hits.get(i);
            RectF rect=new RectF(hit.box);
            rect.inset(-dp(9),-dp(9));
            if(rect.contains(x,y))return hit;
        }
        return null;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
            pressed=find(event.getX(),event.getY());
            return pressed!=null;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_UP){
            MatchHit end=find(event.getX(),event.getY());
            if(pressed!=null&&end==pressed){
                performClick();
                if(listener!=null)listener.onHitTap(pressed);
            }
            pressed=null;
            return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){
            pressed=null;return true;
        }
        return pressed!=null;
    }
    @Override public boolean performClick(){
        super.performClick();return true;
    }
}
