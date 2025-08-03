package club.gifters.giftersclub.gifts;

import androidx.annotation.RestrictTo;
import androidx.camera.core.VideoCapture;
import java.io.File;
import java.util.concurrent.Executor;

/**
 * Helper to invoke VideoCapture.startRecording with file-based output options,
 * bypassing Kotlin restrictTo compile-time checks.
 */
public class VideoCaptureHelper {
    /**
     * Starts recording to the given file using VideoCapture's OutputFileOptions builder.
     */
    @SuppressWarnings("RestrictedApi")
    public static void startRecording(VideoCapture videoCapture,
                                      File file,
                                      Executor executor,
                                      VideoCapture.OnVideoSavedCallback callback) {
        VideoCapture.OutputFileOptions options =
                new VideoCapture.OutputFileOptions.Builder(file)
                        .build();
        videoCapture.startRecording(options, executor, callback);
    }
}