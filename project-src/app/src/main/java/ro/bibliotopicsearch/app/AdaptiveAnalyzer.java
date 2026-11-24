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
    private long thermalReadAt = -10000L;
    private int thermal;

    AdaptiveAnalyzer(MlKitAnalyzer delegate, AdaptiveOcrPolicy policy, PowerManager power,
                     Active active, ErrorListener errorListener) {
        this.delegate = delegate;
        this.policy = policy;
        this.power = power;
        this.active = active;
        this.errorListener = errorListener;
    }

    @Override public int getTargetCoordinateSystem() { return delegate.getTargetCoordinateSystem(); }
    @Override public Size getDefaultTargetResolution() { return delegate.getDefaultTargetResolution(); }
    @Override public void updateTransform(@Nullable Matrix matrix) {
        delegate.updateTransform(matrix);
        transformAvailable = matrix != null;
    }

    @Override public void analyze(@NonNull ImageProxy image) {
        long now = SystemClock.elapsedRealtime();
        long timestamp = image.getImageInfo().getTimestamp();
        if (!active.isActive() || !transformAvailable) {
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
