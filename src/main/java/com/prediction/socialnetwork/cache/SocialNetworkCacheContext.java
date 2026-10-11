package com.prediction.socialnetwork.cache;

import com.prediction.socialnetwork.domain.DatasetType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record SocialNetworkCacheContext(
        String cacheNamespace,
        String datasetId,
        DatasetType datasetType,
        String sessionId,
        SocialNetworkVersionContext versions,
        CacheResultType resultType,
        Map<String, Object> scope,
        Map<String, Object> parameters) {

    public static final String NAMESPACE = "social-network";

    public SocialNetworkCacheContext {
        cacheNamespace = requireText(cacheNamespace, "cacheNamespace");
        datasetId = requireText(datasetId, "datasetId");
        datasetType = Objects.requireNonNull(datasetType, "datasetType");
        versions = Objects.requireNonNull(versions, "versions");
        resultType = Objects.requireNonNull(resultType, "resultType");
        scope = immutableMap(scope, "scope");
        parameters = immutableMap(parameters, "parameters");

        if (datasetType == DatasetType.BUILTIN && sessionId != null) {
            throw new IllegalArgumentException("builtin cache context must not have a sessionId");
        }
        if (datasetType == DatasetType.UPLOAD_SESSION
                && (sessionId == null || sessionId.isBlank())) {
            throw new IllegalArgumentException("upload cache context must have a sessionId");
        }
    }

    public static SocialNetworkCacheContext builtin(
            String datasetId,
            SocialNetworkVersionContext versions,
            CacheResultType resultType,
            Map<String, Object> scope,
            Map<String, Object> parameters) {
        return new SocialNetworkCacheContext(
                NAMESPACE, datasetId, DatasetType.BUILTIN, null, versions,
                resultType, scope, parameters);
    }

    public static SocialNetworkCacheContext uploadSession(
            String datasetId,
            String sessionId,
            SocialNetworkVersionContext versions,
            CacheResultType resultType,
            Map<String, Object> scope,
            Map<String, Object> parameters) {
        return new SocialNetworkCacheContext(
                NAMESPACE, datasetId, DatasetType.UPLOAD_SESSION, sessionId, versions,
                resultType, scope, parameters);
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value, String field) {
        Objects.requireNonNull(value, field);
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
