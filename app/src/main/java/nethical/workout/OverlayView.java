package nethical.pushup;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

public class OverlayView extends View {

    private Pose pose;
    private Paint paint;

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStrokeWidth(8.0f);
    }

    public void setPose(Pose pose) {
        this.pose = pose;
        invalidate(); // Request a redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (pose == null) return;

        // Draw all landmarks
        for (PoseLandmark landmark : pose.getAllPoseLandmarks()) {
            canvas.drawCircle(landmark.getPosition().x, landmark.getPosition().y, 10.0f, paint);
        }

        // Draw connections (stick lines) between landmarks
        drawLine(canvas, pose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER);
        drawLine(canvas, pose, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP);
        drawLine(canvas, pose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW);
        drawLine(canvas, pose, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST);
        drawLine(canvas, pose, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW);
        drawLine(canvas, pose, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST);
        drawLine(canvas, pose, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE);
        drawLine(canvas, pose, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE);
        drawLine(canvas, pose, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE);
        drawLine(canvas, pose, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE);
    }

    private void drawLine(Canvas canvas, Pose pose, int startLandmarkId, int endLandmarkId) {
        PoseLandmark startLandmark = pose.getPoseLandmark(startLandmarkId);
        PoseLandmark endLandmark = pose.getPoseLandmark(endLandmarkId);

        if (startLandmark != null && endLandmark != null) {
            canvas.drawLine(startLandmark.getPosition().x, startLandmark.getPosition().y,
                    endLandmark.getPosition().x, endLandmark.getPosition().y, paint);
        }
    }
}
