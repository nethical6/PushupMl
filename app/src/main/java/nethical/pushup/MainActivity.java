package nethical.pushup;

import android.content.Context;
import android.graphics.Camera;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
import com.google.mlkit.vision.demo.java.posedetector.classification.PoseClassifierProcessor;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;

import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private GraphicOverlay graphicOverlay;
    

    private ExecutorService cameraExecutor;
    private ExecutorService executor;

    private PoseDetectorProcessor imageProcessor;

    private boolean needUpdateGraphicOverlayImageSourceInfo = true;

    private int lensFacing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        graphicOverlay = findViewById(R.id.overlayView);
        
        cameraExecutor = Executors.newSingleThreadExecutor();
        executor = Executors.newSingleThreadExecutor();

        PoseDetectorOptions options =
                new PoseDetectorOptions.Builder()
                        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                        .build();

        //     PoseClassifierProcessor pcf = new PoseClassifierProcessor(getApplication(),true);

        startCamera();

        ExecutorService executor2 = Executors.newSingleThreadExecutor();

        // Submit the task to the executor
        Future<PoseClassifierProcessor> future =
                executor2.submit(
                        () -> {
                            return new PoseClassifierProcessor(getApplication(), true);
                        });

        // Define a callback function to be executed when the task completes
        Runnable onCompletion =
                () -> {
                    try {
                        // Retrieve the PoseClassifierProcessor object from the Future
                        final PoseClassifierProcessor pcf = future.get();
                        imageProcessor =
                                new PoseDetectorProcessor(
                                        this, options, false, false, false, true, true, pcf);
                        startCamera();
                    } catch (InterruptedException | ExecutionException e) {
                        // Handle any exceptions that occurred during the task execution
                        e.printStackTrace();
                    } finally {
                        executor2.shutdown(); // Shut down the executor
                    }
                };

        // Execute onCompletion in a separate thread to avoid blocking the main thread
        executor2.submit(onCompletion);
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

                        if (imageProcessor == null) {
                            cameraProvider.bindToLifecycle(this, cameraSelector, preview);

                            return;
                        }
                        cameraProvider.unbindAll();

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();

        executor.shutdown();
    }

    
}
