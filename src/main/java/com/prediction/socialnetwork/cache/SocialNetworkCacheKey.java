package com.prediction.socialnetwork.cache;

public record SocialNetworkCacheKey(
        String value,
        String canonicalMaterial) {

    public SocialNetworkCacheKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("cache key value must not be blank");
        }
        if (canonicalMaterial == null || canonicalMaterial.isBlank()) {
            throw new IllegalArgumentException("canonical cache material must not be blank");
        }
    }
}
