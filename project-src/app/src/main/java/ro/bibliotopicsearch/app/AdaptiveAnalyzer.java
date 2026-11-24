package ro.bibliotopicsearch.app;

import android.graphics.Matrix;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Size;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.mlkit.vision.MlKitAnalyzer;


/** Skip overloaded frames before invoking ML Kit, without losing CameraX's
 * view-referenced transform. A skipped image is always closed. */
final class AdaptiveAnalyzer implements ImageAnalysis.Analyzer {
    interface Active { boolean isActive(); }
    interface ErrorListener { void failed(long timestamp, RuntimeException error); }
    private final MlKitAnalyzer delegate;
    private final AdaptiveOcrPolicy policy;
    private final PowerManager power;
    private final Active active;
    private final ErrorListener errorListener;
    private volatile boolean transformAvailable;
    private Matrix previousTransform;
    private final Runnable transformChanged;
    private final PageMotionGate motion = new PageMotionGate();
    private long thermalReadAt = -10000L;
    private int thermal;

    AdaptiveAnalyzer(MlKitAnalyzer delegate, AdaptiveOcrPolicy policy, PowerManager power,
                     Active active, ErrorListener errorListener, Runnable transformChanged) {
        this.delegate = delegate;
        this.policy = policy;
        this.power = power;
        this.active = active;
        this.errorListener = errorListener;
        this.transformChanged = transformChanged;
    }

    @Override public int getTargetCoordinateSystem() { return delegate.getTargetCoordinateSystem(); }
    @Override public Size getDefaultTargetResolution() { return delegate.getDefaultTargetResolution(); }
    @Override public synchronized void updateTransform(@Nullable Matrix matrix) {
        boolean changed = matrix == null ? previousTransform != null : !matrix.equals(previousTransform);
        if (changed) {
            transformAvailable = false;
            motion.reset();
            transformChanged.run();
        }
        delegate.updateTransform(matrix);
        previousTransform = matrix == null ? null : new Matrix(matrix);
        transformAvailable = matrix != null;
    }

    /** Small, bounded sample of the original Y plane. No full bitmap or
     * additional recognizer is allocated on the camera thread. */
    private static int[] sampleLuma(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes=image.getPlanes();
            if(planes.length==0)return null;
            java.nio.ByteBuffer buffer=planes[0].getBuffer().duplicate();
            int base=buffer.position(), row=planes[0].getRowStride(), pixel=planes[0].getPixelStride();
            android.graphics.Rect crop=image.getCropRect();
            int width=crop.width(),height=crop.height(),nx=40,ny=32;
            if(width<nx||height<ny||row<=0||pixel<=0)return null;
            int[] sample=new int[nx*ny];
            for(int y=0;y<ny;y++)for(int x=0;x<nx;x++){
                int sum=0,count=0;
                for(int dy=0;dy<3;dy++)for(int dx=0;dx<3;dx++){
                    int px=crop.left+(int)((x+(dx+0.5)/3.0)*width/nx);
                    int py=crop.top+(int)((y+(dy+0.5)/3.0)*height/ny);
                    long index=(long)base+(long)py*row+(long)px*pixel;
                    if(index<base||index>=buffer.limit())continue;
                    sum+=buffer.get((int)index)&255;count++;
                }
                if(count==0)return null;
                sample[y*nx+x]=sum/count;
            }
            return sample;
        } catch(RuntimeException unavailable) {
            // Text/geometry verification still runs if this device cannot
            // expose a safely sampled luminance plane.
            return null;
        }
    }

    @Override public void analyze(@NonNull ImageProxy image) {
        long now = SystemClock.elapsedRealtime();
        long timestamp = image.getImageInfo().getTimestamp();
        if (!active.isActive() || !transformAvailable) {
            image.close();
            return;
        }
        if (motion.shouldSample(now) && motion.changed(sampleLuma(image))) {
            // Invalidate already submitted and displayed frames before using
            // the next stable image. Every rejected image is closed.
            transformChanged.run();
            image.close();
            return;
        }
        if (now - thermalReadAt >= 1000L) {
            thermalReadAt = now;
            if (Build.VERSION.SDK_INT >= 29 && power != null) {
                try { thermal = power.getCurrentThermalStatus(); }
                catch (RuntimeException ignored) { thermal = 0; }
            }
        }
        if (!policy.tryStart(timestamp, now, thermal)) {
            image.close();
            return;
        }
        try {
            delegate.analyze(image);
        } catch (RuntimeException error) {
            image.close();
            errorListener.failed(timestamp, error);
        }
    }
}
