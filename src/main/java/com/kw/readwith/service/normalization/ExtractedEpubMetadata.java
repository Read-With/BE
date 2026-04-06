package com.kw.readwith.service.normalization;

public record ExtractedEpubMetadata(
        String title,
        String author,
        String language,
        ExtractedEpubCover cover
) {

    public static ExtractedEpubMetadata empty() {
        return new ExtractedEpubMetadata(null, null, null, null);
    }
}
