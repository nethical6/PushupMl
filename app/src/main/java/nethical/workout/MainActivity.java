package nethical.workout;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;

import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.java.posedetector.PoseDetectorProcessor;
import com.google.mlkit.vision.demo.java.posedetector.classification.PoseClassifierProcessor;

import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity
        implements PoseDetectorProcessor.PoseDetectorListener {

    private PreviewView previewView;
    private GraphicOverlay graphicOverlay;
    private Button startButton;

    private ExecutorService cameraExecutor;
    private ExecutorService executor;

    private PoseDetectorProcessor imageProcessor;

    private boolean needUpdateGraphicOverlayImageSourceInfo = true;

    private int lensFacing;
    private ProcessCameraProvider cameraProvider;

    private Handler uiHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        graphicOverlay = findViewById(R.id.overlayView);
        startButton = findViewById(R.id.start_button_workout);

        cameraExecutor = Executors.newSingleThreadExecutor();
        executor = Executors.newSingleThreadExecutor();

        startButton.setEnabled(false);

        uiHandler = new Handler(Looper.getMainLooper());

        startCamera();
        loadCsvData();

        startButton.setOnClickListener(
                (v) -> {
                    if (startButton.isEnabled()) {
                        startCamera();
                        startButton.setText("Stop Quest");
                    } else {
                        Toast.makeText(this, "Please wait while the AI loads", Toast.LENGTH_SHORT)
                                .show();
                    }
                });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(
                () -> {
                    try {
                        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                        Preview preview = new Preview.Builder().build();
                        preview.setSurfaceProvider(previewView.getSurfaceProvider());

                        CameraSelector cameraSelector =
                                new CameraSelector.Builder()
                                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                                        .build();

                        cameraProvider.unbindAll();
                        if (imageProcessor == null) {

                            cameraProvider.bindToLifecycle(this, cameraSelector, preview);

                            return;
                        }

                        ImageAnalysis imageAnalysis =
                                new ImageAnalysis.Builder()
                                        .setBackpressureStrategy(
                                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build();

                        imageAnalysis.setAnalyzer(
                                cameraExecutor,
                                new ImageAnalysis.Analyzer() {
                                    @Override
                                    public void analyze(@NonNull ImageProxy imageProxy) {

                                        if (needUpdateGraphicOverlayImageSourceInfo) {
                                            boolean isImageFlipped =
                                                    lensFacing == CameraSelector.LENS_FACING_FRONT;
                                            int rotationDegrees =
                                                    imageProxy.getImageInfo().getRotationDegrees();
                                            if (rotationDegrees == 0 || rotationDegrees == 180) {
                                                graphicOverlay.setImageSourceInfo(
                                                        imageProxy.getWidth(),
                                                        imageProxy.getHeight(),
                                                        isImageFlipped);
                                            } else {
                                                graphicOverlay.setImageSourceInfo(
                                                        imageProxy.getHeight(),
                                                        imageProxy.getWidth(),
                                                        isImageFlipped);
                                            }
                                            needUpdateGraphicOverlayImageSourceInfo = false;
                                        }
                                        try {
                                            imageProcessor.processImageProxy(
                                                    imageProxy, graphicOverlay);
                                            // processImageProxy(imageProxy);
                                        } catch (Exception e) {
                                            Log.e(
                                                    "ml-error",
                                                    "Failed to process image. Error: "
                                                            + e.getLocalizedMessage());
                                            Toast.makeText(
                                                            getApplicationContext(),
                                                            e.getLocalizedMessage(),
                                                            Toast.LENGTH_SHORT)
                                                    .show();
                                        }
                                    }
                                });

                        cameraProvider.bindToLifecycle(
                                this, cameraSelector, preview, imageAnalysis);

                    } catch (ExecutionException | InterruptedException e) {
                        // No errors need to be handled for this Future.
                        // This should never be reached.
                    }
                },
                ContextCompat.getMainExecutor(this));
    }

    private void loadCsvData() {
        // Create a new thread
        AccuratePoseDetectorOptions options =
                new AccuratePoseDetectorOptions.Builder()
                        .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                        .build();

        new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                // Perform the background task
                                PoseClassifierProcessor pcf =
                                        new PoseClassifierProcessor(
                                                getApplication(),
                                                true,
                                                new String[] {
                                                    PoseClassifierProcessor.SQUATS_CLASS
                                                });
                                // Update the UI on the main thread
                                uiHandler.post(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                imageProcessor =
                                                        new PoseDetectorProcessor(
                                                                getApplicationContext(),
                                                                options,
                                                                false,
                                                                false,
                                                                false,
                                                                true,
                                                                true,
                                                                pcf, (poseClassification)->{
                                                                    if(poseClassification.size()>=2){
                                                                       for (int i = 0; i < poseClassification.size(); i++) {
                                                                            startButton.setText(poseClassification.get(0));
                                                                        }
                                                                    }
                                                                    
                                                                });
                                                startButton.setText("Start");
                                                startButton.setEnabled(true);
                                            }
                                        });
                            }
                        })
                .start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (imageProcessor != null) {
            imageProcessor.stop();
        }
        //    cameraProvider.shutdown();

    }

    @Override
    public void onNewRepOccured(List<String> poseClassification) {
      for (int i = 0; i < poseClassification.size(); i++) {
        startButton.setText(poseClassification.get(i));
        }
    }
}
