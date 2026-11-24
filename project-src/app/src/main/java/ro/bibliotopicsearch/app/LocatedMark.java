package ro.bibliotopicsearch.app;
import android.graphics.RectF;

/** A mark in the pixel coordinates of ONE particular bitmap. */
final class LocatedMark {
    final RectF bounds;final String label,detail;final boolean confirmed;
    LocatedMark(RectF box,String label,String detail,boolean confirmed){this.bounds=new RectF(box);this.label=label;this.detail=detail;this.confirmed=confirmed;}
}
