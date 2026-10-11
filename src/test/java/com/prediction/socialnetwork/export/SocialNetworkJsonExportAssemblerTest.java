package com.prediction.socialnetwork.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prediction.socialnetwork.application.SocialNetworkWorkspaceService;
import com.prediction.socialnetwork.application.SocialNetworkWorkspaceSnapshot;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SocialNetworkJsonExportAssemblerTest {

    @Autowired
    private SocialNetworkWorkspaceService workspace;

    @Test
    void assemblesTraceableNetworkJsonResult() {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.current();
        Map<String, Object> result = new SocialNetworkJsonExportAssembler().assemble(
                snapshot,
                "network",
                Map.of("includeExternalNodes", true),
                Map.of("scope", "全量规范化关系网络"));

        assertEquals("network", result.get("exportType"));
        SocialNetworkExportMetadata metadata =
                (SocialNetworkExportMetadata) result.get("metadata");
        assertEquals("think-tank-social-network", metadata.datasetId());
        assertEquals(true, metadata.query().get("includeExternalNodes"));
        assertEquals("全量规范化关系网络", metadata.scope().get("scope"));
        assertEquals(626, metadata.sampleCounts().get("resultCount"));
    }

    @Test
    void rejectsEmptyTopicExportInsteadOfCreatingMisleadingResult() {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.current();
        assertThrows(IllegalStateException.class, () ->
                new SocialNetworkJsonExportAssembler().assemble(
                        snapshot,
                        "topic",
                        Map.of("term", "__no_such_term__"),
                        Map.of()));
    }
}
