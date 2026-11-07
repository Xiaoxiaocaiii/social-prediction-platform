package com.prediction.socialnetwork.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.domain.SocialNetworkState;
import com.prediction.socialnetwork.domain.SocialNetworkStateCatalog;
import com.prediction.socialnetwork.export.SocialNetworkJsonExportAssembler;
import com.prediction.socialnetwork.export.SocialNetworkExportMetadata;
import com.prediction.socialnetwork.export.SocialNetworkCsvSerializer;
import com.prediction.socialnetwork.export.SocialNetworkPrintableSummaryGenerator;
import com.prediction.socialnetwork.export.SocialNetworkExportMessages;
import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.text.SocialNetworkTextAnalysis;
import com.prediction.socialnetwork.normalization.NormalizedRelationship;
import com.prediction.socialnetwork.normalization.NormalizedTweet;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import com.prediction.socialnetwork.quality.QualityIssue;
import com.prediction.socialnetwork.quality.SocialNetworkQualityReport;
import com.prediction.socialnetwork.network.CrossMetricAnalysisCalculator;
import com.prediction.socialnetwork.network.CommunityDetectionResult;
import com.prediction.socialnetwork.network.CommunityDetectionStatus;
import com.prediction.socialnetwork.network.NetworkGraphView;
import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkMetrics;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import com.prediction.socialnetwork.network.NodeMetricDistributionCalculator;
import com.prediction.socialnetwork.network.PageRankResult;
import com.prediction.socialnetwork.network.StructuralRoleAnalysis;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@RestController
@RequestMapping("/api/social-network")
public class SocialNetworkController {

    private static final int MAX_INLINE_EXPORT_BYTES = 2 * 1024 * 1024;
    private static final int MAX_LEGACY_TWEET_EXPORT_ROWS = 5000;
    private static final int MODULE_RANKING_LIMIT = 20;
    private static final int MODULE_INTERACTION_LIMIT = 10;
    private static final int MODULE_TOPIC_LIMIT = 30;
    private static final int MODULE_VISIBLE_SERIES_LIMIT = 30;

    private final SocialNetworkDatasetRegistry registry;
    private final SocialNetworkWorkspaceService workspace;
    private final CliqueEnumerationWorkflowService cliqueWorkflow;
    private final LouvainAnalysisWorkflowService louvainWorkflow;
    private final SocialNetworkEvidenceFactSummaryService evidenceSummaryService;
    private final ObjectMapper objectMapper;
    private final SocialNetworkJsonExportAssembler jsonExportAssembler =
            new SocialNetworkJsonExportAssembler();
    private final SocialNetworkCsvSerializer csvSerializer =
            new SocialNetworkCsvSerializer();
    private final SocialNetworkPrintableSummaryGenerator printableSummaryGenerator =
            new SocialNetworkPrintableSummaryGenerator();

    public SocialNetworkController(
            SocialNetworkDatasetRegistry registry,
            SocialNetworkWorkspaceService workspace,
            CliqueEnumerationWorkflowService cliqueWorkflow,
            LouvainAnalysisWorkflowService louvainWorkflow,
            SocialNetworkEvidenceFactSummaryService evidenceSummaryService,
            ObjectMapper objectMapper) {
        this.registry = registry;
        this.workspace = workspace;
        this.cliqueWorkflow = cliqueWorkflow;
        this.louvainWorkflow = louvainWorkflow;
        this.evidenceSummaryService = evidenceSummaryService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/datasets")
    public SocialNetworkApiResponse<Map<String, Object>> datasets() {
        SocialNetworkWorkspaceSnapshot snapshot = workspace.current();
        Map<String, Object> item = datasetSummary(snapshot.dataset(), snapshot.quality());
        return success(Map.of("items", List.of(item)), snapshot);
    }

    @GetMapping("/datasets/{datasetId}")
    public SocialNetworkApiResponse<Map<String, Object>> dataset(
            @PathVariable String datasetId) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        Map<String, Object> data = datasetSummary(snapshot.dataset(), snapshot.quality());
        data.put("qualitySummary", qualitySummary(snapshot.quality()));
        // Retain machine-readable capability data for API clients; the standalone UI matrix is retired.
        data.put("capabilitySummary", Map.of(
                "items", snapshot.capabilities().items(),
                "capabilityVersion", snapshot.capabilities().capabilityVersion()));
        return success(data, snapshot);
    }

