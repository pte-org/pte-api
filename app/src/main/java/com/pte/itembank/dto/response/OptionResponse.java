package com.pte.itembank.dto.response;

import java.util.UUID;

public record OptionResponse(UUID publicId, String text, boolean correct, int orderIndex) {
}
