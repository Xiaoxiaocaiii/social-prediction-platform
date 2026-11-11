package com.prediction.socialnetwork.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class SocialNetworkBridgeCandidateExportService {

    private static final int PAGE_SIZE = 20;

    private final SocialNetworkBridgeCandidateService candidateService;
    private final ObjectMapper objectMapper;

    public SocialNetworkBridgeCandidateExportService(
            SocialNetworkBridgeCandidateService candidateService,
            ObjectMapper objectMapper) {
        this.candidateService = Objects.requireNonNull(candidateService, "candidateService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public ExportData assemble(String datasetId, Map<String, Object> query) {
        return assemble(datasetId, null, null, query);
    }

    public ExportData assemble(
            String datasetId,
            String datasetVersion,
            String sessionId,
            Map<String, Object> query) {
        int offset = nonNegativeInt(query.get("offset"), 0, "offset");
        int pageSize = boundedPageSize(query.get("pageSize"), PAGE_SIZE, "pageSize");
        String expectedAnalysisResultId =
                requireText(query.get("analysisResultId"), "analysisResultId");

        SocialNetworkBridgeCandidateService.CandidateListView candidates =
                sessionId == null
                        ? candidateService.candidates(datasetId, offset, pageSize)
                        : candidateService.candidates(
                                datasetId, datasetVersion, sessionId, offset, pageSize);
        if ("unavailable".equals(candidates.state())) {
            throw new ExportUnavailableException(
                    candidates.reason() == null ? "candidate_result_unavailable"
                            : candidates.reason());
        }
        SocialNetworkBridgeCandidateService.AnalysisIdentity identity =
                candidates.analysisIdentity();
        if (identity == null
                || !expectedAnalysisResultId.equals(identity.analysisResultId())) {
            throw new ExportUnavailableException("analysis_result_changed");
        }
        if (offset > 0 && offset >= candidates.totalCount()) {
            throw new ExportUnavailableException("analysis_result_changed");
        }

        Map<String, Object> evidencePage = Map.of(
                "state", "not_selected",
                "note", "本次未选择具体账号的关系证据页；候选汇总不能替代逐条关系来源。");
        Object evidenceQueryValue = query.get("evidence");
        if (evidenceQueryValue != null) {
            Map<String, Object> evidenceQuery = mapValue(evidenceQueryValue, "evidence");
            String accountId = requireText(evidenceQuery.get("accountId"), "evidence.accountId");
            if (candidates.items().stream()
                    .noneMatch(item -> accountId.equals(item.accountId()))) {
                throw new IllegalArgumentException(
                        "evidence account must belong to the exported candidate page");
            }
            String communityId = optionalText(evidenceQuery.get("communityId"));
            String direction = text(evidenceQuery.get("direction"), "all");
            int evidenceOffset = nonNegativeInt(
                    evidenceQuery.get("offset"), 0, "evidence.offset");
            int evidencePageSize = boundedPageSize(
                    evidenceQuery.get("pageSize"), PAGE_SIZE, "evidence.pageSize");

            SocialNetworkBridgeCandidateService.EvidenceView evidence =
                    sessionId == null
                            ? candidateService.evidence(
                                    datasetId,
                                    accountId,
                                    communityId,
                                    direction,
                                    expectedAnalysisResultId,
                                    evidenceOffset,
                                    evidencePageSize)
                            : candidateService.evidence(
                                    datasetId,
                                    datasetVersion,
                                    sessionId,
                                    accountId,
                                    communityId,
                                    direction,
                                    expectedAnalysisResultId,
                                    evidenceOffset,
                                    evidencePageSize);
            if ("unavailable".equals(evidence.state())) {
                throw new ExportUnavailableException(
                        evidence.reason() == null ? "evidence_unavailable" : evidence.reason());
            }
            if (evidence.analysisIdentity() == null
                    || !expectedAnalysisResultId.equals(
                            evidence.analysisIdentity().analysisResultId())
                    || !Objects.equals(
                            identity.dataFingerprint(),
                            evidence.analysisIdentity().dataFingerprint())) {
                throw new ExportUnavailableException("analysis_result_changed");
            }
            evidencePage = new LinkedHashMap<>(convert(evidence));
            int evidenceReturnedCount = evidence.items().size();
            evidencePage.put("returnedCount", evidenceReturnedCount);
            evidencePage.put("truncated", evidenceOffset > 0
                    || evidenceOffset + evidenceReturnedCount < evidence.totalCount());
        }

        List<Map<String, Object>> candidateItems = candidates.items().stream()
                .map(this::convert)
                .toList();
        int returnedCount = candidateItems.size();
        boolean truncated = offset > 0
                || offset + returnedCount < candidates.totalCount();
        Map<String, Object> candidatePage = new LinkedHashMap<>();
        candidatePage.put("offset", offset);
        candidatePage.put("pageSize", pageSize);
        candidatePage.put("returnedCount", returnedCount);
        candidatePage.put("totalCount", candidates.totalCount());
        candidatePage.put("truncated", truncated);
        candidatePage.put("items", candidateItems);

        Map<String, Object> range = new LinkedHashMap<>();
        range.put("calculationScope", "完整规范化网络及当前已完成的 Louvain 社群结果");
        range.put("candidatePageOffset", offset);
        range.put("candidatePageSize", pageSize);
        range.put("candidateReturnedCount", returnedCount);
        range.put("candidateTotalCount", candidates.totalCount());
        range.put("candidatePageTruncated", truncated);
        range.put("canvasFiltersApplied", false);
        range.put("selectedEvidencePageIncluded",
                !"not_selected".equals(evidencePage.get("state")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", "empty".equals(candidates.state()) ? "empty" : "available");
        result.put("question",
                "当前候选页中，哪些资料账号与其他已归属社群存在有效直接关系？");
        result.put("conclusion", conclusion(candidates.totalCount(), returnedCount));
        result.put("methodology", Map.of(
                "candidateDefinition",
                "仅列出有账号资料且已归属社群的账号；至少连接一个其他已归属社群才入选。",
                "relationshipCounting",
                "按完整网络中的有效有向关系计数；重复来源不重复增加关系数。",
                "excludedRelationships",
                List.of("自环", "同社群关系", "端点未归属社群的关系"),
                "interpretationLimit",
                "候选用于逐项技术核对，不是影响力排行，也不代表传播控制能力。"));
        result.put("scope", range);
        result.put("analysisIdentity", convert(identity));
        result.put("candidatePage", candidatePage);
        result.put("candidates", candidateItems);
        result.put("evidencePage", evidencePage);
        return new ExportData(
                result, returnedCount, candidates.totalCount(), truncated);
    }

    private static String conclusion(int totalCount, int returnedCount) {
        if (totalCount == 0) {
            return "完整网络中没有符合既定条件的资料账号；这是有效的零结果，不是分析不可用。";
        }
        if (returnedCount == 0) {
            return "完整网络中共有 " + totalCount
                    + " 个候选，但当前页没有返回账号；请刷新候选页后再导出。";
        }
        return "完整网络中共有 " + totalCount
                + " 个跨社群连接候选。候选仅供核对，不作影响力结论。";
    }

    private Map<String, Object> convert(Object value) {
        return objectMapper.convertValue(
                value, new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    private static Map<String, Object> mapValue(Object value, String name) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException(name + " must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static int nonNegativeInt(Object value, int defaultValue, String name) {
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number number)
                || number.longValue() < 0
                || number.longValue() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " must be a non-negative integer");
        }
        return number.intValue();
    }

    private static int boundedPageSize(Object value, int defaultValue, String name) {
        int pageSize = nonNegativeInt(value, defaultValue, name);
        if (pageSize < 1 || pageSize > PAGE_SIZE) {
            throw new IllegalArgumentException(name + " must be between 1 and " + PAGE_SIZE);
        }
        return pageSize;
    }

    private static String requireText(Object value, String name) {
        String text = optionalText(value);
        if (text == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }

    private static String optionalText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().strip();
        return text.isEmpty() ? null : text;
    }

    private static String text(Object value, String defaultValue) {
        String text = optionalText(value);
        return text == null ? defaultValue : text;
    }

    public record ExportData(
            Map<String, Object> result,
            int returnedCount,
            int totalCount,
            boolean truncated) {
    }

    public static final class ExportUnavailableException extends RuntimeException {

        private final String reason;

        public ExportUnavailableException(String reason) {
            super(reason);
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }
}
