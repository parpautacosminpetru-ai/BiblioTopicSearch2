package ro.bibliotopicsearch.app;

import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

final class UiInsets {
    private UiInsets() {}
    static void apply(View root) {
        int left = root.getPaddingLeft(), top = root.getPaddingTop();
        int right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return insets;
        });
        if(root.getContext() instanceof android.app.Activity){
            android.app.Activity activity=(android.app.Activity)root.getContext();
            androidx.core.view.WindowInsetsControllerCompat controller=androidx.core.view.WindowCompat.getInsetsController(activity.getWindow(),root);
            controller.setAppearanceLightStatusBars(true);
        }
        ViewCompat.requestApplyInsets(root);
    }
}
