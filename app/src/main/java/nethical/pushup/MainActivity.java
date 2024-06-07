package nethical.pushup;

import android.graphics.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

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
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.java.posedetector.PoseDetectorProcessor;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private GraphicOverlay graphicOverlay;
    private TextView textView;
    private ExecutorService cameraExecutor;
    private PoseDetector poseDetector;
   private ProcessCameraProvider cameraProvider;
   private Camera camera;
   private Preview previewUseCase;
   private ImageAnalysis analysisUseCase;
   private PoseDetectorProcessor imageProcessor;
    
    private boolean needUpdateGraphicOverlayImageSourceInfo = true;

  private int lensFacing = CameraSelector.LENS_FACING_BACK;
  private CameraSelector cameraSelector;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        graphicOverlay = findViewById(R.id.overlayView);
        textView = findViewById(R.id.textView);

        
        AccuratePoseDetectorOptions options =
                new AccuratePoseDetectorOptions.Builder()
                        .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                        .build();
               
        poseDetector = PoseDetection.getClient(options);

        cameraExecutor = Executors.newSingleThreadExecutor();

        imageProcessor = new PoseDetectorProcessor(
            this,options,false,false,false,false,true
        );
        startCamera();
    }
    

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                    @Override
                    public void analyze(@NonNull ImageProxy imageProxy) {
                                
                        if (needUpdateGraphicOverlayImageSourceInfo) {
                            boolean isImageFlipped = lensFacing == CameraSelector.LENS_FACING_FRONT;
                            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
                            if (rotationDegrees == 0 || rotationDegrees == 180) {
                            graphicOverlay.setImageSourceInfo(
                                imageProxy.getWidth(), imageProxy.getHeight(), isImageFlipped);
                            } else {
                            graphicOverlay.setImageSourceInfo(
                                imageProxy.getHeight(), imageProxy.getWidth(), isImageFlipped);
                            }
                            needUpdateGraphicOverlayImageSourceInfo = false;
                                    
                        }
                        try {
                            imageProcessor.processImageProxy(imageProxy, graphicOverlay);
                        } catch (Exception e) {
                            Log.e("ml-error", "Failed to process image. Error: " + e.getLocalizedMessage());
                            Toast.makeText(getApplicationContext(),e.getLocalizedMessage(), Toast.LENGTH_SHORT)
                                .show();
                        }
                        processImageProxy(imageProxy);
                    }
                });

                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void processImageProxy(ImageProxy imageProxy) {
        @SuppressWarnings("UnsafeOptInUsageError")
        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
        poseDetector.process(image)
                .addOnSuccessListener(pose -> {
                    
                })
                .addOnFailureListener(e -> Log.e("PoseDetection", "Pose detection failed", e))
                .addOnCompleteListener(task -> imageProxy.close());
                
                

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
