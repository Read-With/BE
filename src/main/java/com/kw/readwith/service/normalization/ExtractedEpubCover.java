package com.kw.readwith.service.normalization;

public record ExtractedEpubCover(
        String fileName,
        String contentType,
        byte[] bytes
) {

    public boolean isEmpty() {
        return bytes == null || bytes.length == 0;
    }
}
