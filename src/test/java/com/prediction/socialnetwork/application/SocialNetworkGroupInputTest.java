package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prediction.socialnetwork.network.CommunityDetectionResult;
import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.normalization.NormalizedRelationship;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import com.prediction.socialnetwork.normalization.RelationStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SocialNetworkGroupInputTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesProfileNamesFromTheBuiltinUserFile() {
        SocialNetworkWorkspaceService workspace =
                new SocialNetworkWorkspaceService(new SocialNetworkDatasetRegistry());
        var names = workspace.analysisProfileNames(SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID);
        assertEquals("CFR_org", names.get("17469492"));
        assertEquals("ChathamHouse", names.get("38630618"));
    }

    @Test
    void restoresPersistedEvidenceInputWithoutRebuildingIt() {
        String fingerprint = "fixture-fingerprint";
        SocialNetworkAnalysisResultStore store =
                new SocialNetworkAnalysisResultStore(
                        new ObjectMapper().findAndRegisterModules(), tempDir);
        SocialNetworkWorkspaceService.GroupEvidenceInput persisted = evidenceInput();
        store.saveGroupEvidenceInput(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                fingerprint,
                persisted);
        SocialNetworkWorkspaceService restartedWorkspace =
                new SocialNetworkWorkspaceService(
                        new SocialNetworkDatasetRegistry(), store);

        assertEquals(
                persisted,
                restartedWorkspace.getGroupEvidenceInput(
                        SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID, fingerprint));
    }

    private static SocialNetworkWorkspaceService.GroupEvidenceInput evidenceInput() {
        return new SocialNetworkWorkspaceService.GroupEvidenceInput(
                new SocialNetworkGraphResult(
                        List.of(
                                new NetworkNode("a", NetworkNodeType.PROFILE, 0, 1, 0, false),
                                new NetworkNode("b", NetworkNodeType.PROFILE, 1, 0, 0, false)),
                        List.of(new NetworkEdge("a", "b", "following")),
                        null,
                        null,
                        List.of(),
                        CommunityDetectionResult.notRequested(),
                        null,
                        null),
                List.of(new NormalizedUser(
                        "users.csv",
                        2,
                        "a",
                        Map.of("username", "alice"),
                        NormalizationStatus.NORMALIZED,
                        Set.of())),
                List.of(new NormalizedRelationship(
                        "relationships.csv",
                        3,
                        0,
                        "a",
                        "b",
                        AssociationStatus.LINKED,
                        AssociationStatus.LINKED,
                        RelationStatus.VALID,
                        NormalizationStatus.NORMALIZED,
                        true)));
    }
}
