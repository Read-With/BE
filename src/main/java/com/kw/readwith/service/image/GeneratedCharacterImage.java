package com.kw.readwith.service.image;

public record GeneratedCharacterImage(
        byte[] imageData,
        String model,
        String prompt,
        String promptHash,
        String requestId
) {
}
