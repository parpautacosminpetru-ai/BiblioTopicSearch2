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
        SourceArchiveStore.Page existing = SourceArchiveStore.page(this, sourceId, page);
        if (existing != null) {
            if (!"IMAGE".equals(existing.mediaKind)) {
                status.setText("Această pagină este legată de un PDF, nu de o fotografie.");
                return;
            }
            if (VerifiedCaptureCoordinator.isIndexed(this, sourceId, page)) {
                status.setText("Pagina este deja indexată; originalul și OCR-ul nu se suprascriu.");
                return;
            }
            busy = true;
            capture.setEnabled(false);
            if (!SourceArchiveStore.lines(this, sourceId, page).isEmpty()) {
                status.setText("Recuperez indexul din OCR-ul deja arhivat…");
                worker.execute(() -> {
                    try {
                        VerifiedCaptureCoordinator.Result result =
                                VerifiedCaptureCoordinator.recover(getApplicationContext(),
                                        sourceId, page, TopicMapStore.load(getApplicationContext()));
                        finishCapture("Index recuperat: " + result.paragraphs
                                + " paragrafe • pagina rămâne de verificat.");
                    } catch (Exception problem) {
                        finishCapture("Indexul nu a putut fi recuperat: " + problem.getMessage());
                    }
                });
            } else {
                status.setText("Originalul există; reiau numai OCR-ul, fără o nouă fotografie…");
                worker.execute(() -> recoverExistingOcr(existing));
            }
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

    private void recoverExistingOcr(SourceArchiveStore.Page archived) {
        Bitmap upright = null;
        try {
            File original = SourceArchiveStore.originalFile(getApplicationContext(), archived);
            upright = OriginalPageActivity.decodeImage(original);
            final Bitmap image = upright;
            recognizer.process(InputImage.fromBitmap(image, 0))
                    .addOnSuccessListener(result -> {
                        if (stopped) { image.recycle(); return; }
                        worker.execute(() -> finishOcr(archived.number, image, result));
                    })
                    .addOnFailureListener(failure -> {
                        image.recycle();
                        finishCapture("Original păstrat; OCR nereușit: " + failure.getMessage());
                    });
            upright = null;
        } catch (Exception failure) {
            finishCapture("Nu am putut relua OCR din original: " + failure.getMessage());
        } finally {
            if (upright != null && !upright.isRecycled()) upright.recycle();
        }
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
            if (!sourceId.equals(IndexCoreSourceRegistry.activeSourceId(getApplicationContext()))) {
                throw new IllegalStateException("Sursa activă a fost schimbată. Originalul rămâne păstrat, fără cartografiere greșită.");
            }
            VerifiedCaptureCoordinator.Result result =
                    VerifiedCaptureCoordinator.capture(getApplicationContext(), sourceId, page,
                            recognized, upright.getWidth(), upright.getHeight(),
                            TopicMapStore.load(getApplicationContext()));
            finishCapture("Original JPEG intact • " + result.paragraphs
                    + " paragrafe indexate • verifică facsimilul înainte de ÎNCHEIE ✓.");
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
