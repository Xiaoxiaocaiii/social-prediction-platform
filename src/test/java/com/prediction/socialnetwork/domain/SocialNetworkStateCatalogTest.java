package com.prediction.socialnetwork.domain;

import com.prediction.socialnetwork.capability.CapabilityStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialNetworkStateCatalogTest {

    @Test
    void mapsCapabilityStatusesToCanonicalStates() {
        assertEquals(SocialNetworkState.AVAILABLE,
                SocialNetworkStateCatalog.fromCapability(CapabilityStatus.AVAILABLE));
        assertEquals(SocialNetworkState.LIMITED,
                SocialNetworkStateCatalog.fromCapability(CapabilityStatus.LIMITED));
        assertEquals(SocialNetworkState.DISABLED,
                SocialNetworkStateCatalog.fromCapability(CapabilityStatus.DISABLED));
        assertEquals(SocialNetworkState.UNAVAILABLE,
                SocialNetworkStateCatalog.fromCapability(CapabilityStatus.UNAVAILABLE));
        assertEquals(SocialNetworkState.LOADING,
                SocialNetworkStateCatalog.fromCapability(CapabilityStatus.CHECKING));
    }

    @Test
    void mapsDatasetStatusesToCanonicalStates() {
        assertEquals(SocialNetworkState.AVAILABLE,
                SocialNetworkStateCatalog.fromDataset(DatasetStatus.HEALTHY));
        assertEquals(SocialNetworkState.LIMITED,
                SocialNetworkStateCatalog.fromDataset(
                        DatasetStatus.AVAILABLE_WITH_WARNINGS));
        assertEquals(SocialNetworkState.LIMITED,
                SocialNetworkStateCatalog.fromDataset(DatasetStatus.PARTIAL));
        assertEquals(SocialNetworkState.UNAVAILABLE,
                SocialNetworkStateCatalog.fromDataset(DatasetStatus.UNAVAILABLE));
    }
}
