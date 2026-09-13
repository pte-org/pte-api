package com.pte.examdelivery.controller;

import com.pte.common.web.ApiResponse;
import com.pte.examdelivery.dto.request.StartAttemptRequest;
import com.pte.examdelivery.dto.request.SubmitAnswerRequest;
import com.pte.examdelivery.dto.response.AttemptTaskResponse;
import com.pte.examdelivery.dto.response.AudioPlayResponse;
import com.pte.examdelivery.dto.response.TaskView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dev-only delivery surface for connecting the app to deterministic data before
 * authoring, scheduling, media and authentication are available. It mirrors
 * the student-facing response shape but deliberately owns no production
 * attempt rows, scoring, outbox or external service calls.
 */
@RestController
@Profile("mock-exam-delivery")
@RequestMapping("/mock-attempts")
public class MockExamController {

    private static final UUID DEFAULT_SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID AUDIO_PROMPT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final List<MockTask> TASKS = List.of(
            new MockTask(
                    UUID.fromString("00000000-0000-0000-0000-000000000201"),
                    "Repeat Sentence — Mock Question 1",
                    "repeat-sentence-01.wav"),
            new MockTask(
                    UUID.fromString("00000000-0000-0000-0000-000000000202"),
                    "Repeat Sentence — Mock Question 2",
                    "repeat-sentence-02.wav"));

    private final Map<UUID, MockAttemptState> attempts = new ConcurrentHashMap<>();
    private final String audioPublicBaseUrl;

    public MockExamController(
            @Value("${mock-exam.audio-public-base-url:http://localhost:8085/api/exam-delivery/mock-attempts/media}")
            String audioPublicBaseUrl) {
        this.audioPublicBaseUrl = audioPublicBaseUrl.replaceAll("/$", "");
    }

    @PostMapping
    public ApiResponse<AttemptTaskResponse> start(@RequestBody(required = false) StartAttemptRequest request) {
        UUID sessionId = request == null || request.sessionPublicId() == null
                ? DEFAULT_SESSION_ID
                : request.sessionPublicId();
        MockAttemptState state = attempts.computeIfAbsent(sessionId, ignored -> new MockAttemptState(UUID.randomUUID()));
        return ApiResponse.success(response(state));
    }

    @GetMapping("/{attemptPublicId}/next-task")
    public ApiResponse<AttemptTaskResponse> nextTask(@PathVariable UUID attemptPublicId) {
        MockAttemptState state = findAttempt(attemptPublicId);
        if (!state.completed && state.currentIndex < TASKS.size() - 1) {
            state.currentIndex++;
        } else if (!state.completed) {
            state.completed = true;
        }
        return ApiResponse.success(response(state));
    }

    @PostMapping("/{attemptPublicId}/answers")
    public ApiResponse<Void> submitAnswer(
            @PathVariable UUID attemptPublicId,
            @RequestBody SubmitAnswerRequest request) {
        findAttempt(attemptPublicId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{attemptPublicId}/submit")
    public ApiResponse<AttemptTaskResponse> submit(@PathVariable UUID attemptPublicId) {
        MockAttemptState state = findAttempt(attemptPublicId);
        state.completed = true;
        return ApiResponse.success(response(state));
    }

    @GetMapping("/{attemptPublicId}/items/{itemPublicId}/audio")
    public ApiResponse<AudioPlayResponse> playAudio(
            @PathVariable UUID attemptPublicId,
            @PathVariable UUID itemPublicId) {
        MockAttemptState state = findAttempt(attemptPublicId);
        MockTask task = TASKS.get(state.currentIndex);
        if (!task.publicId().equals(itemPublicId)) {
            throw new IllegalArgumentException("Requested mock audio is not the current task");
        }
        return ApiResponse.success(new AudioPlayResponse(audioPublicBaseUrl + "/" + task.audioFileName()));
    }

    @GetMapping(value = "/media/{fileName}", produces = "audio/wav")
    public ResponseEntity<Resource> media(@PathVariable String fileName) {
        if (TASKS.stream().noneMatch(task -> task.audioFileName().equals(fileName))) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new ClassPathResource("mock-audio/" + fileName);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .body(resource);
    }

    private MockAttemptState findAttempt(UUID attemptPublicId) {
        return attempts.values().stream()
                .filter(state -> state.publicId.equals(attemptPublicId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown mock attempt: " + attemptPublicId));
    }

    private AttemptTaskResponse response(MockAttemptState state) {
        if (state.completed) {
            return new AttemptTaskResponse(state.publicId, "SUBMITTED", true, null, null, null);
        }
        MockTask task = TASKS.get(state.currentIndex);
        TaskView view = new TaskView(
                task.publicId(),
                state.currentIndex,
                TASKS.size(),
                "SPEAKING",
                "REPEAT_SENTENCE",
                task.title(),
                null,
                AUDIO_PROMPT_ID,
                null,
                null,
                null,
                null,
                null,
                10,
                15,
                Instant.now().plusSeconds(60),
                3,
                3,
                null);
        return new AttemptTaskResponse(state.publicId, "IN_PROGRESS", false, view, null, null);
    }

    private record MockTask(UUID publicId, String title, String audioFileName) {
    }

    private static final class MockAttemptState {
        private final UUID publicId;
        private int currentIndex;
        private boolean completed;

        private MockAttemptState(UUID publicId) {
            this.publicId = publicId;
        }
    }
}
