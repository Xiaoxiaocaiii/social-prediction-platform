package com.prediction.socialnetwork.domain;

import com.prediction.socialnetwork.capability.CapabilityStatus;

/**
 * Translates legacy backend status enums into the canonical state vocabulary.
 */
public final class SocialNetworkStateCatalog {

    private SocialNetworkStateCatalog() {
    }

    public static SocialNetworkState fromCapability(CapabilityStatus status) {
        return switch (status) {
            case AVAILABLE -> SocialNetworkState.AVAILABLE;
            case LIMITED -> SocialNetworkState.LIMITED;
            case DISABLED -> SocialNetworkState.DISABLED;
            case UNAVAILABLE -> SocialNetworkState.UNAVAILABLE;
            case CHECKING -> SocialNetworkState.LOADING;
        };
    }

    public static SocialNetworkState fromDataset(DatasetStatus status) {
        return switch (status) {
            case HEALTHY -> SocialNetworkState.AVAILABLE;
            case AVAILABLE_WITH_WARNINGS, PARTIAL -> SocialNetworkState.LIMITED;
            case UNAVAILABLE -> SocialNetworkState.UNAVAILABLE;
        };
    }
}
