package com.prediction.socialnetwork.application;

import java.util.List;
import java.util.Map;

public record SocialNetworkApiResponse<T>(
        boolean success,
        String code,
        String message,
        String requestId,
        T data,
        List<Map<String, Object>> warnings,
        Map<String, Object> meta) {

    public static <T> SocialNetworkApiResponse<T> success(
            String requestId,
            T data,
            List<Map<String, Object>> warnings,
            Map<String, Object> meta) {
        return new SocialNetworkApiResponse<>(
                true, "OK", "请求成功", requestId, data, warnings, meta);
    }

    public static <T> SocialNetworkApiResponse<T> error(
            String requestId,
            String code,
            String message,
            Map<String, Object> meta) {
        return new SocialNetworkApiResponse<>(
                false, code, message, requestId, null, List.of(), meta);
    }
}
