package com.kw.readwith.service.normalization;

record EpubManifestItem(
        String id,
        String href,
        String mediaType,
        String properties
) {

    boolean hasProperty(String property) {
        if (properties == null || properties.isBlank()) {
            return false;
        }

        for (String token : properties.split("\\s+")) {
            if (property.equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }
}
