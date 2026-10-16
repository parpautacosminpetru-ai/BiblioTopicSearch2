package ro.bibliotopicsearch.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.CameraController;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Archive a real camera JPEG rather than a cropped live preview or an OCR
 * reconstruction. OCR is computed from an in-memory upright display COPY.
 */
public final class CameraScanActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION = 642;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LifecycleCameraController controller;
    private TextRecognizer recognizer;
    private EditText pageInput;
    private TextView status;
    private Button capture;
    private String sourceId;
    private boolean stopped;
    private boolean busy;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        sourceId = IndexCoreSourceRegistry.activeSourceId(this);
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        buildUi();
        IndexCoreDatabase.SourceRecord source = IndexCoreSourceRegistry.activeSource(this);
        if (source == null || source.title.trim().isEmpty()) {
            status.setText("Definește mai întâi sursa și titlul ei din ecranul SURSĂ.");
            capture.setEnabled(false);
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) openCamera();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(9, 15, 20));
        root.setPadding(dp(10), dp(9), dp(10), dp(10));

        TextView title = new TextView(this);
        title.setText("SCANARE ORIGINALĂ • FOTOGRAFIA NU SE MODIFICĂ");
        title.setTextColor(Color.rgb(174, 229, 193));
        title.setTextSize(14);
        root.addView(title);

        PreviewView preview = new PreviewView(this);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        pageInput = new EditText(this);
        pageInput.setHint("Numărul paginii");
        pageInput.setSingleLine(true);
        pageInput.setTextColor(Color.WHITE);
        pageInput.setHintTextColor(Color.LTGRAY);
        pageInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        controls.addView(pageInput, new LinearLayout.LayoutParams(0, dp(52), 1f));
        capture = new Button(this);
        capture.setText("FOTOGRAFIAZĂ");
        capture.setAllCaps(false);
        capture.setOnClickListener(v -> capturePage());
        controls.addView(capture, new LinearLayout.LayoutParams(0, dp(52), 1.2f));
        root.addView(controls);

        status = new TextView(this);
        status.setText("JPEG original • OCR separat • verificare umană înainte de încheiere.");
        status.setTextColor(Color.WHITE);
        status.setTextSize(12);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        Button viewer = new Button(this);
        viewer.setAllCaps(false);
        viewer.setText("DESCHIDE ORIGINALUL / VERIFICĂ OCR");
        viewer.setOnClickListener(v -> {
            Intent intent = new Intent(this, OriginalPageActivity.class);
            intent.putExtra(OriginalPageActivity.EXTRA_SOURCE_ID, sourceId);
            intent.putExtra(OriginalPageActivity.EXTRA_PAGE, pageInput.getText().toString().trim());
            startActivity(intent);
        });
        root.addView(viewer);

        controller = new LifecycleCameraController(this);
        controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE);
        preview.setController(controller);
        setContentView(root);
    }

    private void openCamera() {
        if (!stopped && controller != null) controller.bindToLifecycle(this);
    }

    private void capturePage() {
        if (busy || controller == null) return;
        String page = pageInput.getText().toString().trim();
        if (page.isEmpty()) {
            status.setText("Introdu pagina înainte să fotografiezi, pentru proveniență sigură.");
            return;
        }
        if (SourceArchiveStore.page(this, sourceId, page) != null) {
            status.setText("Pagina are deja un original păstrat. Nu îl suprascriu.");
            return;
        }
        busy = true;
        capture.setEnabled(false);
        status.setText("Fotografiez și păstrez JPEG-ul original…");
        File temp;
        try {
            temp = File.createTempFile("camera-original-", ".jpg", getCacheDir());
        } catch (Exception failure) {
            finishCapture("Spațiu insuficient pentru fotografiere.");
            return;
        }

        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(temp).build();
        controller.takePicture(options, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        worker.execute(() -> processPicture(page, temp));
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException failure) {
                        //noinspection ResultOfMethodCallIgnored
                        temp.delete();
                        finishCapture("Fotografia nu a reușit: " + failure.getMessage());
                    }
                });
    }

    private void processPicture(String number, File temp) {
        Bitmap upright = null;
        try {
            SourceArchiveStore.Page archived = SourceArchiveStore.preserveScan(
                    getApplicationContext(), sourceId, number, temp);
            File original = SourceArchiveStore.originalFile(getApplicationContext(), archived);
            upright = OriginalPageActivity.decodeImage(original);
            final Bitmap ocrBitmap = upright;
            recognizer.process(InputImage.fromBitmap(ocrBitmap, 0))
                    .addOnSuccessListener(recognized -> {
                        if (stopped) {
                            ocrBitmap.recycle();
                            return;
                        }
                        worker.execute(() -> finishOcr(number, ocrBitmap, recognized));
                    })
                    .addOnFailureListener(failure -> {
                        ocrBitmap.recycle();
                        finishCapture("Originalul a fost păstrat, dar OCR a eșuat. "
                                + "Pagina nu a fost declarată completă.");
                    });
            upright = null; // owned by asynchronous ML Kit callback
        } catch (Exception failure) {
            if (upright != null && !upright.isRecycled()) upright.recycle();
            finishCapture("Scanare nereușită: " + failure.getMessage());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private void finishOcr(String page, Bitmap upright, Text recognized) {
        try {
            SourceArchiveStore.saveOcr(getApplicationContext(), sourceId, page, recognized,
                    upright.getWidth(), upright.getHeight());
            TopicMap thematic = TopicMapStore.load(getApplicationContext());
            ExactCartographyCapture.Report result = ExactCartographyCapture.capture(
                    getApplicationContext(), recognized, thematic, page, false);
            finishCapture("Original JPEG păstrat intact. "
                    + result.compact() + " • verifică înainte de ÎNCHEIE ✓.");
        } catch (Exception failure) {
            finishCapture("Originalul este păstrat, dar cartografierea necesită reluare: "
                    + failure.getMessage());
        } finally {
            if (!upright.isRecycled()) upright.recycle();
        }
    }

    private void finishCapture(String message) {
        runOnUiThread(() -> {
            if (stopped) return;
            busy = false;
            capture.setEnabled(true);
            status.setText(message);
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == CAMERA_PERMISSION) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) openCamera();
            else status.setText("Permisiunea camerei este necesară pentru scanare.");
        }
    }

    @Override
    protected void onDestroy() {
        stopped = true;
        if (controller != null) controller.unbind();
        worker.shutdown();
        if (recognizer != null) recognizer.close();
        super.onDestroy();
    }

    private int dp(int px) { return Math.round(px * getResources().getDisplayMetrics().density); }
}
