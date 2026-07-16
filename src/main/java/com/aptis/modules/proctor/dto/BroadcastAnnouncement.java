package com.aptis.modules.proctor.dto;

/** Payload pushed to students subscribed to /topic/exam/{examId}/announcements. Plaintext only. */
public record BroadcastAnnouncement(Long examId, String message) {
}
