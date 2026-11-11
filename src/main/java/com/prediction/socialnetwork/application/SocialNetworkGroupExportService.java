package com.prediction.socialnetwork.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prediction.socialnetwork.export.SocialNetworkCsvSerializer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SocialNetworkGroupExportService {

    public static final int MAX_EXPORT_BYTES = 2 * 1024 * 1024;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final CliqueEnumerationWorkflowService cliqueWorkflow;
    private final LouvainAnalysisWorkflowService louvainWorkflow;
    private final SocialNetworkGroupEvidenceService evidenceService;
    private final SocialNetworkDatasetRegistry registry;
    private final ObjectMapper objectMapper;
    private final SocialNetworkCsvSerializer csvSerializer =
            new SocialNetworkCsvSerializer();

    public SocialNetworkGroupExportService(
            CliqueEnumerationWorkflowService cliqueWorkflow,
            LouvainAnalysisWorkflowService louvainWorkflow,
            SocialNetworkGroupEvidenceService evidenceService,
            ObjectMapper objectMapper) {
        this(cliqueWorkflow, louvainWorkflow, evidenceService, objectMapper, null);
    }

    @Autowired
    public SocialNetworkGroupExportService(
            CliqueEnumerationWorkflowService cliqueWorkflow,
            LouvainAnalysisWorkflowService louvainWorkflow,
            SocialNetworkGroupEvidenceService evidenceService,
            ObjectMapper objectMapper,
            SocialNetworkDatasetRegistry registry) {
        this.cliqueWorkflow = Objects.requireNonNull(cliqueWorkflow, "cliqueWorkflow");
        this.louvainWorkflow = Objects.requireNonNull(louvainWorkflow, "louvainWorkflow");
        this.evidenceService = Objects.requireNonNull(evidenceService, "evidenceService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.registry = registry;
    }

    public ExportResult export(String datasetId, ExportRequest request) {
        return export(datasetId, null, null, request);
    }

    public ExportResult export(
            String datasetId,
            String datasetVersion,
            String sessionId,
            ExportRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        String type = normalized(request.analysisType(), "analysisType");
        String format = normalized(request.format(), "format");
        if (!"clique".equals(type) && !"louvain".equals(type)) {
            throw new IllegalArgumentException("analysisType must be clique or louvain");
        }
        if (!List.of("json", "csv", "print").contains(format)) {
            throw new IllegalArgumentException("format must be json, csv, or print");
        }
        if (registry != null) {
            if (datasetVersion == null && sessionId == null) {
                if (registry.findDataset(datasetId).isUploadSession()) {
                    throw new SocialNetworkDatasetContextException();
                }
            } else {
                registry.resolveDatasetContext(datasetId, datasetVersion, sessionId);
            }
        }

        int offset = nonNegative(request.offset(), 0, "offset");
        int pageSize = boundedPageSize(request.pageSize(), "pageSize");
        Object analysis;
        Map<String, Object> analysisMap;
        Map<String, Object> scope = new LinkedHashMap<>();
        if ("clique".equals(type)) {
            CliqueEnumerationWorkflowView view =
                    cliqueStatus(datasetId, datasetVersion, sessionId, offset, pageSize);
            rejectRunningAnalysis(view.state());
            if ("NOT_REQUESTED".equals(view.state()) || view.dataFingerprint() == null) {
                throw new EmptyResultException();
            }
            analysis = view;
            analysisMap = objectMap(view);
            scope.put("range", "当前 Clique 列表页");
            scope.put("offset", view.offset());
            scope.put("pageSize", view.pageSize());
            scope.put("returnedCount", view.cliques().size());
            scope.put("totalCount", view.retainedCliqueCount());
            scope.put("truncated",
                    view.offset() + view.cliques().size() < view.retainedCliqueCount());
        } else {
            LouvainAnalysisWorkflowView view =
                    louvainStatus(datasetId, datasetVersion, sessionId);
            rejectRunningAnalysis(view.state());
            if ("NOT_REQUESTED".equals(view.state()) || view.dataFingerprint() == null) {
                throw new EmptyResultException();
            }
            analysis = view;
            analysisMap = objectMap(view);
            scope.put("range", "当前 Louvain 完整分区");
            scope.put("offset", 0);
            scope.put("pageSize", view.communities().size());
            scope.put("returnedCount", view.communities().size());
            scope.put("totalCount", view.communities().size());
            scope.put("truncated", false);
        }

        Map<String, Object> evidence = null;
        if (request.evidence() != null) {
            SocialNetworkGroupEvidenceView evidenceView =
                    readEvidence(
                            datasetId,
                            datasetVersion,
                            sessionId,
                            type,
                            request.evidence());
            if (!Objects.equals(
                    analysisMap.get("dataFingerprint"), evidenceView.dataFingerprint())) {
                throw new IllegalStateException("analysis changed while assembling export");
            }
            boolean sameAnalysis;
            if (analysis instanceof CliqueEnumerationWorkflowView before) {
                CliqueEnumerationWorkflowView current =
                        cliqueStatus(datasetId, datasetVersion, sessionId, offset, pageSize);
                sameAnalysis = Objects.equals(before.datasetId(), current.datasetId())
                        && Objects.equals(
                                before.datasetVersion(), current.datasetVersion())
                        && Objects.equals(before.cacheKey(), current.cacheKey())
                        && Objects.equals(before.dataFingerprint(), current.dataFingerprint())
                        && Objects.equals(before.requestedAt(), current.requestedAt())
                        && Objects.equals(before.finishedAt(), current.finishedAt());
            } else {
                LouvainAnalysisWorkflowView before = (LouvainAnalysisWorkflowView) analysis;
                LouvainAnalysisWorkflowView current =
                        louvainStatus(datasetId, datasetVersion, sessionId);
                sameAnalysis = Objects.equals(before.cacheKey(), current.cacheKey())
                        && Objects.equals(before.dataFingerprint(), current.dataFingerprint())
                        && Objects.equals(before.requestedAt(), current.requestedAt())
                        && Objects.equals(before.finishedAt(), current.finishedAt());
            }
            if (!sameAnalysis) {
                throw new IllegalStateException("analysis changed while assembling export");
            }
            evidence = objectMap(evidenceView);
            scope.put("evidence", evidenceScope(evidenceView));
        } else {
            scope.put("evidence", Map.of("included", false));
        }

        Map<String, Object> metadata = metadata(type, analysisMap, scope, sessionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analysis", analysisMap);
        result.put("evidence", evidence);

        String contentType;
        Object content;
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("exportType", type);
        file.put("metadata", metadata);
        file.put("data", result);
        switch (format) {
            case "csv" -> {
                content = csv(result, metadata);
                contentType = "text/csv; charset=UTF-8";
            }
            case "print" -> {
                content = printable(result, metadata);
                contentType = "text/plain; charset=UTF-8";
            }
            default -> {
                try {
                    content = objectMapper.writeValueAsString(file);
                } catch (JsonProcessingException exception) {
                    throw new IllegalStateException(
                            "Unable to serialize export content", exception);
                }
                contentType = "application/json; charset=UTF-8";
            }
        }
        int contentLength = utf8Length(content);
        if (contentLength > MAX_EXPORT_BYTES) {
            throw new ExportTooLargeException();
        }
        return new ExportResult(type, format, contentType, contentLength, metadata, content);
    }

    private SocialNetworkGroupEvidenceView readEvidence(
            String datasetId,
            String datasetVersion,
            String sessionId,
            String analysisType,
            EvidenceTarget target) {
        String targetType = normalized(target.type(), "evidence.type");
        String section = normalized(target.section(), "evidence.section");
        int offset = nonNegative(target.offset(), 0, "evidence.offset");
        int pageSize = boundedPageSize(target.pageSize(), "evidence.pageSize");
        if (!"members".equals(section) && !"ties".equals(section)) {
            throw new IllegalArgumentException("evidence.section must be members or ties");
        }
        if ("clique".equals(targetType) && "clique".equals(analysisType)) {
            int index;
            try {
                index = Integer.parseInt(target.id());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("evidence.id must be a clique index");
            }
            return datasetVersion == null && sessionId == null
                    ? evidenceService.clique(
                            datasetId, index, section, offset, pageSize)
                    : evidenceService.clique(
                            datasetId, datasetVersion, sessionId,
                            index, section, offset, pageSize);
        }
        if ("louvain".equals(targetType) && "louvain".equals(analysisType)) {
            String communityId = requireText(target.id(), "evidence.id");
            return sessionId == null
                    ? evidenceService.louvain(
                            datasetId, communityId, section, offset, pageSize)
                    : evidenceService.louvain(
                            datasetId, datasetVersion, sessionId,
                            communityId, section, offset, pageSize);
        }
        if ("between".equals(targetType) && "louvain".equals(analysisType)) {
            if (!"ties".equals(section)) {
                throw new IllegalArgumentException(
                        "between-community evidence only supports ties");
            }
            String first = requireText(target.id(), "evidence.id");
            String second = requireText(target.secondId(), "evidence.secondId");
            return sessionId == null
                    ? evidenceService.betweenCommunities(
                            datasetId, first, second, offset, pageSize)
                    : evidenceService.betweenCommunities(
                            datasetId, datasetVersion, sessionId, first, second,
                            offset, pageSize);
        }
        throw new IllegalArgumentException("evidence target does not match analysisType");
    }

    private LouvainAnalysisWorkflowView louvainStatus(
            String datasetId, String datasetVersion, String sessionId) {
        return sessionId == null
                ? louvainWorkflow.status(datasetId)
                : louvainWorkflow.status(datasetId, datasetVersion, sessionId);
    }

    private CliqueEnumerationWorkflowView cliqueStatus(
            String datasetId,
            String datasetVersion,
            String sessionId,
            int offset,
            int pageSize) {
        return datasetVersion == null && sessionId == null
                ? cliqueWorkflow.status(datasetId, offset, pageSize)
                : cliqueWorkflow.status(
                        datasetId, datasetVersion, sessionId, offset, pageSize);
    }

    private static Map<String, Object> evidenceScope(SocialNetworkGroupEvidenceView view) {
        int returnedCount = view.evidenceAvailable()
                ? "members".equals(view.section()) ? view.members().size() : view.ties().size()
                : 0;
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("included", true);
        scope.put("available", view.evidenceAvailable());
        scope.put("unavailableReason", view.unavailableReason());
        scope.put("targetId", view.targetId());
        scope.put("section", view.section());
        scope.put("offset", view.offset());
        scope.put("pageSize", view.pageSize());
        scope.put("returnedCount", returnedCount);
        scope.put("totalCount", view.totalCount());
        scope.put("truncated",
                view.evidenceAvailable() && view.offset() + returnedCount < view.totalCount());
        return scope;
    }

    private static void rejectRunningAnalysis(String state) {
        if ("RUNNING".equals(state) || "BUSY".equals(state)) {
            throw new AnalysisRunningException();
        }
    }

    private static Map<String, Object> metadata(
            String type,
            Map<String, Object> analysis,
            Map<String, Object> scope,
            String sessionId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("datasetId", analysis.get("datasetId"));
        metadata.put("datasetVersion", analysis.get("datasetVersion"));
        if (sessionId != null) {
            metadata.put("sessionId", sessionId);
        }
        metadata.put("analysisType", type);
        metadata.put("analysisState", analysis.get("state"));
        metadata.put("complete", analysis.get("complete"));
        metadata.put("dataFingerprint", analysis.get("dataFingerprint"));
        metadata.put("cacheKey", analysis.get("cacheKey"));
        metadata.put("algorithmVersion", analysis.get("algorithmVersion"));
        metadata.put("algorithmParameters", analysis.get("algorithmParameters"));
        metadata.put("inputContractVersion", analysis.get("inputContractVersion"));
        metadata.put("relationProjectionVersion", analysis.get("relationProjectionVersion"));
        metadata.put("projection", analysis.get("projection"));
        metadata.put("analysisScope", analysis.get("analysisScope"));
        metadata.put("inputFiles", analysis.get("inputFiles"));
        metadata.put("requestedAt", analysis.get("requestedAt"));
        metadata.put("finishedAt", analysis.get("finishedAt"));
        if ("clique".equals(type)) {
            metadata.put("membershipListComplete", analysis.get("membershipListComplete"));
        } else {
            metadata.put("workerCompleted", analysis.get("workerCompleted"));
            metadata.put("converged", analysis.get("converged"));
            metadata.put("qualityContractVersion", analysis.get("qualityContractVersion"));
        }
        metadata.put("scope", scope);
        return metadata;
    }

    private String csv(Map<String, Object> result, Map<String, Object> metadata) {
        List<Map<String, ?>> rows = new ArrayList<>();
        flatten(rows, "metadata", "", metadata);
        flatten(rows, "analysis", "", result.get("analysis"));
        flatten(rows, "evidence", "", result.get("evidence"));
        return csvSerializer.serialize(List.of("recordType", "path", "value"), rows);
    }

    private String printable(Map<String, Object> result, Map<String, Object> metadata) {
        Map<String, Object> analysis = stringMap(result.get("analysis"));
        Map<String, Object> scope = stringMap(metadata.get("scope"));
        String type = stringValue(metadata.get("analysisType"));
        StringBuilder text = new StringBuilder("社交网络分析报告\n");
        appendField(text, "分析类型",
                "clique".equals(type) ? "Clique 极大团分析" : "Louvain 社群分析");
        appendField(text, "导出时间（UTC）", java.time.Instant.now().toString());
        if ("clique".equals(type)) {
            appendCliqueReport(text, analysis, scope);
        } else {
            appendLouvainReport(text, analysis);
        }
        appendEvidenceReport(text, result.get("evidence"), scope);
        appendVerificationReport(text, analysis, metadata);
        return text.toString();
    }

    private void flatten(
            List<Map<String, ?>> rows,
            String recordType,
            String path,
            Object value) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> flatten(
                    rows, recordType, appendPath(path, String.valueOf(key)), nested));
            return;
        }
        if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                flatten(rows, recordType, path + "[" + index + "]", list.get(index));
            }
            if (list.isEmpty()) {
                addCsvRow(rows, recordType, path, "[]");
            }
            return;
        }
        addCsvRow(rows, recordType, path, value);
    }

    private static void appendCliqueReport(
            StringBuilder text,
            Map<String, Object> analysis,
            Map<String, Object> scope) {
        appendSection(text, "结论摘要");
        int totalCliques = intValue(analysis.get("retainedCliqueCount"), 0);
        appendField(text, "识别到的极大团总数", count(totalCliques));
        appendField(text, "最大团规模",
                count(intValue(analysis.get("largestObservedCliqueSize"), 0)) + " 个账号");
        appendField(text, "分析状态", analysisStatus(analysis));
        text.append("同一账号可能出现在多个团中，团的数量不等于不同现实组织的数量。\n");

        appendSection(text, "分析范围与口径");
        appendField(text, "数据集", datasetLabel(analysis));
        appendField(text, "输入账号数", count(intValue(analysis.get("inputNodeCount"), 0)));
        appendField(text, "输入有向关注关系数",
                count(intValue(analysis.get("inputDirectedEdgeCount"), 0)));
        appendField(text, "关系处理方式", projectionDescription(analysis.get("projection")));
        appendField(text, "团的定义",
                "团内任意两个账号之间至少有一条关注关系；每个团至少 3 个账号，且不能再加入其他账号。");

        int offset = intValue(scope.get("offset"), 0);
        int returned = intValue(scope.get("returnedCount"), 0);
        appendSection(text, "本次导出范围");
        if (returned == 0) {
            appendField(text, "当前页", "没有团条目");
        } else {
            appendField(text, "当前页", "第 " + count(offset + 1) + " 到 "
                    + count(offset + returned) + " 个团");
        }
        appendField(text, "全量结果",
                count(intValue(scope.get("totalCount"), totalCliques)) + " 个团");
        if (Boolean.TRUE.equals(scope.get("truncated"))) {
            text.append("说明：本文件只列出当前页，其余团仍保存在完整分析结果中。\n");
        }

        appendSection(text, "团规模分布");
        Map<String, Object> distribution = stringMap(analysis.get("cliqueSizeDistribution"));
        distribution.entrySet().stream()
                .sorted((left, right) -> Integer.compare(
                        integerKey(left.getKey()), integerKey(right.getKey())))
                .forEach(entry -> appendField(
                        text,
                        count(integerKey(entry.getKey())) + " 个账号的团",
                        count(intValue(entry.getValue(), 0)) + " 个"));

        appendSection(text, "当前页团成员");
        List<?> cliques = listValue(analysis.get("cliques"));
        if (cliques.isEmpty()) {
            text.append("当前页没有可列出的团。\n");
        }
        for (int index = 0; index < cliques.size(); index++) {
            List<?> members = listValue(cliques.get(index));
            text.append("团 ").append(count(offset + index + 1))
                    .append("（").append(count(members.size())).append(" 个账号）\n");
            for (Object member : members) {
                text.append("  - 账号 ID：").append(stringValue(member)).append('\n');
            }
        }
        appendTopParticipation(text, analysis.get("nodeParticipationCounts"));
    }

    private static void appendLouvainReport(
            StringBuilder text,
            Map<String, Object> analysis) {
        Map<String, Object> quality = stringMap(analysis.get("quality"));
        List<?> communities = listValue(analysis.get("communities"));
        appendSection(text, "结论摘要");
        appendField(text, "划分出的社群数", count(communities.size()));
        appendField(text, "已分配账号数",
                count(intValue(quality.get("assignedNodeCount"), 0)));
        appendField(text, "未分配孤立账号数",
                count(intValue(analysis.get("unassignedNodeCount"), 0)));
        appendField(text, "社群内部关系数",
                count(intValue(quality.get("internalTieCount"), 0)));
        appendField(text, "社群之间关系数",
                count(intValue(quality.get("crossCommunityTieCount"), 0)));
        appendField(text, "模块度 Q", decimal(quality.get("modularity")));
        appendField(text, "算法状态", analysisStatus(analysis));
        text.append("社群是按网络连接模式得到的算法分组，不代表现实组织、身份或因果关系。\n");

        appendSection(text, "分析范围与口径");
        appendField(text, "数据集", datasetLabel(analysis));
        appendField(text, "输入账号数", count(intValue(analysis.get("inputNodeCount"), 0)));
        appendField(text, "输入有向关注关系数",
                count(intValue(analysis.get("inputDirectedRelationshipCount"), 0)));
        appendField(text, "关系处理方式", projectionDescription(analysis.get("projection")));

        appendSection(text, "社群列表");
        for (Object item : communities) {
            Map<String, Object> community = stringMap(item);
            text.append(stringValue(community.get("id")))
                    .append("：").append(count(intValue(community.get("nodeCount"), 0)))
                    .append(" 个账号，内部关系 ")
                    .append(count(intValue(community.get("internalTieCount"), 0)))
                    .append(" 条\n");
        }
        text.append("完整账号归属表未在报告中逐项展开；可使用 JSON/CSV 导出查看明细。\n");
    }

    private static void appendTopParticipation(StringBuilder text, Object value) {
        Map<String, Object> participation = stringMap(value);
        if (participation.isEmpty()) {
            return;
        }
        appendSection(text, "参与多个团的账号（前 10 名）");
        text.append("次数表示账号出现在多少个极大团中，不是粉丝数或关注关系数；账号以 ID 标识。\n");
        List<Map.Entry<String, Object>> ranked = new ArrayList<>(participation.entrySet());
        ranked.sort((left, right) -> {
            int byCount = Integer.compare(
                    intValue(right.getValue(), 0), intValue(left.getValue(), 0));
            return byCount != 0 ? byCount : left.getKey().compareTo(right.getKey());
        });
        ranked.stream()
                .filter(entry -> intValue(entry.getValue(), 0) > 0)
                .limit(10)
                .forEach(entry -> appendField(
                        text,
                        "账号 ID " + entry.getKey(),
                        count(intValue(entry.getValue(), 0)) + " 个团"));
    }

    private static void appendEvidenceReport(
            StringBuilder text,
            Object evidenceValue,
            Map<String, Object> scope) {
        Map<String, Object> evidenceScope = stringMap(scope.get("evidence"));
        appendSection(text, "来源证据");
        if (!Boolean.TRUE.equals(evidenceScope.get("included"))) {
            text.append("本次未附加成员或关系来源证据。\n");
            return;
        }
        if (!Boolean.TRUE.equals(evidenceScope.get("available"))) {
            appendField(text, "证据状态", "不可用");
            appendField(text, "原因", evidenceScope.get("unavailableReason"));
            return;
        }

        Map<String, Object> evidence = stringMap(evidenceValue);
        String section = stringValue(evidence.get("section"));
        appendField(text, "证据对象", evidence.get("targetId"));
        appendField(text, "证据类型",
                "members".equals(section) ? "成员资料来源" : "关系来源");
        appendField(text, "本页证据数",
                count(intValue(evidenceScope.get("returnedCount"), 0)) + " / "
                        + count(intValue(evidenceScope.get("totalCount"), 0)));
        if ("members".equals(section)) {
            for (Object item : listValue(evidence.get("members"))) {
                Map<String, Object> member = stringMap(item);
                text.append("账号 ID：").append(stringValue(member.get("nodeId")))
                        .append("（").append(stringValue(member.get("nodeType"))).append("）\n");
                for (Object source : listValue(member.get("profileSources"))) {
                    appendSourceReference(text, stringMap(source), false);
                }
            }
        } else {
            for (Object item : listValue(evidence.get("ties"))) {
                Map<String, Object> tie = stringMap(item);
                text.append(stringValue(tie.get("leftNodeId"))).append(" — ")
                        .append(stringValue(tie.get("rightNodeId")))
                        .append("（无向关系，权重 ")
                        .append(count(intValue(tie.get("weight"), 0))).append("）\n");
                for (Object source : listValue(tie.get("supportingRelationships"))) {
                    appendSourceReference(text, stringMap(source), true);
                }
                for (Object source : listValue(tie.get("duplicateRelationshipRecords"))) {
                    text.append("  重复原始记录：");
                    appendSourceReference(text, stringMap(source), true);
                }
            }
        }
    }

    private static void appendSourceReference(
            StringBuilder text,
            Map<String, Object> source,
            boolean relationship) {
        text.append("  来源：").append(stringValue(source.get("sourceFileName")))
                .append(" 第 ").append(count(intValue(source.get("sourceRowNumber"), 0)))
                .append(" 行");
        if (relationship) {
            text.append("，").append(stringValue(source.get("sourceId")))
                    .append(" → ").append(stringValue(source.get("targetId")));
            int arrayIndex = intValue(source.get("sourceArrayIndex"), -1);
            if (arrayIndex >= 0) {
                text.append("，列表索引 ").append(count(arrayIndex));
            }
        }
        text.append('\n');
    }

    private static void appendVerificationReport(
            StringBuilder text,
            Map<String, Object> analysis,
            Map<String, Object> metadata) {
        appendSection(text, "数据核对说明");
        appendField(text, "结果状态",
                Boolean.TRUE.equals(metadata.get("complete")) ? "完整" : "未完整");
        appendField(text, "导出说明", "本报告整理当前数据版本已生成的分析结果，未重新运行分析");
        for (Object item : listValue(analysis.get("inputFiles"))) {
            Map<String, Object> file = stringMap(item);
            appendField(text, "使用文件（" + fileRoleLabel(file.get("role")) + "）",
                    file.get("logicalPath"));
        }
    }

    private static String fileRoleLabel(Object role) {
        return switch (stringValue(role).toLowerCase(Locale.ROOT)) {
            case "users", "user" -> "账号资料";
            case "relationships", "relationship" -> "关注关系";
            case "tweets", "tweet" -> "推文";
            case "interactions", "interaction" -> "互动";
            default -> "数据";
        };
    }

    private static void appendSection(StringBuilder text, String title) {
        text.append('\n').append(title).append('\n');
    }

    private static void appendField(StringBuilder text, String label, Object value) {
        text.append(label).append("：")
                .append(value == null ? "未提供" : value)
                .append('\n');
    }

    private static String analysisStatus(Map<String, Object> analysis) {
        String state = stringValue(analysis.get("state"));
        boolean complete = Boolean.TRUE.equals(analysis.get("complete"));
        if ("COMPLETED".equals(state) && complete) {
            return "已完成，结果完整";
        }
        if ("COMPLETED_UNCONVERGED".equals(state)) {
            return "已结束，但尚未收敛";
        }
        return state + (complete ? "，结果完整" : "，结果不完整");
    }

    private static String datasetLabel(Map<String, Object> analysis) {
        return stringValue(analysis.get("datasetId")) + "（"
                + stringValue(analysis.get("datasetVersion")) + "）";
    }

    private static String projectionDescription(Object projection) {
        String normalized = stringValue(projection).toLowerCase(Locale.ROOT);
        if ("undirected_simple_any_direction".equals(normalized)
                || "undirected_simple".equals(normalized)) {
            return "任一方向有关注即视为相连；重复关系合并，双向关注只计为一条无向关系。";
        }
        return "按关注关系判断账号之间是否相连，重复关系不重复计数。";
    }

    private static Map<String, Object> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, nested) -> result.put(String.valueOf(key), nested));
        return result;
    }

    private static List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static String stringValue(Object value) {
        return value == null ? "未提供" : String.valueOf(value);
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static int integerKey(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static String count(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static String decimal(Object value) {
        return value instanceof Number number
                ? String.format(Locale.US, "%.4f", number.doubleValue())
                : stringValue(value);
    }

    private static void addCsvRow(
            List<Map<String, ?>> rows,
            String recordType,
            String path,
            Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("recordType", recordType);
        row.put("path", path);
        row.put("value", value);
        rows.add(row);
    }

    private static String appendPath(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    private int utf8Length(Object content) {
        return String.valueOf(content).getBytes(StandardCharsets.UTF_8).length;
    }

    private Map<String, Object> objectMap(Object value) {
        return objectMapper.convertValue(value, new TypeReference<>() { });
    }

    private static int boundedPageSize(Integer value, String field) {
        int pageSize = value == null ? DEFAULT_PAGE_SIZE : value;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(field + " must be between 1 and 100");
        }
        return pageSize;
    }

    private static int nonNegative(Integer value, int defaultValue, String field) {
        int normalized = value == null ? defaultValue : value;
        if (normalized < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return normalized;
    }

    private static String normalized(String value, String field) {
        return requireText(value, field).toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    public record ExportRequest(
            String analysisType,
            String format,
            Integer offset,
            Integer pageSize,
            EvidenceTarget evidence) {
    }

    public record EvidenceTarget(
            String type,
            String id,
            String secondId,
            String section,
            Integer offset,
            Integer pageSize) {
    }

    public record ExportResult(
            String exportType,
            String format,
            String contentType,
            int contentLength,
            Map<String, Object> metadata,
            Object result) {
    }

    public static final class EmptyResultException extends RuntimeException {
    }

    public static final class ExportTooLargeException extends RuntimeException {
    }

    public static final class AnalysisRunningException extends RuntimeException {
    }
}
