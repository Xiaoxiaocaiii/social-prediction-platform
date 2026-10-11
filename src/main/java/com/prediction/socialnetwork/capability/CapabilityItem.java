package com.prediction.socialnetwork.capability;

import com.prediction.socialnetwork.domain.SocialNetworkState;

import java.util.List;

public record CapabilityItem(
        String capabilityId,
        String moduleId,
        CapabilityStatus status,
        SocialNetworkState state,
        String reasonCode,
        String reasonMessage,
        List<String> requiredInputs,
        List<String> availableInputs,
        String fallback) {

    public CapabilityItem {
        requiredInputs = List.copyOf(requiredInputs);
        availableInputs = List.copyOf(availableInputs);
    }
}
