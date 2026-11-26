package com.pte.itembank.internal.generation.controller;

import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.generation.constant.VoiceConstants;
import com.pte.itembank.internal.generation.voice.ChangeVoiceRequest;
import com.pte.itembank.internal.generation.voice.VoiceCatalogQueryService;
import com.pte.itembank.internal.generation.voice.VoiceCatalogResponse;
import com.pte.itembank.internal.generation.voice.VoiceChangeService;
import com.pte.itembank.internal.generation.voice.VoiceSampleService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The voices of spoken questions: the list to pick from, a sample of each, and speaking a draft again. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
public class QuestionVoiceController {

    private final VoiceChangeService changeService;
    private final VoiceCatalogQueryService catalogService;
    private final VoiceSampleService sampleService;

    public QuestionVoiceController(VoiceChangeService changeService, VoiceCatalogQueryService catalogService,
                                   VoiceSampleService sampleService) {
        this.changeService = changeService;
        this.catalogService = catalogService;
        this.sampleService = sampleService;
    }

    @PostMapping("/questions/{publicId}/audio/voice")
    public ApiResponse<QuestionResponse> change(@PathVariable UUID publicId,
                                                @Valid @RequestBody ChangeVoiceRequest request) {
        return ApiResponse.success(changeService.changeVoice(publicId, request.voice(), request.version(),
                CurrentUserContext.required()));
    }

    @GetMapping("/question-generation/voices")
    public ApiResponse<VoiceCatalogResponse> voices() {
        return ApiResponse.success(catalogService.listEnabled());
    }

    @GetMapping("/question-generation/voices/{voice}/sample")
    public ResponseEntity<byte[]> sample(@PathVariable String voice) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(VoiceConstants.AUDIO_WAV))
                .header(HttpHeaders.CACHE_CONTROL, VoiceConstants.SAMPLE_CACHE_CONTROL)
                .body(sampleService.sample(voice));
    }
}
