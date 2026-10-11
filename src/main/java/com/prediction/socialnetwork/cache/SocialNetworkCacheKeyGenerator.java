package com.prediction.socialnetwork.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SocialNetworkCacheKeyGenerator {

    public SocialNetworkCacheKey generate(SocialNetworkCacheContext context) {
        Objects.requireNonNull(context, "context");
        String material = canonicalContext(context);
        return new SocialNetworkCacheKey(sha256(material), material);
    }

    private String canonicalContext(SocialNetworkCacheContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append(context.cacheNamespace()).append('|');
        appendField(builder, "datasetId", context.datasetId());
        appendField(builder, "datasetType", context.datasetType().name().toLowerCase());
        appendField(builder, "sessionId", context.sessionId());
        appendField(builder, "datasetVersion", context.versions().datasetVersion());
        appendField(builder, "normalizationVersion", context.versions().normalizationVersion());
        appendField(builder, "metricVersion", context.versions().metricVersion());
        appendField(builder, "pagerankVersion", context.versions().pagerankVersion());
        appendField(builder, "textProcessingVersion", context.versions().textProcessingVersion());
        appendField(builder, "stopwordsVersion", context.versions().stopwordsVersion());
        appendField(builder, "tokenizerVersion", context.versions().tokenizerVersion());
        appendField(builder, "tfidfVersion", context.versions().tfidfVersion());
        appendField(builder, "explanationVersion", context.versions().explanationVersion());
        appendField(builder, "resultType", context.resultType().value());
        appendField(builder, "scope", canonicalValue(context.scope()));
        appendField(builder, "parameters", canonicalValue(context.parameters()));
        return builder.toString();
    }

    private static void appendField(StringBuilder builder, String name, String value) {
        builder.append(name).append('=').append(escape(value)).append('|');
    }

    private static String canonicalValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .map(entry -> escape(String.valueOf(entry.getKey()))
                            + ':' + canonicalValue(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(SocialNetworkCacheKeyGenerator::canonicalValue)
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> values = new java.util.ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                values.add(java.lang.reflect.Array.get(value, index));
            }
            return canonicalValue(values);
        }
        return escape(String.valueOf(value));
    }

    private static String escape(String value) {
        if (value == null) {
            return "null";
        }
        return value.replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("=", "\\=")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace(",", "\\,")
                .replace(":", "\\:");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                hex.append(String.format("%02x", current));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
