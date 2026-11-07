package com.prediction.socialnetwork.application;

import java.util.List;

public record SocialNetworkGroupEvidenceView(
        String datasetId,
        String analysisType,
        String state,
        boolean analysisComplete,
        boolean membershipListComplete,
        Boolean converged,
        boolean evidenceAvailable,
        String unavailableReason,
        String dataFingerprint,
        String targetId,
        String section,
        int offset,
        int pageSize,
        int totalCount,
        List<CliqueEvidenceAggregator.MemberEvidence> members,
        List<CliqueEvidenceAggregator.TieEvidence> ties) {

    public SocialNetworkGroupEvidenceView {
        members = List.copyOf(members);
        ties = List.copyOf(ties);
    }
}