    /**
     * Provides per-module availability for API clients and automated checks.
     * The frontend no longer renders this as a standalone capability matrix.
     */
    @GetMapping("/datasets/{datasetId}/capabilities")
    public SocialNetworkApiResponse<Map<String, Object>> capabilities(
            @PathVariable String datasetId) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        return success(Map.of(
                "capabilityVersion", snapshot.capabilities().capabilityVersion(),
                "items", snapshot.capabilities().items()), snapshot);
    }

    @GetMapping("/datasets/{datasetId}/quality/summary")
    public SocialNetworkApiResponse<Map<String, Object>> quality(
            @PathVariable String datasetId) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        return success(qualitySummary(snapshot.quality()), snapshot);
    }

    @GetMapping("/datasets/{datasetId}/network/summary")
    public SocialNetworkApiResponse<Map<String, Object>> networkSummary(
            @PathVariable String datasetId) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        return success(networkSummary(snapshot), snapshot);
    }

    @PostMapping("/datasets/{datasetId}/network/cliques")
    public ResponseEntity<SocialNetworkApiResponse<CliqueEnumerationWorkflowView>>
            requestCliqueAnalysis(
                    @PathVariable String datasetId,
                    @RequestParam(defaultValue = "false") boolean force) {
        CliqueEnumerationWorkflowView view = cliqueWorkflow.request(datasetId, force);
        return ResponseEntity.accepted().body(cliqueSuccess(view));
    }

    @GetMapping("/datasets/{datasetId}/network/cliques")
    public SocialNetworkApiResponse<CliqueEnumerationWorkflowView> cliqueAnalysis(
            @PathVariable String datasetId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int pageSize) {
        return cliqueSuccess(cliqueWorkflow.status(datasetId, offset, pageSize));
    }

    @PostMapping("/datasets/{datasetId}/network/louvain")
    public ResponseEntity<SocialNetworkApiResponse<LouvainAnalysisWorkflowView>>
            requestLouvainAnalysis(
                    @PathVariable String datasetId,
                    @RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.accepted().body(
                louvainSuccess(louvainWorkflow.request(datasetId, force)));
    }

    @GetMapping("/datasets/{datasetId}/network/louvain")
    public SocialNetworkApiResponse<LouvainAnalysisWorkflowView> louvainAnalysis(
            @PathVariable String datasetId) {
        return louvainSuccess(louvainWorkflow.status(datasetId));
    }

    @GetMapping("/datasets/{datasetId}/network/structure-roles")
    public SocialNetworkApiResponse<Map<String, Object>> structureRoles(
            @PathVariable String datasetId,
            @RequestParam(defaultValue = "20") int limit) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        return success(structureRolesData(snapshot, limit), snapshot);
    }

    private Map<String, Object> structureRolesData(
            SocialNetworkWorkspaceSnapshot snapshot,
            int limit) {
        StructuralRoleAnalysis analysis = snapshot.network().structuralRoles();
        Map<String, NormalizedUser> profilesById = profilesByUserId(snapshot.users());
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<StructuralRankingValue> coreValues = analysis.kCoreRanking().stream()
                .map(entry -> new StructuralRankingValue(
                        entry.nodeId(), entry.nodeType(), entry.coreNumber(), null))
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("algorithmVersion", analysis.algorithmVersion());
        data.put("analysisSnapshot",
                SocialNetworkNodeEvidenceAssembler.snapshotIdentity(snapshot));
        data.put("scope", "完整规范化网络快照；包含孤立有效用户及有效关系涉及的外部节点；"
                + "结构指标采用无向无权简单投影");
        data.put("nodeCount", analysis.nodeCount());
        data.put("undirectedTieCount", analysis.undirectedTieCount());
        data.put("limit", boundedLimit);
        data.put("kCore", structuralRankingBlock(
                "k-core 层级",
                analysis.nodeCount() == 0
                        ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                        : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE,
                structuralRankingItems(
                        coreValues, boundedLimit, "按 core number 降序排列", snapshot),
                analysis.nodeCount() == 0 ? "当前完整网络范围内没有节点。" : null));
        data.put("kCoreDistribution", kCoreDistributionBlock(analysis, profilesById));
        data.put("metricDistributions", metricDistributions(snapshot, analysis));

        boolean betweennessAvailable = analysis.nodeCount() >= 3;
        List<StructuralRankingValue> betweennessValues = betweennessAvailable
                ? analysis.betweennessRanking().stream()
                        .map(entry -> new StructuralRankingValue(
                                entry.nodeId(),
                                entry.nodeType(),
                                entry.normalizedBetweenness(),
                                entry.rawBetweenness()))
                        .toList()
                : List.of();
        data.put("betweenness", structuralRankingBlock(
                "归一化介数中心性",
                betweennessAvailable
                        ? com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE
                        : com.prediction.socialnetwork.domain.SocialNetworkState.UNAVAILABLE,
                structuralRankingItems(
                        betweennessValues,
                        boundedLimit,
                        "按归一化介数中心性降序排列",
                        snapshot),
                betweennessAvailable
                        ? null
                        : "节点数不足 3，无法计算归一化介数中心性。"));
        data.put("crossCommunityEvidence", structuralRankingBlock(
                "跨社群关系数",
                com.prediction.socialnetwork.domain.SocialNetworkState.UNAVAILABLE,
                List.of(),
                "社群结果尚未通过负责人复核，暂不提供跨社群辅助证据。"));
        data.put("crossMetric", crossMetricBlock(snapshot, analysis, profilesById));
        return data;
    }

    @GetMapping("/datasets/{datasetId}/network/evidence/nodes")
    public SocialNetworkApiResponse<Map<String, Object>> networkNodeEvidence(
            @PathVariable String datasetId) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        Map<String, Object> evidence =
                new SocialNetworkNodeEvidenceAssembler().assemble(snapshot);
        return success(evidence, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/network/evidence/summary")
    public SocialNetworkApiResponse<SocialNetworkEvidenceFactSummaryGenerator.Summary>
            networkEvidenceSummary(@PathVariable String datasetId) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        return success(evidenceSummaryService.get(snapshot), snapshot);
    }

    @GetMapping("/datasets/{datasetId}/network/graph")
    public SocialNetworkApiResponse<Map<String, Object>> networkGraph(
            @PathVariable String datasetId,
            @RequestParam(defaultValue = "100") int maxNodes,
            @RequestParam(defaultValue = "500") int maxEdges,
            @RequestParam(defaultValue = "true") boolean includeExternalNodes) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        int nodeLimit = Math.max(1, Math.min(maxNodes, 1000));
        int edgeLimit = Math.max(1, Math.min(maxEdges, 15000));
        Map<String, NormalizedUser> profilesById = new LinkedHashMap<>();
        snapshot.users().stream()
                .filter(user -> user.userId() != null && !user.userId().isBlank())
                .forEach(user -> profilesById.putIfAbsent(user.userId(), user));

        List<NetworkNode> scopedNodes = snapshot.network().nodes().stream()
                .filter(node -> includeExternalNodes || node.nodeType() == NetworkNodeType.PROFILE)
                .toList();
        Set<String> scopedIds = scopedNodes.stream()
                .map(NetworkNode::id)
                .collect(java.util.stream.Collectors.toSet());
        List<NetworkEdge> scopedEdges = snapshot.network().edges().stream()
                .filter(edge -> scopedIds.contains(edge.source())
                        && scopedIds.contains(edge.target()))
                .toList();
        boolean nodesTruncated = scopedNodes.size() > nodeLimit;
        List<NetworkNode> nodes = scopedNodes.stream().limit(nodeLimit).toList();
        Set<String> returnedIds = nodes.stream()
                .map(NetworkNode::id)
                .collect(java.util.stream.Collectors.toSet());
        List<NetworkEdge> edgesWithinReturnedNodes = scopedEdges.stream()
                .filter(edge -> returnedIds.contains(edge.source())
                        && returnedIds.contains(edge.target()))
                .toList();
        boolean edgesTruncated = edgesWithinReturnedNodes.size() > edgeLimit;
        List<NetworkEdge> edges = edgesWithinReturnedNodes.stream().limit(edgeLimit).toList();
        boolean truncated = nodesTruncated || edgesTruncated;
        CommunityDetectionResult communityDetection =
                snapshot.network().communityDetection();
        boolean communitiesAvailable =
                communityDetection.status() == CommunityDetectionStatus.COMPLETED;
        Map<String, String> communityByNodeId = communityDetection.communityByNodeId();
        List<Map<String, Object>> globalCommunities = communitiesAvailable
                ? communitySummary(scopedNodes, scopedEdges, communityByNodeId)
                : List.of();
        List<Map<String, Object>> displayedCommunities = communitiesAvailable
                ? communitySummary(nodes, edges, communityByNodeId)
                : List.of();
        Integer globalCommunityCount =
                communitiesAvailable ? globalCommunities.size() : null;
        Integer displayedCommunityCount =
                communitiesAvailable ? displayedCommunities.size() : null;
        Integer globalUnassignedNodeCount = communitiesAvailable
                ? unassignedNodeCount(scopedNodes, communityByNodeId)
                : null;
        Integer displayedUnassignedNodeCount = communitiesAvailable
                ? unassignedNodeCount(nodes, communityByNodeId)
                : null;
        List<String> truncatedBy = new ArrayList<>();
        if (nodesTruncated) {
            truncatedBy.add("maxNodes");
        }
        if (edgesTruncated) {
            truncatedBy.add("maxEdges");
        }
        List<String> truncationDetails = new ArrayList<>();
        if (nodesTruncated) {
            truncationDetails.add("节点范围 " + scopedNodes.size() + " 个，本次返回 " + nodes.size() + " 个");
        }
        if (edgesTruncated) {
            truncationDetails.add("当前返回节点间有向关系 " + edgesWithinReturnedNodes.size()
                    + " 条，本次返回 " + edges.size() + " 条");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scope", includeExternalNodes
                ? "全量规范化关系网络（包含外部节点）"
                : "仅资料用户组成的规范化关系网络（排除外部节点及其关联边）");
        data.put("graphScope", includeExternalNodes
                ? "full_normalized_network"
                : "profile_nodes_only");
        data.put("state", truncated
                ? com.prediction.socialnetwork.domain.SocialNetworkState.LIMITED
                : scopedNodes.isEmpty()
                    ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                    : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("nodes", nodes.stream()
                .map(node -> nodeSummary(
                        node, profilesById, communityByNodeId.get(node.id())))
                .toList());
        data.put("edges", edges);
        data.put("communityAnalysisStatus",
                communityDetection.status().name().toLowerCase(Locale.ROOT));
        data.put("globalCommunities", globalCommunities);
        data.put("displayedCommunities", displayedCommunities);
        data.put("globalCommunityCount", globalCommunityCount);
        data.put("displayedCommunityCount", displayedCommunityCount);
        data.put("globalUnassignedNodeCount", globalUnassignedNodeCount);
        data.put("displayedUnassignedNodeCount", displayedUnassignedNodeCount);
        // Keep the original names as aliases for the graph currently drawn on the canvas.
        data.put("communities", displayedCommunities);
        data.put("communityCount", displayedCommunityCount);
        data.put("unassignedNodeCount", displayedUnassignedNodeCount);
        data.put("communityAlgorithm", communityDetection.algorithm());
        data.put("communityProjection", communityDetection.relationProjection());
        data.put("communityDirectionNote",
                "社群识别将关注关系投影为无向联系；画布中的关注箭头仍保留原始方向。");
        data.put("communityBoundary",
                "社群表示当前关系结构中相对紧密的节点集合，不代表真实组织或群体身份。");
        data.put("communityIterations", communitiesAvailable
                ? communityDetection.iterations()
                : null);
        data.put("communityConverged", communitiesAvailable
                ? communityDetection.converged()
                : null);
        data.put("communityAssignmentScope", communitiesAvailable
                ? "full_normalized_network"
                : null);
        data.put("totalNodeCount", scopedNodes.size());
        data.put("totalEdgeCount", scopedEdges.size());
        data.put("returnedNodeCount", nodes.size());
        data.put("returnedEdgeCount", edges.size());
        // Keep the original count names as aliases for the returned arrays.
        data.put("nodeCount", nodes.size());
        data.put("edgeCount", edges.size());
        data.put("truncated", truncated);
        data.put("truncatedBy", truncatedBy);
        data.put("truncationReason", truncated
                ? "本次画布结果受请求上限限制：" + String.join("；", truncationDetails) + "。"
                : null);
        data.put("includeExternalNodes", includeExternalNodes);
        data.put("limits", Map.of("maxNodes", nodeLimit, "maxEdges", edgeLimit));
        return success(data, snapshot);
    }

    private static int unassignedNodeCount(
            List<NetworkNode> nodes, Map<String, String> communityByNodeId) {
        return (int) nodes.stream()
                .filter(node -> !communityByNodeId.containsKey(node.id()))
                .count();
    }

    @GetMapping("/datasets/{datasetId}/users/search")
    public SocialNetworkApiResponse<Map<String, Object>> searchUsers(
            @PathVariable String datasetId,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "20") int limit) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        String normalizedQuery = query == null ? "" : query.strip().toLowerCase();
        List<Map<String, Object>> items = snapshot.users().stream()
                .filter(user -> isSearchableUser(user, normalizedQuery))
                .map(user -> userSummary(user, snapshot))
                .limit(boundedLimit)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query == null ? "" : query);
        data.put("limit", boundedLimit);
        data.put("items", items);
        data.put("matchedCount", items.size());
        data.put("state", items.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("searchScope", List.of("user_id", "username", "display_name"));
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/users/{userId}")
    public SocialNetworkApiResponse<Map<String, Object>> userDetail(
            @PathVariable String datasetId,
            @PathVariable String userId) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        Map<String, Object> data = userDetailData(snapshot, userId);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/network/local")
    public SocialNetworkApiResponse<Map<String, Object>> localNetwork(
            @PathVariable String datasetId,
            @RequestParam String centerNodeId,
            @RequestParam(defaultValue = "1") int depth,
            @RequestParam(defaultValue = "30") int topK,
            @RequestParam(defaultValue = "true") boolean includeExternalNodes) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        NetworkGraphView view = new com.prediction.socialnetwork.network.SocialNetworkCalculator()
                .localView(snapshot.network(), centerNodeId, depth, topK, includeExternalNodes);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("centerNodeId", view.centerNodeId());
        data.put("depth", view.depth());
        data.put("topK", view.topK());
        data.put("includeExternalNodes", view.includeExternalNodes());
        data.put("truncated", view.truncated());
        data.put("truncationReason", view.truncationReason());
        data.put("nodes", view.nodes().stream()
                .map(node -> nodeSummary(node, snapshot))
                .toList());
        data.put("edges", view.edges());
        data.put("state", view.nodes().isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : view.truncated()
                    ? com.prediction.socialnetwork.domain.SocialNetworkState.LIMITED
                    : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("scope", "中心节点局部关系网络");
        data.put("limits", Map.of(
                "maxNodes", com.prediction.socialnetwork.network.SocialNetworkCalculator.MAX_GRAPH_NODES,
                "maxEdges", com.prediction.socialnetwork.network.SocialNetworkCalculator.MAX_GRAPH_EDGES));
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/rankings")
    public SocialNetworkApiResponse<Map<String, Object>> rankings(
            @PathVariable String datasetId,
            @RequestParam(defaultValue = "indegree") String metric,
            @RequestParam(defaultValue = "20") int limit) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        String normalizedMetric = metric == null ? "indegree" : metric.strip().toLowerCase();
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<RankingValue> values = rankingValues(snapshot, normalizedMetric);
        SocialNetworkMetricCatalog.MetricDefinition definition =
                SocialNetworkMetricCatalog.require(normalizedMetric);
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        double previous = Double.NaN;
        int rank = 0;
        for (int index = 0; index < Math.min(boundedLimit, values.size()); index++) {
            RankingValue value = values.get(index);
            if (Double.isNaN(previous) || Double.compare(previous, value.value()) != 0) {
                rank = index + 1;
                previous = value.value();
            }
            items.add(rankingItem(value, rank, normalizedMetric, snapshot));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metric", normalizedMetric);
        data.put("metricType", definition.type());
        data.put("label", definition.label());
        data.put("sampleSize", values.size());
        data.put("scope", definition.scope());
        data.put("metricDefinition", metricDefinition(definition));
        data.put("items", items);
        data.put("available", !values.isEmpty());
        data.put("state", values.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("reason", values.isEmpty() ? "当前指标没有可用样本。" : null);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/interactions/summary")
    public SocialNetworkApiResponse<Map<String, Object>> interactionSummary(
            @PathVariable String datasetId) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scale", snapshot.interactions());
        data.put("scope", "去重且纳入分析的推文样本；未归属来源保留在全局统计中");
        data.put("metrics", snapshot.interactions().metrics());
        data.put("metricDefinitions", metricDefinitions(
                List.of("like_count", "reply_count", "repost_count")));
        data.put("state", snapshot.interactions().available()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE
                : com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/interactions/tweets")
    public SocialNetworkApiResponse<Map<String, Object>> interactionTweets(
            @PathVariable String datasetId,
            @RequestParam(defaultValue = "like_count") String metric,
            @RequestParam(defaultValue = "20") int limit) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        String field = interactionMetric(metric);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<Map<String, Object>> allItems = snapshot.tweets().stream()
                .filter(NormalizedTweet::analysisIncluded)
                .map(tweet -> interactionValue(tweet, field))
                .filter(value -> value != null)
                .sorted((left, right) -> {
                    int valueOrder = Long.compare(
                            (Long) right.get("value"), (Long) left.get("value"));
                    return valueOrder != 0 ? valueOrder
                            : String.valueOf(left.get("tweetId"))
                                    .compareTo(String.valueOf(right.get("tweetId")));
                })
                .toList();
        List<Map<String, Object>> items = allItems.stream()
                .limit(boundedLimit)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metric", field);
        data.put("metricDefinition",
                metricDefinition(SocialNetworkMetricCatalog.require(field)));
        data.put("limit", boundedLimit);
        data.put("totalCount", allItems.size());
        data.put("truncated", allItems.size() > boundedLimit);
        data.put("scope", "去重且纳入分析的推文样本");
        data.put("items", items);
        data.put("available", !items.isEmpty());
        data.put("state", items.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("reason", items.isEmpty() ? "当前指标没有可用推文样本。" : null);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/interactions/users")
    public SocialNetworkApiResponse<Map<String, Object>> interactionUsers(
            @PathVariable String datasetId,
            @RequestParam(defaultValue = "like_count") String metric,
            @RequestParam(defaultValue = "20") int limit) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        String field = interactionMetric(metric);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        Map<String, List<Long>> grouped = new LinkedHashMap<>();
        snapshot.tweets().stream()
                .filter(tweet -> tweet.analysisIncluded()
                        && tweet.authorStatus() == AssociationStatus.LINKED
                        && tweet.authorUserId() != null)
                .forEach(tweet -> {
                    Long value = parseLong(tweet.fields().get(field));
                    if (value != null && value >= 0) {
                        grouped.computeIfAbsent(tweet.authorUserId(),
                                ignored -> new java.util.ArrayList<>()).add(value);
                    }
                });
        List<Map<String, Object>> allItems = grouped.entrySet().stream()
                .map(entry -> {
                    List<Long> values = entry.getValue();
                    long total = values.stream().mapToLong(Long::longValue).sum();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("userId", entry.getKey());
                    item.put("total", total);
                    item.put("average", total * 1.0 / values.size());
                    item.put("median", median(values));
                    item.put("sampleCount", values.size());
                    return item;
                })
                .sorted((left, right) -> {
                    int totalOrder = Double.compare(
                            ((Number) right.get("total")).doubleValue(),
                            ((Number) left.get("total")).doubleValue());
                    return totalOrder != 0 ? totalOrder
                            : String.valueOf(left.get("userId"))
                                    .compareTo(String.valueOf(right.get("userId")));
                })
                .toList();
        List<Map<String, Object>> items = allItems.stream()
                .limit(boundedLimit)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metric", field);
        data.put("metricDefinition",
                metricDefinition(SocialNetworkMetricCatalog.require(field)));
        data.put("limit", boundedLimit);
        data.put("totalCount", allItems.size());
        data.put("truncated", allItems.size() > boundedLimit);
        data.put("scope", "已关联用户的去重推文样本");
        data.put("items", items);
        data.put("available", !items.isEmpty());
        data.put("state", items.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("reason", items.isEmpty() ? "当前指标没有可用用户样本。" : null);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/interactions/timeseries")
    public SocialNetworkApiResponse<Map<String, Object>> interactionTimeseries(
            @PathVariable String datasetId,
            @RequestParam(defaultValue = "like_count") String metric) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        String field = interactionMetric(metric);
        Map<String, List<Long>> grouped = new java.util.TreeMap<>();
        int missingDateCount = 0;
        for (NormalizedTweet tweet : snapshot.tweets()) {
            if (!tweet.analysisIncluded()) {
                continue;
            }
            Long value = parseLong(tweet.fields().get(field));
            String date = tweet.fields().get("created_at");
            if (value == null || value < 0 || date == null || date.isBlank()) {
                if (date == null || date.isBlank()) {
                    missingDateCount++;
                }
                continue;
            }
            grouped.computeIfAbsent(date.strip(), ignored -> new java.util.ArrayList<>())
                    .add(value);
        }
        List<Map<String, Object>> items = grouped.entrySet().stream()
                .map(entry -> {
                    long total = entry.getValue().stream().mapToLong(Long::longValue).sum();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("period", entry.getKey());
                    item.put("total", total);
                    item.put("average", total * 1.0 / entry.getValue().size());
                    item.put("sampleCount", entry.getValue().size());
                    return item;
                })
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metric", field);
        data.put("metricDefinition",
                metricDefinition(SocialNetworkMetricCatalog.require(field)));
        data.put("items", items);
        data.put("totalCount", items.size());
        data.put("missingDateCount", missingDateCount);
        data.put("scope", "按 created_at 聚合的去重推文样本");
        data.put("available", !items.isEmpty());
        data.put("state", items.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("reason", items.isEmpty() ? "当前指标没有可用时间样本。" : null);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/text/summary")
    public SocialNetworkApiResponse<Map<String, Object>> textSummary(
            @PathVariable String datasetId) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        SocialNetworkTextAnalysis.Result result = snapshot.textAnalysis();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("languageCounts", result.languageCounts());
        data.put("analysisSampleCount", result.analysisSampleCount());
        data.put("validTextSampleCount", result.validTextSampleCount());
        data.put("emptyTextSampleCount", result.emptyTextSampleCount());
        data.put("textProcessingVersion", result.textProcessingVersion());
        data.put("stopwordsVersion", result.stopwordsVersion());
        data.put("tokenizerVersion", result.tokenizerVersion());
        data.put("tfidfVersion", result.tfidfVersion());
        data.put("originalTextPreserved", true);
        data.put("available", result.validTextSampleCount() > 0);
        data.put("state", result.validTextSampleCount() > 0
                ? com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE
                : com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/text/keywords")
    public SocialNetworkApiResponse<Map<String, Object>> textKeywords(
            @PathVariable String datasetId,
            @RequestParam(defaultValue = "all") String language,
            @RequestParam(defaultValue = "30") int limit) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<SocialNetworkTextAnalysis.Keyword> items = snapshot.textAnalysis().keywords().stream()
                .filter(keyword -> "all".equalsIgnoreCase(language)
                        || keyword.language().equalsIgnoreCase(language))
                .limit(boundedLimit)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("language", language.toLowerCase());
        data.put("limit", boundedLimit);
        data.put("items", items);
        data.put("scope", "去重且纳入分析的有效文本推文");
        data.put("available", !items.isEmpty());
        data.put("state", items.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("reason", items.isEmpty() ? "当前语言范围没有有效关键词。" : null);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/text/tfidf")
    public SocialNetworkApiResponse<Map<String, Object>> textTfidf(
            @PathVariable String datasetId,
            @RequestParam(defaultValue = "30") int limit) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("limit", boundedLimit);
        data.put("items", snapshot.textAnalysis().tfidf().stream()
                .limit(boundedLimit).toList());
        data.put("formula", "tfidf=(1+log(raw_tf))*[log((N+1)/(df+1))+1]");
        data.put("scope", "文档单位为一条去重且有效文本推文");
        data.put("available", !snapshot.textAnalysis().tfidf().isEmpty());
        data.put("state", snapshot.textAnalysis().tfidf().isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/text/search")
    public SocialNetworkApiResponse<Map<String, Object>> textSearch(
            @PathVariable String datasetId,
            @RequestParam String query,
            @RequestParam(defaultValue = "20") int limit) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        String normalizedQuery = query == null ? "" : query.strip().toLowerCase();
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<Map<String, Object>> items = snapshot.textAnalysis().documents().stream()
                .filter(document -> document.tokens().stream()
                        .anyMatch(token -> token.equals(normalizedQuery)))
                .limit(boundedLimit)
                .map(document -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("tweetId", document.tweetId());
                    item.put("authorUserId", document.authorUserId());
                    item.put("language", document.language());
                    item.put("textOriginal", document.textOriginal());
                    item.put("textNormalized", document.textNormalized());
                    item.put("status", document.status());
                    return item;
                })
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("normalizedQuery", normalizedQuery);
        data.put("items", items);
        data.put("matchedCount", items.size());
        data.put("matchMode", "exact-token");
        data.put("available", !items.isEmpty());
        data.put("state", items.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("reason", items.isEmpty() ? "没有精确词元匹配的推文。" : null);
        return success(data, snapshot);
    }

    @GetMapping("/datasets/{datasetId}/text/topic")
    public SocialNetworkApiResponse<Map<String, Object>> textTopic(
            @PathVariable String datasetId,
            @RequestParam String query,
            @RequestParam(defaultValue = "30") int limit) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        String normalizedQuery = query == null ? "" : query.strip().toLowerCase();
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        Set<String> matchedTweetIds = snapshot.textAnalysis().documents().stream()
                .filter(document -> document.tokens().stream()
                        .anyMatch(token -> token.equals(normalizedQuery)))
                .map(SocialNetworkTextAnalysis.Document::tweetId)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, com.prediction.socialnetwork.normalization.NormalizedTweet> tweets =
                snapshot.tweets().stream().collect(java.util.stream.Collectors.toMap(
                        com.prediction.socialnetwork.normalization.NormalizedTweet::tweetId,
                        tweet -> tweet,
                        (left, right) -> left));
        Map<String, Integer> userMatches = new LinkedHashMap<>();
        Map<String, long[]> userInteractions = new LinkedHashMap<>();
        List<Map<String, Object>> matchedTweets = new ArrayList<>();
        for (String tweetId : matchedTweetIds) {
            var tweet = tweets.get(tweetId);
            if (tweet == null) {
                continue;
            }
            if (tweet.authorStatus() == AssociationStatus.LINKED
                    && tweet.authorUserId() != null) {
                userMatches.merge(tweet.authorUserId(), 1, Integer::sum);
            }
            long[] interaction = userInteractions.computeIfAbsent(
                    tweet.authorUserId() == null ? "" : tweet.authorUserId(),
                    ignored -> new long[3]);
            interaction[0] += parseLongOrZero(tweet.fields().get("like_count"));
            interaction[1] += parseLongOrZero(tweet.fields().get("reply_count"));
            interaction[2] += parseLongOrZero(tweet.fields().get("repost_count"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tweetId", tweet.tweetId());
            item.put("authorUserId", tweet.authorUserId());
            item.put("authorStatus", tweet.authorStatus().name().toLowerCase());
            item.put("textOriginal", tweet.fields().getOrDefault("text", ""));
            item.put("createdAt", tweet.fields().get("created_at"));
            item.put("likeCount", parseLongOrZero(tweet.fields().get("like_count")));
            item.put("replyCount", parseLongOrZero(tweet.fields().get("reply_count")));
            item.put("repostCount", parseLongOrZero(tweet.fields().get("repost_count")));
            matchedTweets.add(item);
        }
        matchedTweets.sort((left, right) -> {
            int likesOrder = Long.compare(
                    ((Number) right.get("likeCount")).longValue(),
                    ((Number) left.get("likeCount")).longValue());
            return likesOrder != 0 ? likesOrder
                    : String.valueOf(left.get("tweetId"))
                            .compareTo(String.valueOf(right.get("tweetId")));
        });
        Set<String> relatedUsers = userMatches.keySet();
        List<Map<String, Object>> users = userMatches.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(boundedLimit)
                .map(entry -> {
                    long[] values = userInteractions.get(entry.getKey());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("userId", entry.getKey());
                    item.put("matchedTweetCount", entry.getValue());
                    item.put("likeTotal", values[0]);
                    item.put("replyTotal", values[1]);
                    item.put("repostTotal", values[2]);
                    return item;
                }).toList();
        List<Map<String, Object>> allEdges = snapshot.relationships().stream()
                .filter(relation -> relation.analysisIncluded()
                        && relatedUsers.contains(relation.sourceUserId())
                        && relatedUsers.contains(relation.targetUserId()))
                .map(relation -> {
                    Map<String, Object> edge = new LinkedHashMap<>();
                    edge.put("source", relation.sourceUserId());
                    edge.put("target", relation.targetUserId());
                    edge.put("relationType", "following");
                    return edge;
                }).toList();
        List<Map<String, Object>> edges = allEdges.stream().limit(500).toList();
        Map<String, Object> network = new LinkedHashMap<>();
        network.put("nodeCount", relatedUsers.size());
        network.put("edgeCount", edges.size());
        network.put("totalEdgeCount", allEdges.size());
        network.put("truncated", allEdges.size() > edges.size());
        network.put("edges", edges);
        network.put("scope", "主题相关已关联用户之间的真实关注关系");
        network.put("externalNodesIncluded", false);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("normalizedQuery", normalizedQuery);
        data.put("matchedTweetCount", matchedTweets.size());
        data.put("returnedTweetCount", Math.min(boundedLimit, matchedTweets.size()));
        data.put("relatedUserCount", relatedUsers.size());
        data.put("returnedUserCount", users.size());
        data.put("tweets", matchedTweets.stream().limit(boundedLimit).toList());
        data.put("users", users);
        data.put("network", network);
        data.put("textProcessingVersion", snapshot.textAnalysis().textProcessingVersion());
        data.put("matchMode", "exact-token");
        data.put("available", !matchedTweets.isEmpty());
        data.put("state", matchedTweets.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : relatedUsers.isEmpty()
                    ? com.prediction.socialnetwork.domain.SocialNetworkState.LIMITED
                    : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("reason", matchedTweets.isEmpty() ? "没有精确词元匹配的推文。" : null);
        return success(data, snapshot);
    }

    @PostMapping("/datasets/{datasetId}/exports")
    public ResponseEntity<SocialNetworkApiResponse<Map<String, Object>>> export(
            @PathVariable String datasetId,
            @RequestBody Map<String, Object> request) {
        SocialNetworkWorkspaceSnapshot snapshot = requestSnapshot(datasetId);
        String format = textValue(request, "format", "json").toLowerCase();
        String exportType = textValue(request, "exportType", "").toLowerCase();
        if (!List.of("json", "csv", "print").contains(format) || exportType.isBlank()) {
            return badRequest(SocialNetworkExportMessages.BAD_REQUEST_CODE,
                    SocialNetworkExportMessages.BAD_REQUEST_MESSAGE);
        }
        Map<String, Object> query = mapValue(request.get("query"));
        Map<String, Object> effectiveQuery;
        try {
            effectiveQuery = effectiveExportQuery(exportType, query);
        } catch (IllegalArgumentException exception) {
            return badRequest("SN_BAD_REQUEST", exception.getMessage());
        }
        Map<String, Object> scope = new LinkedHashMap<>(mapValue(request.get("scope")));
        try {
            Map<String, Object> result;
            if ("tweets".equals(exportType)) {
                long tweetCount = snapshot.tweets().stream()
                        .filter(NormalizedTweet::analysisIncluded)
                        .count();
                if (tweetCount > MAX_LEGACY_TWEET_EXPORT_ROWS
                        || estimatedTweetExportBytes(snapshot) > MAX_INLINE_EXPORT_BYTES) {
                    return exportTooLarge(snapshot);
                }
                scope.put("range", "全部去重且纳入分析的推文样本");
                result = jsonExportAssembler.assemble(
                        snapshot, exportType, effectiveQuery, scope);
            } else {
                Object moduleData = moduleExportData(
                        datasetId, snapshot, exportType, effectiveQuery);
                int resultCount = exportSampleCount(exportType, moduleData);
                if (resultCount <= 0) {
                    throw new IllegalStateException("SN_EXPORT_EMPTY_RESULT");
                }
                scope.put("range", "当前模块结果；按页面展示限制截取");
                scope.put("calculationScope", switch (exportType) {
                    case "network", "users" -> "完整规范化网络快照";
                    case "interactions" -> "当前互动指标及页面展示范围";
                    case "topic" -> "当前精确词元查询及页面展示范围";
                    case "quality" -> "当前数据集质量报告";
                    default -> "当前模块结果";
                });
                scope.put("resultCount", resultCount);
                scope.put("truncated", containsTruncatedResult(moduleData));
                result = jsonExportAssembler.assemblePrepared(
                        snapshot, exportType, effectiveQuery, scope, moduleData, resultCount);
            }
            SocialNetworkExportMetadata metadata =
                    (SocialNetworkExportMetadata) result.get("metadata");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("exportId", UUID.randomUUID().toString());
            data.put("exportType", exportType);
            data.put("status", SocialNetworkExportMessages.STATUS_COMPLETED);
            data.put("statusLabel", SocialNetworkExportMessages.STATUS_COMPLETED_LABEL);
            data.put("expiresAt", null);
            data.put("downloadAvailable", false);
            data.put("format", format);
            data.put("metadata", metadata);
            data.put("resultState", Boolean.TRUE.equals(scope.get("truncated"))
                    ? "limited" : "available");
            if ("csv".equals(format)) {
                data.put("contentType", "text/csv; charset=UTF-8");
                data.put("result", csvResult(
                        exportType, result.get("data"), metadata));
            } else if ("print".equals(format)) {
                data.put("contentType", "text/plain; charset=UTF-8");
                data.put("result", printableSummaryGenerator.generate(
                        metadata, exportType, result.get("data")));
            } else {
                data.put("contentType", "application/json; charset=UTF-8");
                data.put("result", result.get("data"));
            }
            Object contentForLength = "json".equals(format)
                    ? Map.of(
                            "exportType", exportType,
                            "metadata", metadata,
                            "data", data.get("result"))
                    : data.get("result");
            int contentLength = contentLength(format, contentForLength);
            if (contentLength > MAX_INLINE_EXPORT_BYTES) {
                return exportTooLarge(snapshot);
            }
            data.put("contentLength", contentLength);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    success(data, snapshot));
        } catch (IllegalArgumentException exception) {
            return badRequest("SN_BAD_REQUEST", exception.getMessage());
        } catch (IllegalStateException exception) {
            return ResponseEntity.unprocessableEntity().body(
                    SocialNetworkApiResponse.error(
                            UUID.randomUUID().toString(),
                            SocialNetworkExportMessages.EMPTY_RESULT_CODE,
                            SocialNetworkExportMessages.EMPTY_RESULT_MESSAGE,
                            meta(snapshot)));
        }
    }

    private long estimatedTweetExportBytes(SocialNetworkWorkspaceSnapshot snapshot) {
        long estimatedBytes = 0;
        for (NormalizedTweet tweet : snapshot.tweets()) {
            if (!tweet.analysisIncluded()) {
                continue;
            }
            estimatedBytes += 256L;
            for (String value : tweet.fields().values()) {
                if (value != null) {
                    estimatedBytes += value.length() * 3L;
                    if (estimatedBytes > MAX_INLINE_EXPORT_BYTES) {
                        return estimatedBytes;
                    }
                }
            }
            if (estimatedBytes > MAX_INLINE_EXPORT_BYTES) {
                return estimatedBytes;
            }
        }
        return estimatedBytes;
    }

    private Object moduleExportData(
            String datasetId,
            SocialNetworkWorkspaceSnapshot snapshot,
            String exportType,
            Map<String, Object> query) {
        return switch (exportType) {
            case "network" -> {
                Map<String, Object> data =
                        new LinkedHashMap<>(networkSummary(snapshot));
                Map<String, Object> roles =
                        structureRolesData(snapshot, MODULE_RANKING_LIMIT);
                Map<String, Object> structureEvidence = new LinkedHashMap<>();
                structureEvidence.put("analysisSnapshot", roles.get("analysisSnapshot"));
                structureEvidence.put("scope", roles.get("scope"));
                structureEvidence.put("algorithmVersion", roles.get("algorithmVersion"));
                structureEvidence.put("kCoreDistribution", roles.get("kCoreDistribution"));
                structureEvidence.put("metricDistributions", roles.get("metricDistributions"));
                data.put("structureEvidence", structureEvidence);
                try {
                    data.put("evidenceSummary", objectMapper.convertValue(
                            evidenceSummaryService.get(snapshot), Object.class));
                } catch (SocialNetworkEvidenceSummaryDatasetNotSupportedException exception) {
                    data.put("evidenceSummary", Map.of(
                            "state", "unavailable",
                            "reason", "全网事实摘要暂仅支持内置数据集。",
                            "analysisSnapshot",
                                    SocialNetworkNodeEvidenceAssembler.snapshotIdentity(snapshot),
                            "facts", List.of(),
                            "networkInterpretations", List.of(),
                            "cannotConclude", List.of()));
                }
                yield data;
            }
            case "users" -> {
                Map<String, Object> data = new LinkedHashMap<>();
                String metric = textValue(query, "metric", "indegree");
                Map<String, Object> ranking =
                        new LinkedHashMap<>(rankings(
                                datasetId, metric, MODULE_RANKING_LIMIT).data());
                ranking.put("truncated",
                        numberValue(ranking.get("sampleSize"))
                                > listSize(ranking.get("items")));
                data.put("ranking", ranking);
                Map<String, Object> roles =
                        new LinkedHashMap<>(structureRolesData(
                                snapshot, MODULE_RANKING_LIMIT));
                roles.put("truncated", hasLimitedRoleList(roles));
                data.put("structuralRoles", roles);
                data.put("truncated", Boolean.TRUE.equals(ranking.get("truncated"))
                        || Boolean.TRUE.equals(roles.get("truncated")));
                yield data;
            }
            case "interactions" -> {
                String metric = interactionMetric(textValue(query, "metric", "like_count"));
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("metric", metric);
                Map<String, Object> summary =
                        new LinkedHashMap<>(interactionSummary(datasetId).data());
                Map<String, Object> scale = new LinkedHashMap<>(
                        mapValue(objectMapper.convertValue(
                                snapshot.interactions(), Object.class)));
                Map<String, Object> allMetrics = mapValue(scale.get("metrics"));
                Object selectedMetric = allMetrics.get(metric);
                Map<String, Object> selectedMetrics = selectedMetric == null
                        ? Map.of() : Map.of(metric, selectedMetric);
                scale.put("metrics", selectedMetrics);
                summary.put("scale", scale);
                summary.put("metrics", selectedMetrics);
                summary.put("metric", metric);
                data.put("summary", summary);
                data.put("tweets", interactionTweets(
                        datasetId, metric, MODULE_INTERACTION_LIMIT).data());
                data.put("users", interactionUsers(
                        datasetId, metric, MODULE_INTERACTION_LIMIT).data());
                Map<String, Object> timeseries =
                        new LinkedHashMap<>(interactionTimeseries(datasetId, metric).data());
                List<?> items = (List<?>) timeseries.getOrDefault("items", List.of());
                int totalCount = items.size();
                if (totalCount > MODULE_VISIBLE_SERIES_LIMIT) {
                    timeseries.put("items", items.subList(
                            totalCount - MODULE_VISIBLE_SERIES_LIMIT, totalCount));
                    timeseries.put("truncated", true);
                    timeseries.put("totalCount", totalCount);
                }
                data.put("timeseries", timeseries);
                data.put("truncated",
                        Boolean.TRUE.equals(timeseries.get("truncated"))
                                || Boolean.TRUE.equals(
                                        mapOrEmpty(data.get("tweets")).get("truncated"))
                                || Boolean.TRUE.equals(
                                        mapOrEmpty(data.get("users")).get("truncated")));
                yield data;
            }
            case "topic" -> {
                String term = textValue(query, "term", "");
                Map<String, Object> topicData = new LinkedHashMap<>(
                        textTopic(datasetId, term, MODULE_TOPIC_LIMIT).data());
                Map<String, Object> network =
                        new LinkedHashMap<>(mapValue(topicData.get("network")));
                List<?> edges = (List<?>) network.getOrDefault("edges", List.of());
                int totalEdges = numberValue(network.get("totalEdgeCount"));
                if (edges.size() > MODULE_VISIBLE_SERIES_LIMIT) {
                    network.put("edges", edges.subList(0, MODULE_VISIBLE_SERIES_LIMIT));
                }
                int returnedEdges = listSize(network.get("edges"));
                network.put("edgeCount", returnedEdges);
                network.put("returnedEdgeCount", returnedEdges);
                network.put("truncated", totalEdges > returnedEdges);
                topicData.put("truncated",
                        numberValue(topicData.get("matchedTweetCount"))
                                        > listSize(topicData.get("tweets"))
                                || numberValue(topicData.get("relatedUserCount"))
                                        > listSize(topicData.get("users"))
                                || Boolean.TRUE.equals(network.get("truncated")));
                topicData.put("network", network);
                yield topicData;
            }
            case "quality" -> {
                Map<String, Object> data =
                        new LinkedHashMap<>(qualitySummary(snapshot.quality()));
                data.put("warnings", warnings(snapshot.quality()));
                yield data;
            }
            default -> throw new IllegalArgumentException(
                    "unsupported export type: " + exportType);
        };
    }

    private static Map<String, Object> effectiveExportQuery(
            String exportType,
            Map<String, Object> query) {
        return switch (exportType) {
            case "network", "tweets", "quality" -> Map.of();
            case "users" -> {
                String metric = textValue(query, "metric", "indegree").toLowerCase();
                if (!List.of("indegree", "outdegree", "pagerank").contains(metric)) {
                    throw new IllegalArgumentException("unsupported user ranking metric");
                }
                yield Map.of("metric", metric);
            }
            case "interactions" -> Map.of(
                    "metric", interactionMetric(
                            textValue(query, "metric", "like_count")));
            case "topic" -> {
                String term = textValue(query, "term", "");
                if (term.isBlank()) {
                    throw new IllegalArgumentException("topic query term must not be blank");
                }
                yield Map.of("term", term);
            }
            default -> throw new IllegalArgumentException("unsupported export type");
        };
    }

    private int exportSampleCount(String exportType, Object exportData) {
        if (!(exportData instanceof Map<?, ?> data)) {
            return 0;
        }
        return switch (exportType) {
            case "network" -> numberValue(data.get("nodeCount")) > 0 ? 1 : 0;
            case "users" -> {
                Map<?, ?> ranking = mapOrEmpty(data.get("ranking"));
                Map<?, ?> roles = mapOrEmpty(data.get("structuralRoles"));
                Map<?, ?> core = mapOrEmpty(roles.get("kCore"));
                Map<?, ?> betweenness = mapOrEmpty(roles.get("betweenness"));
                yield listSize(ranking.get("items"))
                        + listSize(core.get("items"))
                        + listSize(betweenness.get("items"));
            }
            case "interactions" -> {
                Map<?, ?> tweets = mapOrEmpty(data.get("tweets"));
                Map<?, ?> users = mapOrEmpty(data.get("users"));
                Map<?, ?> timeseries = mapOrEmpty(data.get("timeseries"));
                int count = listSize(tweets.get("items"))
                        + listSize(users.get("items"))
                        + listSize(timeseries.get("items"));
                Map<?, ?> summary = mapOrEmpty(data.get("summary"));
                yield count > 0 || "available".equalsIgnoreCase(
                        String.valueOf(summary.get("state")))
                        ? Math.max(count, 1) : 0;
            }
            case "topic" -> numberValue(data.get("matchedTweetCount")) == 0
                    ? 0
                    : Math.max(1, listSize(data.get("tweets"))
                            + listSize(data.get("users"))
                            + listSize(mapOrEmpty(data.get("network")).get("edges")));
            case "quality" -> 1;
            default -> 0;
        };
    }

    private static int numberValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static int listSize(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    private static Map<?, ?> mapOrEmpty(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static boolean containsTruncatedResult(Object value) {
        if (value instanceof Map<?, ?> map) {
            if (Boolean.TRUE.equals(map.get("truncated"))) {
                return true;
            }
            return map.values().stream().anyMatch(SocialNetworkController::containsTruncatedResult);
        }
        if (value instanceof List<?> list) {
            return list.stream().anyMatch(SocialNetworkController::containsTruncatedResult);
        }
        return false;
    }

    private static boolean hasLimitedRoleList(Map<?, ?> roles) {
        return limitedRoleBlock(roles.get("kCore"), roles.get("nodeCount"))
                || limitedRoleBlock(roles.get("betweenness"), roles.get("nodeCount"));
    }

    private static boolean limitedRoleBlock(Object blockValue, Object fullCountValue) {
        Map<?, ?> block = mapOrEmpty(blockValue);
        String state = String.valueOf(block.get("state")).toLowerCase();
        return !"unavailable".equals(state)
                && !"empty".equals(state)
                && numberValue(fullCountValue) > listSize(block.get("items"));
    }

    private ResponseEntity<SocialNetworkApiResponse<Map<String, Object>>> exportTooLarge(
            SocialNetworkWorkspaceSnapshot snapshot) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                SocialNetworkApiResponse.<Map<String, Object>>error(
                        UUID.randomUUID().toString(),
                        SocialNetworkExportMessages.TOO_LARGE_CODE,
                        SocialNetworkExportMessages.TOO_LARGE_MESSAGE,
                        meta(snapshot)));
    }

    private static long parseLongOrZero(String value) {
        Long parsed = parseLong(value);
        return parsed == null || parsed < 0 ? 0L : parsed;
    }

    private static String textValue(
            Map<String, Object> request,
            String key,
            String defaultValue) {
        Object value = request == null ? null : request.get(key);
        return value == null ? defaultValue : value.toString().strip();
    }

    private static Map<String, Object> mapValue(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("export query and scope must be objects");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private int contentLength(String format, Object result) {
        try {
            return "json".equals(format)
                    ? objectMapper.writeValueAsBytes(result).length
                    : String.valueOf(result).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("unable to serialize export result", exception);
        }
    }

    private String csvResult(
            String exportType,
            Object result,
            SocialNetworkExportMetadata metadata) {
        Object normalized = objectMapper.convertValue(result, Object.class);
        List<Map<String, Object>> rows = new ArrayList<>();
        collectCsvRows(exportType, normalized, rows);
        if (rows.isEmpty()) {
            throw new IllegalStateException("SN_EXPORT_EMPTY_RESULT");
        }
        Object normalizedMetadata = objectMapper.convertValue(metadata, Object.class);
        List<Map<String, Object>> allRows = new ArrayList<>();
        if (normalizedMetadata instanceof Map<?, ?> values) {
            values.forEach((key, value) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("recordType", "metadata");
                row.put("field", String.valueOf(key));
                row.put("value", csvMetadataValue(value));
                allRows.add(row);
            });
        }
        allRows.addAll(rows);
        Set<String> columns = new LinkedHashSet<>();
        columns.add("recordType");
        allRows.forEach(row -> columns.addAll(row.keySet()));
        return csvSerializer.serialize(new ArrayList<>(columns), allRows);
    }

    private String csvMetadataValue(Object value) {
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException(
                        "unable to serialize export metadata", exception);
            }
        }
        return String.valueOf(value);
    }

    private static void collectCsvRows(
            String recordType,
            Object value,
            List<Map<String, Object>> rows) {
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("recordType", recordType);
                    flattenCsvFields("", map, row);
                    rows.add(row);
                }
            }
            return;
        }
        if (!(value instanceof Map<?, ?> map)) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("recordType", recordType);
        boolean hasNested = false;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object fieldValue = entry.getValue();
            if (fieldValue instanceof Map<?, ?> || fieldValue instanceof List<?>) {
                hasNested = true;
            } else {
                row.put(String.valueOf(entry.getKey()), fieldValue);
            }
        }
        if (row.size() > 1 || !hasNested) {
            rows.add(row);
        }
        map.forEach((key, nested) -> {
            if (nested instanceof Map<?, ?> || nested instanceof List<?>) {
                collectCsvRows(
                        recordType + "." + String.valueOf(key), nested, rows);
            }
        });
    }

    private static void flattenCsvFields(
            String prefix,
            Map<?, ?> source,
            Map<String, Object> row) {
        source.forEach((key, value) -> {
            String field = prefix.isEmpty()
                    ? String.valueOf(key)
                    : prefix + "." + String.valueOf(key);
            if (value instanceof Map<?, ?> nested) {
                flattenCsvFields(field, nested, row);
            } else if (value instanceof List<?> list) {
                row.put(field, list.stream()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining("|")));
            } else {
                row.put(field, value);
            }
        });
    }

    @ExceptionHandler(SocialNetworkDatasetContextException.class)
    public ResponseEntity<SocialNetworkApiResponse<Void>> contextMismatch(
            SocialNetworkDatasetContextException exception) {
        return error(
                HttpStatus.BAD_REQUEST,
                "SN_DATASET_CONTEXT_MISMATCH",
                "数据集上下文不匹配");
    }

    @ExceptionHandler(SocialNetworkEvidenceSummaryDatasetNotSupportedException.class)
    public ResponseEntity<SocialNetworkApiResponse<Void>> evidenceSummaryDatasetNotSupported(
            SocialNetworkEvidenceSummaryDatasetNotSupportedException exception) {
        return error(
                HttpStatus.BAD_REQUEST,
                "SN_EVIDENCE_SUMMARY_BUILTIN_ONLY",
                "事实摘要目前仅支持内置数据集");
    }

    @ExceptionHandler(SocialNetworkDatasetSessionExpiredException.class)
    public ResponseEntity<SocialNetworkApiResponse<Void>> sessionExpired(
            SocialNetworkDatasetSessionExpiredException exception) {
        return error(
                HttpStatus.GONE,
                "SN_DATASET_SESSION_EXPIRED",
                "上传分析会话已过期");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<SocialNetworkApiResponse<Void>> notFound(IllegalArgumentException exception) {
        return error(HttpStatus.NOT_FOUND, "SN_DATASET_NOT_FOUND", "未找到指定数据集");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<SocialNetworkApiResponse<Void>> unavailable(IllegalStateException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "SN_DATASET_UNAVAILABLE", "当前社交网络数据暂不可用");
    }

    private SocialNetworkWorkspaceSnapshot requestSnapshot(String datasetId) {
        if (!(RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes requestAttributes)) {
            SocialNetworkDataset dataset = registry.findDataset(datasetId);
            if (dataset.isUploadSession()) {
                throw new SocialNetworkDatasetContextException();
            }
            return workspace.get(datasetId);
        }
        String requestedDatasetId = requestAttributes.getRequest().getParameter("datasetId");
        if (requestedDatasetId != null && !requestedDatasetId.equals(datasetId)) {
            throw new SocialNetworkDatasetContextException();
        }
        String datasetVersion =
                requestAttributes.getRequest().getParameter("datasetVersion");
        String sessionId = requestAttributes.getRequest().getParameter("sessionId");
        if (datasetVersion == null && sessionId == null) {
            SocialNetworkDataset dataset = registry.findDataset(datasetId);
            if (dataset.isUploadSession()) {
                throw new SocialNetworkDatasetContextException();
            }
            return workspace.get(datasetId);
        }
        if (datasetVersion == null || datasetVersion.isBlank()) {
            throw new SocialNetworkDatasetContextException();
        }
        return workspace.get(datasetId, datasetVersion, sessionId);
    }

    private <T> SocialNetworkApiResponse<T> success(
            T data, SocialNetworkWorkspaceSnapshot snapshot) {
        return SocialNetworkApiResponse.success(
                UUID.randomUUID().toString(),
                data,
                warnings(snapshot.quality()),
                meta(snapshot));
    }

    private static SocialNetworkApiResponse<CliqueEnumerationWorkflowView> cliqueSuccess(
            CliqueEnumerationWorkflowView view) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("datasetId", view.datasetId());
        meta.put("datasetVersion", view.datasetVersion());
        meta.put("analysisScope", view.analysisScope());
        meta.put("dataFingerprint", view.dataFingerprint());
        meta.put("cacheKey", view.cacheKey());
        meta.put("cacheHit", view.cacheHit());
        meta.put("scope", Map.of(
                "inputIdentityMode", view.inputIdentityMode(),
                "inputFingerprintAvailable", view.inputFingerprintAvailable(),
                "projection", view.projection(),
                "analysisScope", view.analysisScope(),
                "inputContractVersion", view.inputContractVersion(),
                "relationProjectionVersion", view.relationProjectionVersion()));
        return SocialNetworkApiResponse.success(
                UUID.randomUUID().toString(),
                view,
                List.of(),
                meta);
    }

    private static SocialNetworkApiResponse<LouvainAnalysisWorkflowView> louvainSuccess(
            LouvainAnalysisWorkflowView view) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("datasetId", view.datasetId());
        meta.put("datasetVersion", view.datasetVersion());
        meta.put("analysisScope", view.analysisScope());
        meta.put("dataFingerprint", view.dataFingerprint());
        meta.put("cacheKey", view.cacheKey());
        meta.put("cacheHit", view.cacheHit());
        meta.put("scope", Map.of(
                "inputIdentityMode", view.dataFingerprint() == null
                        ? "unavailable"
                        : "data_fingerprint",
                "inputFingerprintAvailable", view.dataFingerprint() != null,
                "projection", view.projection(),
                "analysisScope", view.analysisScope(),
                "inputContractVersion", view.inputContractVersion(),
                "relationProjectionVersion", view.relationProjectionVersion()));
        return SocialNetworkApiResponse.success(
                UUID.randomUUID().toString(), view, List.of(), meta);
    }

    private static Map<String, Object> datasetSummary(
            SocialNetworkDataset dataset, SocialNetworkQualityReport quality) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("datasetId", dataset.datasetId());
        data.put("name", dataset.name());
        data.put("type", dataset.type().name().toLowerCase());
        data.put("version", dataset.datasetVersion());
        data.put("status", quality.datasetStatus().name().toLowerCase());
        data.put("state", SocialNetworkStateCatalog.fromDataset(quality.datasetStatus()));
        data.put("sourceDescription", dataset.sourceDescription());
        data.put("timeRange", Map.of(
                "from", String.valueOf(dataset.timeRangeFrom()),
                "to", String.valueOf(dataset.timeRangeTo())));
        data.put("files", dataset.files());
        data.put("counts", quality.summary());
        return data;
    }

    private static Map<String, Object> qualitySummary(SocialNetworkQualityReport report) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("datasetStatus", report.datasetStatus().name().toLowerCase());
        data.put("state", SocialNetworkStateCatalog.fromDataset(report.datasetStatus()));
        data.put("summary", report.summary());
        data.put("userQuality", report.userQuality());
        data.put("relationshipQuality", report.relationshipQuality());
        data.put("tweetQuality", report.tweetQuality());
        data.put("interactionQuality", report.interactionQuality());
        data.put("textQuality", report.textQuality());
        data.put("coverage", report.coverage());
        return data;
    }

    private static Map<String, Object> networkSummary(
            SocialNetworkWorkspaceSnapshot snapshot) {
        var metrics = snapshot.network().metrics();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nodeCount", metrics.nodeCount());
        data.put("edgeCount", metrics.edgeCount());
        data.put("profileUserCount", metrics.profileUserCount());
        data.put("externalNodeCount", metrics.externalNodeCount());
        data.put("isolatedNodeCount", metrics.isolatedNodeCount());
        data.put("weaklyConnectedComponentCount", metrics.weaklyConnectedComponentCount());
        data.put("largestWeaklyConnectedComponentNodeCount",
                metrics.largestWeaklyConnectedComponentNodeCount());
        data.put("largestWeaklyConnectedComponentRatio",
                metrics.largestWeaklyConnectedComponentRatio());
        data.put("averageClusteringCoefficient", metrics.averageClusteringCoefficient());
        data.put("reciprocityRate", metrics.reciprocityRate());
        data.put("largestWeaklyConnectedComponentAveragePathLength",
                metrics.largestWeaklyConnectedComponentAveragePathLength());
        data.put("density", metrics.density());
        data.put("metricVersion", metrics.metricVersion());
        data.put("pagerankVersion", metrics.pagerankVersion());
        data.put("includeExternalNodes", metrics.includeExternalNodes());
        data.put("excludeSelfLoops", metrics.excludeSelfLoops());
        data.put("scope", "全量规范化关系网络");
        data.put("state", metrics.nodeCount() == 0
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("pageRank", Map.of(
                "converged", snapshot.network().pageRank().converged(),
                "iterations", snapshot.network().pageRank().iterations(),
                "finalDelta", snapshot.network().pageRank().finalDelta(),
                "sum", snapshot.network().pageRank().sum()));
        data.put("interactionScale", snapshot.interactions());
        return data;
    }

    private static boolean isSearchableUser(NormalizedUser user, String query) {
        if (user.userId() == null
                || user.userId().isBlank()
                || user.normalizationStatus() == NormalizationStatus.REJECTED_REQUIRED_FIELD
                || user.normalizationStatus() == NormalizationStatus.REJECTED_INVALID_FORMAT) {
            return false;
        }
        if (query.isBlank()) {
            return true;
        }
        return List.of(user.userId(), user.fields().get("username"), user.fields().get("display_name"))
                .stream()
                .filter(value -> value != null)
                .map(value -> value.toLowerCase())
                .anyMatch(value -> value.contains(query));
    }

    private static Map<String, Object> userSummary(
            NormalizedUser user, SocialNetworkWorkspaceSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.userId());
        data.put("username", user.fields().getOrDefault("username", ""));
        data.put("displayName", user.fields().getOrDefault("display_name", ""));
        data.put("nodeType", snapshot.network().nodes().stream()
                .filter(node -> node.id().equals(user.userId()))
                .map(node -> node.nodeType().value())
                .findFirst()
                .orElse("profile"));
        return data;
    }

    private static Map<String, Object> userDetailData(
            SocialNetworkWorkspaceSnapshot snapshot, String userId) {
        NetworkNode node = snapshot.network().nodes().stream()
                .filter(item -> item.id().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("user node not found"));
        NormalizedUser profile = snapshot.users().stream()
                .filter(user -> userId.equals(user.userId()))
                .findFirst()
                .orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("nodeType", node.nodeType().value());
        data.put("profileStatus", profile == null ? "unavailable" : "available");
        data.put("profileState", profile == null
                ? com.prediction.socialnetwork.domain.SocialNetworkState.UNAVAILABLE
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("datasetVersion", snapshot.dataset().datasetVersion());
        data.put("scope", "当前规范化关系网络与采集样本");
        if (profile == null || node.nodeType() == NetworkNodeType.EXTERNAL) {
            data.put("profile", Map.of(
                    "username", "",
                    "displayName", "",
                    "bio", "",
                    "status", "资料未采集"));
        } else {
            data.put("profile", profileFields(profile));
        }
        data.put("networkPosition", Map.of(
                "inDegree", node.inDegree(),
                "outDegree", node.outDegree(),
                "pageRank", node.pageRank(),
                "isolated", node.isolated()));
        data.put("rawAccountFields", rawAccountFields(profile));
        data.put("rankingReasons", rankingReasons(snapshot, userId));
        data.put("tweetEvidence", tweetEvidence(snapshot, userId));
        data.put("neighbors", neighbors(snapshot, userId));
        data.put("textSummary", Map.of(
                "available", false,
                "state", com.prediction.socialnetwork.domain.SocialNetworkState.UNAVAILABLE,
                "reason", "关键词和主题分析将在文本业务任务中接入。"));
        String localNetworkLink = "/api/social-network/datasets/"
                + snapshot.dataset().datasetId()
                + "/network/local?centerNodeId=" + userId;
        if (snapshot.dataset().isUploadSession()) {
            localNetworkLink += "&datasetVersion=" + snapshot.dataset().datasetVersion()
                    + "&sessionId=" + snapshot.dataset().sessionId();
        }
        data.put("links", Map.of(
                "localNetwork", localNetworkLink));
        return data;
    }

    private static Map<String, Object> profileFields(NormalizedUser profile) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", profile.fields().getOrDefault("username", ""));
        data.put("displayName", profile.fields().getOrDefault("display_name", ""));
        data.put("bio", profile.fields().getOrDefault("bio", ""));
        data.put("location", profile.fields().getOrDefault("location", ""));
        data.put("createdAt", profile.fields().getOrDefault("created_at", ""));
        data.put("status", "已采集");
        return data;
    }

    private static Map<String, Object> rawAccountFields(NormalizedUser profile) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (profile == null) {
            data.put("available", false);
            data.put("state", com.prediction.socialnetwork.domain.SocialNetworkState.UNAVAILABLE);
            data.put("reason", "外部节点未采集用户资料。");
            return data;
        }
        data.put("available", true);
        data.put("state", com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("followersCount", parseLong(profile.fields().get("followers_count")));
        data.put("followingCount", parseLong(profile.fields().get("following_count")));
        data.put("declaredTweetCount", parseLong(profile.fields().get("tweet_count")));
        data.put("source", "用户资料原始字段");
        return data;
    }

    private static Map<String, Object> rankingReasons(
            SocialNetworkWorkspaceSnapshot snapshot, String userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (String metric : List.of("indegree", "outdegree", "pagerank",
                "followers_count", "following_count", "tweet_count")) {
            List<RankingValue> values = rankingValues(snapshot, metric);
            int position = -1;
            double value = Double.NaN;
            for (int index = 0; index < values.size(); index++) {
                if (userId.equals(values.get(index).nodeId())) {
                    position = index + 1;
                    value = values.get(index).value();
                    break;
                }
            }
            if (position > 0) {
                data.put(metric, Map.of(
                        "rank", position,
                        "value", value,
                        "reason", "按" + metricLabel(metric) + "降序排列"));
            }
        }
        return data;
    }

    private static Map<String, Object> tweetEvidence(
            SocialNetworkWorkspaceSnapshot snapshot, String userId) {
        List<NormalizedTweet> tweets = snapshot.tweets().stream()
                .filter(tweet -> userId.equals(tweet.authorUserId()) && tweet.analysisIncluded())
                .toList();
        Map<String, InteractionMetricSummary> metrics = new LinkedHashMap<>();
        for (String field : List.of("like_count", "reply_count", "repost_count")) {
            List<Long> values = tweets.stream()
                    .map(tweet -> parseLong(tweet.fields().get(field)))
                    .filter(value -> value != null && value >= 0)
                    .toList();
            long total = values.stream().mapToLong(Long::longValue).sum();
            metrics.put(field, new InteractionMetricSummary(
                    field, total, values.isEmpty() ? 0.0 : total * 1.0 / values.size(),
                    median(values), values.size(), tweets.size() - values.size(),
                    (int) values.stream().filter(value -> value == 0).count()));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("available", !tweets.isEmpty());
        data.put("state", tweets.isEmpty()
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        data.put("sampleCount", tweets.size());
        data.put("scope", "当前采集且成功关联的推文样本");
        data.put("interactions", metrics);
        data.put("reason", tweets.isEmpty() ? "当前用户没有可展示的关联推文样本。" : null);
        return data;
    }

    private static double median(List<Long> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Long> sorted = new java.util.ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    private static Map<String, Object> neighbors(
            SocialNetworkWorkspaceSnapshot snapshot, String userId) {
        List<Map<String, Object>> items = snapshot.network().edges().stream()
                .filter(edge -> userId.equals(edge.source()) || userId.equals(edge.target()))
                .map(edge -> {
                    String neighborId = userId.equals(edge.source()) ? edge.target() : edge.source();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("nodeId", neighborId);
                    item.put("direction", userId.equals(edge.source()) ? "outgoing" : "incoming");
                    item.put("relationType", edge.relationType());
                    item.put("external", snapshot.network().nodes().stream()
                            .filter(node -> neighborId.equals(node.id()))
                            .anyMatch(node -> node.nodeType() == NetworkNodeType.EXTERNAL));
                    return item;
                })
                .limit(50)
                .toList();
        return Map.of("items", items, "sampleLimit", 50, "totalAvailable", items.size());
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String interactionMetric(String metric) {
        String normalized = metric == null ? "like_count" : metric.strip().toLowerCase();
        if (!List.of("like_count", "reply_count", "repost_count").contains(normalized)) {
            throw new IllegalArgumentException("unsupported interaction metric");
        }
        return normalized;
    }

    private static Map<String, Object> interactionValue(
            NormalizedTweet tweet, String metric) {
        Long value = parseLong(tweet.fields().get(metric));
        if (value == null || value < 0) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("tweetId", tweet.tweetId());
        item.put("authorUserId", tweet.authorUserId());
        item.put("authorStatus", tweet.authorStatus().name().toLowerCase());
        item.put("sourceFolder", tweet.sourceFolder());
        item.put("createdAt", tweet.fields().get("created_at"));
        item.put("value", value);
        item.put("qualityFlags", tweet.qualityFlags());
        return item;
    }

    private static Map<String, Object> nodeSummary(
            NetworkNode node, SocialNetworkWorkspaceSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", node.id());
        data.put("nodeType", node.nodeType().value());
        data.put("inDegree", node.inDegree());
        data.put("outDegree", node.outDegree());
        data.put("pageRank", node.pageRank());
        data.put("isolated", node.isolated());
        if (node.nodeType() == NetworkNodeType.PROFILE) {
            snapshot.users().stream()
                    .filter(user -> node.id().equals(user.userId()))
                    .findFirst()
                    .ifPresent(user -> {
                        data.put("username", user.fields().getOrDefault("username", ""));
                        data.put("displayName", user.fields().getOrDefault("display_name", ""));
                    });
        }
        return data;
    }

    private static Map<String, Object> nodeSummary(
            NetworkNode node,
            Map<String, NormalizedUser> profilesById,
            String communityId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", node.id());
        data.put("nodeType", node.nodeType().value());
        data.put("inDegree", node.inDegree());
        data.put("outDegree", node.outDegree());
        data.put("pageRank", node.pageRank());
        data.put("isolated", node.isolated());
        NormalizedUser profile = profilesById.get(node.id());
        if (profile != null && node.nodeType() == NetworkNodeType.PROFILE) {
            data.put("username", profile.fields().getOrDefault("username", ""));
            data.put("displayName", profile.fields().getOrDefault("display_name", ""));
        }
        if (communityId != null) {
            data.put("communityId", communityId);
        }
        return data;
    }

    private static List<Map<String, Object>> communitySummary(
            List<NetworkNode> nodes,
            List<NetworkEdge> edges,
            Map<String, String> communityByNodeId) {
        Map<String, List<NetworkNode>> membersByCommunity = new java.util.TreeMap<>();
        nodes.forEach(node -> {
            String communityId = communityByNodeId.get(node.id());
            if (communityId != null) {
                membersByCommunity.computeIfAbsent(communityId, key -> new ArrayList<>())
                        .add(node);
            }
        });
        Map<String, Integer> internalEdgesByCommunity = new LinkedHashMap<>();
        edges.forEach(edge -> {
            String sourceCommunity = communityByNodeId.get(edge.source());
            String targetCommunity = communityByNodeId.get(edge.target());
            if (sourceCommunity != null && sourceCommunity.equals(targetCommunity)) {
                internalEdgesByCommunity.merge(sourceCommunity, 1, Integer::sum);
            }
        });
        return membersByCommunity.entrySet().stream().map(entry -> {
            List<NetworkNode> members = entry.getValue();
            Map<String, Object> community = new LinkedHashMap<>();
            community.put("id", entry.getKey());
            community.put("nodeCount", members.size());
            community.put("profileNodeCount", members.stream()
                    .filter(node -> node.nodeType() == NetworkNodeType.PROFILE).count());
            community.put("externalNodeCount", members.stream()
                    .filter(node -> node.nodeType() == NetworkNodeType.EXTERNAL).count());
            community.put("internalDirectedEdgeCount",
                    internalEdgesByCommunity.getOrDefault(entry.getKey(), 0));
            return community;
        }).toList();
    }

    private static List<RankingValue> rankingValues(
            SocialNetworkWorkspaceSnapshot snapshot, String metric) {
        Map<String, Double> values = new LinkedHashMap<>();
        if (Set.of("indegree", "outdegree", "pagerank").contains(metric)) {
            snapshot.network().nodes().forEach(node -> values.put(node.id(),
                    switch (metric) {
                        case "indegree" -> (double) node.inDegree();
                        case "outdegree" -> (double) node.outDegree();
                        default -> node.pageRank();
                    }));
        } else {
            Set<String> seen = new LinkedHashSet<>();
            snapshot.users().forEach(user -> {
                if (user.userId() == null || !seen.add(user.userId())) {
                    return;
                }
                String raw = user.fields().get(fieldForMetric(metric));
                if (raw == null || raw.isBlank()) {
                    return;
                }
                try {
                    long parsed = Long.parseLong(raw);
                    if (parsed >= 0) {
                        values.put(user.userId(), (double) parsed);
                    }
                } catch (NumberFormatException ignored) {
                    // Invalid raw counters remain excluded and are represented by coverage.
                }
            });
        }
        return values.entrySet().stream()
                .map(entry -> new RankingValue(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingDouble(RankingValue::value).reversed()
                        .thenComparing(RankingValue::nodeId))
                .toList();
    }

    private static Map<String, Object> structuralRankingBlock(
            String label,
            com.prediction.socialnetwork.domain.SocialNetworkState state,
            List<Map<String, Object>> items,
            String reason) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("label", label);
        block.put("state", state);
        block.put("items", items);
        block.put("reason", reason);
        return block;
    }

    private static Map<String, NormalizedUser> profilesByUserId(
            List<NormalizedUser> users) {
        Map<String, NormalizedUser> profilesById = new LinkedHashMap<>();
        users.stream()
                .filter(user -> user.userId() != null && !user.userId().isBlank())
                .forEach(user -> profilesById.putIfAbsent(user.userId(), user));
        return profilesById;
    }

    private static Map<String, Object> nodeIdentityEvidence(
            String nodeId,
            NetworkNodeType nodeType,
            Map<String, NormalizedUser> profilesById) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("nodeId", nodeId);
        item.put("nodeType", nodeType.value());
        item.putAll(nodeProfileEvidence(nodeId, nodeType, profilesById));
        return item;
    }

    private static Map<String, Object> nodeProfileEvidence(
            String nodeId,
            NetworkNodeType nodeType,
            Map<String, NormalizedUser> profilesById) {
        NormalizedUser profile = nodeType == NetworkNodeType.PROFILE
                ? profilesById.get(nodeId)
                : null;
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("profileState", profile == null ? "unavailable" : "available");
        evidence.put("profileReason", profile != null
                ? null
                : nodeType == NetworkNodeType.EXTERNAL
                        ? "外部节点没有对应的资料记录。"
                        : "当前数据集中没有该节点的资料记录。");
        evidence.put("profile", Map.of(
                "username", profileText(profile, "username"),
                "displayName", profileText(profile, "display_name")));
        return evidence;
    }

    private static String profileText(NormalizedUser profile, String field) {
        if (profile == null) {
            return "";
        }
        String value = profile.fields().get(field);
        return value == null ? "" : value;
    }

    private static Map<String, Object> kCoreDistributionBlock(
            StructuralRoleAnalysis analysis,
            Map<String, NormalizedUser> profilesById) {
        StructuralRoleAnalysis.KCoreDistribution distribution =
                analysis.kCoreDistribution();
        boolean empty = distribution.sampleSize() == 0;
        List<Map<String, Object>> shells = distribution.shells().stream()
                .map(shell -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("coreNumber", shell.coreNumber());
                    item.put("exactCount", shell.exactCount());
                    item.put("exactRatio", shell.exactRatio());
                    item.put("cumulativeCount", shell.cumulativeCount());
                    item.put("cumulativeRatio", shell.cumulativeRatio());
                    item.put("nodes", shell.nodes().stream()
                            .map(node -> nodeIdentityEvidence(
                                    node.nodeId(), node.nodeType(), profilesById))
                            .toList());
                    return item;
                })
                .toList();

        Map<String, Object> block = new LinkedHashMap<>();
        block.put("label", "k-core 完整层级分布");
        block.put("state", empty
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE);
        block.put("reason", empty ? "当前完整网络范围内没有节点。" : null);
        block.put("algorithmVersion", analysis.algorithmVersion());
        block.put("sampleSize", distribution.sampleSize());
        block.put("maxCoreNumber", distribution.maxCoreNumber());
        block.put("highestCoreCount", distribution.highestCoreCount());
        block.put("complete", true);
        block.put("truncated", false);
        block.put("shells", shells);
        return block;
    }

    private static Map<String, Object> metricDistributions(
            SocialNetworkWorkspaceSnapshot snapshot,
            StructuralRoleAnalysis analysis) {
        int populationSize = analysis.nodeCount();
        boolean empty = populationSize == 0;
        NodeMetricDistributionCalculator calculator =
                new NodeMetricDistributionCalculator();
        SocialNetworkState degreeState = empty
                ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                : com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE;
        String emptyReason = empty ? "当前完整网络范围内没有节点。" : null;
        List<NetworkNode> nodes = snapshot.network().nodes();
        NetworkMetrics metrics = snapshot.network().metrics();

        Map<String, Object> distributions = new LinkedHashMap<>();
        distributions.put("populationSize", populationSize);
        distributions.put("complete", true);
        distributions.put("truncated", false);
        distributions.put("indegree", metricDistributionBlock(
                calculator,
                "indegree",
                "入度",
                "count",
                "directed_simple_network",
                metrics.metricVersion(),
                populationSize,
                nodes.stream().map(node -> (double) node.inDegree()).toList(),
                degreeState,
                emptyReason));
        distributions.put("outdegree", metricDistributionBlock(
                calculator,
                "outdegree",
                "出度",
                "count",
                "directed_simple_network",
                metrics.metricVersion(),
                populationSize,
                nodes.stream().map(node -> (double) node.outDegree()).toList(),
                degreeState,
                emptyReason));

        PageRankResult pageRank = snapshot.network().pageRank();
        com.prediction.socialnetwork.domain.SocialNetworkState pageRankState =
                empty
                        ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                        : pageRank.converged()
                                ? com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE
                                : com.prediction.socialnetwork.domain.SocialNetworkState.LIMITED;
        String pageRankReason = empty
                ? emptyReason
                : pageRank.converged()
                        ? null
                        : "PageRank 未收敛；以下摘要仅作诊断，不用于正式结论或跨指标互核。";
        Map<String, Object> pageRankDistribution = metricDistributionBlock(
                calculator,
                "pageRank",
                "PageRank",
                "score",
                "directed_simple_network",
                metrics.pagerankVersion(),
                populationSize,
                nodes.stream().map(node -> node.pageRank()).toList(),
                pageRankState,
                pageRankReason);
        pageRankDistribution.put("diagnostics", Map.of(
                "converged", pageRank.converged(),
                "iterations", pageRank.iterations(),
                "finalDelta", pageRank.finalDelta(),
                "sum", pageRank.sum()));
        distributions.put("pageRank", pageRankDistribution);

        boolean betweennessAvailable = populationSize >= 3;
        com.prediction.socialnetwork.domain.SocialNetworkState betweennessState =
                empty
                        ? com.prediction.socialnetwork.domain.SocialNetworkState.EMPTY
                        : betweennessAvailable
                                ? com.prediction.socialnetwork.domain.SocialNetworkState.AVAILABLE
                                : com.prediction.socialnetwork.domain.SocialNetworkState.UNAVAILABLE;
        List<Double> betweennessValues = betweennessAvailable
                ? analysis.betweennessRanking().stream()
                        .map(StructuralRoleAnalysis.BetweennessEntry::normalizedBetweenness)
                        .toList()
                : List.of();
        distributions.put("normalizedBetweenness", metricDistributionBlock(
                calculator,
                "normalizedBetweenness",
                "归一化介数中心性",
                "score",
                "undirected_unweighted_simple_projection",
                analysis.algorithmVersion(),
                populationSize,
                betweennessValues,
                betweennessState,
                empty
                        ? emptyReason
                        : betweennessAvailable
                                ? null
                                : "节点数不足 3，无法计算归一化介数中心性。"));
        return distributions;
    }

    private static Map<String, Object> crossMetricBlock(
            SocialNetworkWorkspaceSnapshot snapshot,
            StructuralRoleAnalysis structuralRoles,
            Map<String, NormalizedUser> profilesById) {
        int populationSize = structuralRoles.nodeCount();
        PageRankResult pageRank = snapshot.network().pageRank();
        Map<String, String> unavailableReasons = new LinkedHashMap<>();
        if (!pageRank.converged()) {
            unavailableReasons.put(
                    "pageRank",
                    "PageRank 未收敛；该指标不参与正式跨指标互核。");
        }
        if (populationSize < 3) {
            unavailableReasons.put(
                    "normalizedBetweenness",
                    "节点数不足 3，归一化介数中心性不可用。");
        }

        Map<String, Map<String, Double>> valuesByNodeId = new LinkedHashMap<>();
        for (NetworkNode node : snapshot.network().nodes()) {
            Map<String, Double> values = new LinkedHashMap<>();
            values.put("indegree", (double) node.inDegree());
            values.put("outdegree", (double) node.outDegree());
            if (pageRank.converged()) {
                values.put("pageRank", node.pageRank());
            }
            valuesByNodeId.put(node.id(), values);
        }
        for (StructuralRoleAnalysis.KCoreEntry entry : structuralRoles.kCoreRanking()) {
            Map<String, Double> values = valuesByNodeId.get(entry.nodeId());
            if (values != null) {
                values.put("kCore", (double) entry.coreNumber());
            }
        }
        if (populationSize >= 3) {
            for (StructuralRoleAnalysis.BetweennessEntry entry
                    : structuralRoles.betweennessRanking()) {
                Map<String, Double> values = valuesByNodeId.get(entry.nodeId());
                if (values != null && entry.normalizedBetweenness() != null) {
                    values.put("normalizedBetweenness", entry.normalizedBetweenness());
                }
            }
        }

        List<CrossMetricAnalysisCalculator.NodeMetricValues> nodes =
                snapshot.network().nodes().stream()
                        .map(node -> new CrossMetricAnalysisCalculator.NodeMetricValues(
                                node.id(),
                                node.nodeType(),
                                valuesByNodeId.getOrDefault(node.id(), Map.of())))
                        .toList();
        CrossMetricAnalysisCalculator.Analysis analysis =
                new CrossMetricAnalysisCalculator().analyze(nodes, unavailableReasons);

        Map<String, Object> block = new LinkedHashMap<>();
        block.put("state", analysis.state().apiValue());
        block.put("reason", analysis.reason());
        block.put("populationSize", analysis.populationSize());
        block.put("metricOrder", CrossMetricAnalysisCalculator.METRIC_KEYS);
        block.put("spearmanPairs", analysis.spearmanPairs().stream()
                .map(pair -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("metricA", pair.metricA());
                    item.put("metricB", pair.metricB());
                    item.put("rho", pair.rho());
                    item.put("sampleSize", pair.sampleSize());
                    item.put("tieMethod", pair.tieMethod());
                    item.put("state", pair.state().apiValue());
                    item.put("reason", pair.reason());
                    return item;
                })
                .toList());
        block.put("top20Comparisons", analysis.top20Comparisons().stream()
                .map(comparison -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("metricA", comparison.metricA());
                    item.put("metricB", comparison.metricB());
                    item.put("k", comparison.k());
                    item.put("thresholdA", comparison.thresholdA());
                    item.put("thresholdB", comparison.thresholdB());
                    item.put("setSizeA", comparison.setSizeA());
                    item.put("setSizeB", comparison.setSizeB());
                    item.put("intersectionCount", comparison.intersectionCount());
                    item.put("jaccard", comparison.jaccard());
                    item.put("positiveScoreNodeCountA",
                            comparison.positiveScoreNodeCountA());
                    item.put("positiveScoreNodeCountB",
                            comparison.positiveScoreNodeCountB());
                    item.put("tieExpandedA", comparison.tieExpandedA());
                    item.put("tieExpandedB", comparison.tieExpandedB());
                    item.put("state", comparison.state().apiValue());
                    item.put("reason", comparison.reason());
                    return item;
                })
                .toList());
        block.put("nodeEvidenceState", analysis.nodeEvidenceState().apiValue());
        block.put("nodeEvidenceReason", analysis.nodeEvidenceReason());
        block.put("nodeEvidence", analysis.nodeEvidence().stream()
                .map(evidence -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("nodeId", evidence.nodeId());
                    item.put("nodeType", evidence.nodeType().value());
                    item.putAll(nodeProfileEvidence(
                            evidence.nodeId(), evidence.nodeType(), profilesById));
                    item.put("metrics", evidence.metrics().stream()
                            .map(metric -> Map.of(
                                    "metricKey", metric.metricKey(),
                                    "value", metric.value(),
                                    "rank", metric.rank()))
                            .toList());
                    return item;
                })
                .toList());
        return block;
    }

    private static Map<String, Object> metricDistributionBlock(
            NodeMetricDistributionCalculator calculator,
            String metricKey,
            String label,
            String unit,
            String projection,
            String algorithmVersion,
            int populationSize,
            List<Double> values,
            com.prediction.socialnetwork.domain.SocialNetworkState state,
            String reason) {
        int invalidValueCount = (int) values.stream()
                .filter(value -> value == null || !Double.isFinite(value))
                .count();
        com.prediction.socialnetwork.domain.SocialNetworkState resultState = state;
        String resultReason = reason;
        NodeMetricDistributionCalculator.Summary summary;
        if (invalidValueCount > 0) {
            summary = calculator.summarize(List.of());
            resultState = com.prediction.socialnetwork.domain.SocialNetworkState.UNAVAILABLE;
            resultReason = "有 " + invalidValueCount
                    + " 个缺失或非有限指标值，无法按完整总体生成分布。";
        } else {
            summary = calculator.summarize(values);
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("metricKey", metricKey);
        block.put("label", label);
        block.put("unit", unit);
        block.put("projection", projection);
        block.put("algorithmVersion", algorithmVersion);
        block.put("state", resultState);
        block.put("reason", resultReason);
        block.put("populationSize", populationSize);
        block.put("sampleSize", summary.sampleSize());
        block.put("invalidValueCount", invalidValueCount);
        block.put("zeroCount", summary.zeroCount());
        block.put("zeroRatio", summary.zeroRatio());
        block.put("min", summary.min());
        block.put("mean", summary.mean());
        block.put("median", summary.median());
        block.put("p90", summary.p90());
        block.put("p95", summary.p95());
        block.put("max", summary.max());
        return block;
    }

    private static List<Map<String, Object>> structuralRankingItems(
            List<StructuralRankingValue> values,
            int limit,
            String reason,
            SocialNetworkWorkspaceSnapshot snapshot) {
        List<Map<String, Object>> items = new ArrayList<>();
        double previousValue = Double.NaN;
        int rank = 0;
        for (int index = 0; index < Math.min(limit, values.size()); index++) {
            StructuralRankingValue value = values.get(index);
            double numericValue = value.value().doubleValue();
            if (Double.isNaN(previousValue)
                    || Double.compare(previousValue, numericValue) != 0) {
                rank = index + 1;
                previousValue = numericValue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", rank);
            item.put("nodeId", value.nodeId());
            item.put("nodeType", value.nodeType().value());
            item.put("status", value.nodeType() == NetworkNodeType.EXTERNAL
                    ? "external" : "available");
            item.put("value", value.value());
            if (value.rawValue() != null) {
                item.put("rawValue", value.rawValue());
            }
            item.put("reason", reason);
            snapshot.users().stream()
                    .filter(user -> value.nodeId().equals(user.userId()))
                    .findFirst()
                    .ifPresent(user -> {
                        item.put("username", user.fields().getOrDefault("username", ""));
                        item.put("displayName", user.fields().getOrDefault("display_name", ""));
                    });
            items.add(item);
        }
        return items;
    }

    private static Map<String, Object> rankingItem(
            RankingValue value,
            int rank,
            String metric,
            SocialNetworkWorkspaceSnapshot snapshot) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("rank", rank);
        item.put("nodeId", value.nodeId());
        item.put("value", value.value());
        item.put("status", snapshot.network().nodes().stream()
                .filter(node -> node.id().equals(value.nodeId()))
                .map(node -> node.nodeType() == NetworkNodeType.EXTERNAL ? "external" : "available")
                .findFirst()
                .orElse("available"));
        item.put("sampleSize", 1);
        item.put("reason", "按" + metricLabel(metric) + "降序排列");
        snapshot.users().stream()
                .filter(user -> value.nodeId().equals(user.userId()))
                .findFirst()
                .ifPresent(user -> {
                    item.put("username", user.fields().getOrDefault("username", ""));
                    item.put("displayName", user.fields().getOrDefault("display_name", ""));
                });
        return item;
    }

    private static String fieldForMetric(String metric) {
        return switch (metric) {
            case "followers_count" -> "followers_count";
            case "following_count" -> "following_count";
            case "tweet_count" -> "tweet_count";
            default -> throw new IllegalArgumentException("unsupported ranking metric");
        };
    }

    private static String metricType(String metric) {
        return SocialNetworkMetricCatalog.require(metric).type();
    }

    private static String metricLabel(String metric) {
        return SocialNetworkMetricCatalog.require(metric).label();
    }

    private static Map<String, Object> metricDefinition(
            SocialNetworkMetricCatalog.MetricDefinition definition) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", definition.key());
        data.put("label", definition.label());
        data.put("type", definition.type());
        data.put("scope", definition.scope());
        data.put("missingValueRule", definition.missingValueRule());
        data.put("zeroValueRule", definition.zeroValueRule());
        data.put("invalidValueRule", definition.invalidValueRule());
        return data;
    }

    private static Map<String, Object> metricDefinitions(List<String> metrics) {
        Map<String, Object> definitions = new LinkedHashMap<>();
        for (String metric : metrics) {
            SocialNetworkMetricCatalog.MetricDefinition definition =
                    SocialNetworkMetricCatalog.require(metric);
            definitions.put(metric, metricDefinition(definition));
        }
        return definitions;
    }

    private record RankingValue(String nodeId, double value) {
    }

    private record StructuralRankingValue(
            String nodeId,
            NetworkNodeType nodeType,
            Number value,
            Double rawValue) {
    }

    private static List<Map<String, Object>> warnings(SocialNetworkQualityReport report) {
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (QualityIssue issue : report.issues()) {
            if (!issue.severity().name().equals("WARNING")
                    && !issue.severity().name().equals("INFO")) {
                continue;
            }
            String key = issue.severity() + "|" + issue.code() + "|"
                    + issue.dimension() + "|" + issue.message();
            Map<String, Object> item = grouped.computeIfAbsent(key, ignored -> warning(issue));
            item.put("count", ((Integer) item.getOrDefault("count", 0)) + 1);
        }
        return List.copyOf(grouped.values());
    }

    private static Map<String, Object> warning(QualityIssue issue) {
        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("code", issue.code());
        warning.put("message", issue.message());
        warning.put("severity", issue.severity().name().toLowerCase());
        warning.put("affectedScope", issue.dimension());
        warning.put("count", 0);
        return warning;
    }

    private static Map<String, Object> meta(SocialNetworkWorkspaceSnapshot snapshot) {
        SocialNetworkDataset dataset = snapshot.dataset();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("datasetId", dataset.datasetId());
        meta.put("datasetVersion", dataset.datasetVersion());
        meta.put("normalizationVersion", dataset.normalizationVersion());
        meta.put("metricVersion", dataset.metricVersion());
        meta.put("textProcessingVersion", dataset.textProcessingVersion());
        meta.put("explanationVersion", dataset.explanationVersion());
        meta.put("qualityStatus", snapshot.quality().datasetStatus().name().toLowerCase());
        meta.put("capabilityVersion", snapshot.capabilities().capabilityVersion());
        meta.put("scope", Map.of());
        meta.put("calculatedAt", Instant.now().toString());
        return meta;
    }

    private static ResponseEntity<SocialNetworkApiResponse<Void>> error(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(
                SocialNetworkApiResponse.error(
                        UUID.randomUUID().toString(),
                        code,
                        message,
                        Map.of("scope", Map.of())));
    }

    private static ResponseEntity<SocialNetworkApiResponse<Map<String, Object>>> badRequest(
            String code,
            String message) {
        return ResponseEntity.badRequest().body(
                SocialNetworkApiResponse.error(
                        UUID.randomUUID().toString(),
                        code,
                        message,
                        Map.of("scope", Map.of())));
    }
}
