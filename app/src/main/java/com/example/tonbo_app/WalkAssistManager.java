package com.example.tonbo_app;

import android.speech.tts.TextToSpeech;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class WalkAssistManager {

    public static final String STATE_CLEAR = "CLEAR";
    public static final String STATE_OBSTACLE = "OBSTACLE";
    public static final String STATE_UNKNOWN = "UNKNOWN";

    private static final int CONFIRM_FRAMES_OBSTACLE = 2;
    private static final int CONFIRM_FRAMES_CLEAR = 5;
    private static final int CONFIRM_FRAMES_UNKNOWN = 3;
    private static final int OBSTACLE_RELEASE_CLEAR_FRAMES = 5;

    private static final long COOLDOWN_OBSTACLE_MS = 1000;
    private static final long COOLDOWN_CLEAR_MS = 3000;
    private static final long COOLDOWN_UNKNOWN_MS = 5000;
    private static final float DANGER_CENTER_LEFT_RATIO = 0.3f;
    private static final float DANGER_CENTER_RIGHT_RATIO = 0.7f;
    private static final float MIN_OBSTACLE_AREA_RATIO = 0.015f;
    private static final float MIN_CENTER_OVERLAP_RATIO = 0.25f;

    private static final Set<String> WALKING_OBSTACLES = new HashSet<>(Arrays.asList(
            "person", "car", "truck", "bus", "motorcycle", "bicycle",
            "bench", "chair", "traffic light", "stop sign",
            "bottle", "backpack", "suitcase", "handbag", "potted plant",
            "fire hydrant", "parking meter", "umbrella",
            // Common names from custom/other detectors
            "tree", "pole", "box", "cone", "barrier", "cart", "stroller", "wheelchair"
    ));

    private final TextToSpeech tts;

    // Confirmed state returned by analyze and used by speak.
    private String confirmedState = STATE_UNKNOWN;
    // Candidate state and frames for debounce confirmation.
    private String candidateState = STATE_UNKNOWN;
    private int candidateFrames = 0;
    // CLEAR candidate can tolerate one noisy frame without breaking count.
    private boolean clearNoiseUsed = false;
    // Hysteresis: after OBSTACLE is confirmed, require 5 raw CLEAR frames to release.
    private int obstacleReleaseClearFrames = 0;

    // Per-state cooldown timestamps for speech.
    private long lastObstacleSpeakTime = 0;
    private long lastClearSpeakTime = 0;
    private long lastUnknownSpeakTime = 0;

    public WalkAssistManager(TextToSpeech tts) {
        this.tts = tts;
        this.confirmedState = STATE_UNKNOWN;
        this.candidateState = STATE_UNKNOWN;
    }

    public String analyzeWalkablePath(List<Detection> detections,
                                      float screenWidth,
                                      float screenHeight) {
        // Keep parameters for API compatibility with caller.
        // State decision currently does not require geometric partitioning.
        String rawState = computeRawState(detections, screenWidth, screenHeight);

        // Hysteresis: once obstacle is confirmed, do not release immediately.
        // Need continuous raw CLEAR frames to unlock OBSTACLE state.
        if (STATE_OBSTACLE.equals(confirmedState)) {
            if (STATE_CLEAR.equals(rawState)) {
                obstacleReleaseClearFrames++;
            } else {
                obstacleReleaseClearFrames = 0;
            }
            if (obstacleReleaseClearFrames < OBSTACLE_RELEASE_CLEAR_FRAMES) {
                return confirmedState;
            }
        }

        // Candidate confirmation with CLEAR noise tolerance.
        if (rawState.equals(candidateState)) {
            candidateFrames++;
        } else if (STATE_CLEAR.equals(candidateState) && !clearNoiseUsed) {
            // Allow one noisy frame while accumulating CLEAR.
            clearNoiseUsed = true;
        } else {
            candidateState = rawState;
            candidateFrames = 1;
            clearNoiseUsed = false;
        }

        int requiredFrames = requiredFramesFor(candidateState);
        if (candidateFrames >= requiredFrames && !candidateState.equals(confirmedState)) {
            confirmedState = candidateState;
            if (STATE_OBSTACLE.equals(confirmedState)) {
                obstacleReleaseClearFrames = 0;
            }
        }

        return confirmedState;
    }

    public void speak(String pathState) {
        long now = System.currentTimeMillis();
        if (pathState == null || pathState.isEmpty()) return;

        String speechText = "";
        long cooldownMs;
        long lastSpokenAt;

        switch (pathState) {
            case STATE_CLEAR:
                speechText = "前方道路畅通";
                cooldownMs = COOLDOWN_CLEAR_MS;
                lastSpokenAt = lastClearSpeakTime;
                break;
            case STATE_OBSTACLE:
                speechText = "前方受阻，请停下";
                cooldownMs = COOLDOWN_OBSTACLE_MS;
                lastSpokenAt = lastObstacleSpeakTime;
                break;
            case STATE_UNKNOWN:
                speechText = "前方情况不确定，请减速慢行";
                cooldownMs = COOLDOWN_UNKNOWN_MS;
                lastSpokenAt = lastUnknownSpeakTime;
                break;
            default:
                return;
        }

        if (now - lastSpokenAt < cooldownMs) return;

        if (tts != null && !speechText.isEmpty()) {
            tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, null);
            if (STATE_CLEAR.equals(pathState)) {
                lastClearSpeakTime = now;
            } else if (STATE_OBSTACLE.equals(pathState)) {
                lastObstacleSpeakTime = now;
            } else if (STATE_UNKNOWN.equals(pathState)) {
                lastUnknownSpeakTime = now;
            }
        }
    }

    private String computeRawState(List<Detection> detections, float screenWidth, float screenHeight) {
        if (detections == null || detections.isEmpty()) return STATE_UNKNOWN;

        boolean hasAnyObstacle = false;
        boolean hasAnyHighConfidenceDetection = false;

        for (Detection det : detections) {
            if (det == null) continue;
            String label = normalizeLabel(det.getLabel());
            if (label.isEmpty()) continue;

            if (det.getConfidence() >= AppConstants.WALK_ASSIST_OBSTACLE_CONFIDENCE_THRESHOLD) {
                hasAnyHighConfidenceDetection = true;
                if (isWalkingObstacle(label) && isObstacleInPath(det, screenWidth, screenHeight)) {
                    hasAnyObstacle = true;
                    break;
                }
            }
        }

        if (hasAnyObstacle) return STATE_OBSTACLE;
        if (!hasAnyHighConfidenceDetection) return STATE_UNKNOWN;
        return STATE_CLEAR;
    }

    private int requiredFramesFor(String state) {
        if (STATE_OBSTACLE.equals(state)) return CONFIRM_FRAMES_OBSTACLE;
        if (STATE_CLEAR.equals(state)) return CONFIRM_FRAMES_CLEAR;
        return CONFIRM_FRAMES_UNKNOWN;
    }

    private boolean isObstacleInPath(Detection det, float screenWidth, float screenHeight) {
        if (det == null || screenWidth <= 0 || screenHeight <= 0) return false;

        float left = Math.max(0f, Math.min(screenWidth, det.getLeft()));
        float right = Math.max(0f, Math.min(screenWidth, det.getRight()));
        float top = Math.max(0f, Math.min(screenHeight, det.getTop()));
        float bottom = Math.max(0f, Math.min(screenHeight, det.getBottom()));

        float boxWidth = right - left;
        float boxHeight = bottom - top;
        if (boxWidth <= 0 || boxHeight <= 0) return false;

        float areaRatio = (boxWidth * boxHeight) / (screenWidth * screenHeight);
        if (areaRatio < MIN_OBSTACLE_AREA_RATIO) return false;

        float centerLeft = screenWidth * DANGER_CENTER_LEFT_RATIO;
        float centerRight = screenWidth * DANGER_CENTER_RIGHT_RATIO;
        float overlap = Math.max(0f, Math.min(right, centerRight) - Math.max(left, centerLeft));
        float overlapRatio = overlap / boxWidth;

        return overlapRatio >= MIN_CENTER_OVERLAP_RATIO;
    }

    private boolean isWalkingObstacle(String label) {
        return WALKING_OBSTACLES.contains(label);
    }

    private String normalizeLabel(String label) {
        if (label == null) return "";
        return label.trim().toLowerCase(Locale.ROOT);
    }
}
