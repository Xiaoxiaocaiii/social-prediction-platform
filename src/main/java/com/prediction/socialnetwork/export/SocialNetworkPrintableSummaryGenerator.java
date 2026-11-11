package com.prediction.socialnetwork.export;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Generates a concise printable summary from the same export metadata.
 */
public final class SocialNetworkPrintableSummaryGenerator {

    public String generate(
            SocialNetworkExportMetadata metadata,
            String exportType) {
        return generate(metadata, exportType, null);
    }

    public String generate(
            SocialNetworkExportMetadata metadata,
            String exportType,
            Object result) {
        Objects.requireNonNull(metadata, "metadata");
        if (exportType == null || exportType.isBlank()) {
            throw new IllegalArgumentException("exportType must not be blank");
        }
        if ("quality".equals(exportType) && result instanceof Map<?, ?> quality) {
            return generateQualityReport(metadata, quality);
        }
        if ("network".equals(exportType) && result instanceof Map<?, ?> network) {
            return generateNetworkReport(metadata, network);
        }
        if ("bridge-candidates".equals(exportType)) {
            return generateBridgeCandidatesReport(metadata, asMap(result));
        }
        if ("users".equals(exportType)) {
            return generateUsersReport(metadata, asMap(result));
        }
        if ("interactions".equals(exportType)) {
            return generateInteractionsReport(metadata, asMap(result));
        }
        if ("topic".equals(exportType)) {
            return generateTopicReport(metadata, asMap(result));
        }
        if ("tweets".equals(exportType)) {
            return generateTweetsReport(metadata, asList(result));
        }
        return generateMetadataReport(metadata, exportType);
    }

    private static String generateBridgeCandidatesReport(
            SocialNetworkExportMetadata metadata,
            Map<?, ?> result) {
        Map<?, ?> scope = asMap(result.get("scope"));
        Map<?, ?> candidatePage = asMap(result.get("candidatePage"));
        Map<?, ?> identity = asMap(result.get("analysisIdentity"));
        List<?> candidates = asList(result.get("candidates"));
        Map<?, ?> evidencePage = asMap(result.get("evidencePage"));
        List<?> evidenceItems = asList(evidencePage.get("items"));
        StringBuilder report = reportHeader(metadata, "跨社群连接候选核对报告");

        line(report, "一、问题");
        line(report, stringValue(result.get("question"),
                "哪些资料账号与其他已归属社群存在有效直接关系？"));
        line(report, "");
        line(report, "二、结论");
        line(report, stringValue(result.get("conclusion"), "未提供候选结论。"));

        line(report, "");
        line(report, "三、口径与范围");
        Map<?, ?> methodology = asMap(result.get("methodology"));
        line(report, "- 候选条件：" + stringValue(
                methodology.get("candidateDefinition"), "未提供。"));
        line(report, "- 关系计数：" + stringValue(
                methodology.get("relationshipCounting"), "未提供。"));
        line(report, "- 排除范围：" + String.join("；",
                asList(methodology.get("excludedRelationships")).stream()
                        .map(String::valueOf).toList()));
        line(report, "- 解释边界：" + stringValue(
                methodology.get("interpretationLimit"), "未提供。"));
        line(report, "- 计算范围：" + stringValue(
                scope.get("calculationScope"), "未提供。"));
        line(report,
                "- 数量解释：连接社群数表示候选连接到的其他社群数量；关联账号数按不同账号计数。");
        line(report,
                "- 关系方向：主动关系是候选关注其他社群账号；指向该账号是其他社群账号关注候选。");
        int candidateOffset = candidatePage.get("offset") instanceof Number offset
                ? offset.intValue() : 0;
        int candidateReturnedCount = candidatePage.get("returnedCount") instanceof Number count
                ? count.intValue() : candidates.size();
        int candidateTotalCount = candidatePage.get("totalCount") instanceof Number count
                ? count.intValue() : candidates.size();
        if (candidateTotalCount == 0) {
            line(report, "- 候选范围：完整网络中没有符合条件的候选。");
        } else if (candidateReturnedCount == 0) {
            line(report, "- 候选范围：当前页未返回账号；完整候选共 "
                    + display(candidateTotalCount) + " 个。");
        } else {
            line(report, "- 候选范围：第 "
                    + display(candidateOffset + 1) + " 至 "
                    + display(candidateOffset + candidateReturnedCount)
                    + " 项；本页 " + display(candidateReturnedCount)
                    + " 个，完整候选共 " + display(candidateTotalCount) + " 个。");
        }
        if (Boolean.TRUE.equals(candidatePage.get("truncated"))) {
            line(report, "本文件只包含这一页候选，其他页面未包含。");
        }
        line(report, "- 画布筛选是否影响候选：" +
                (Boolean.TRUE.equals(scope.get("canvasFiltersApplied")) ? "是" : "否"));

        line(report, "");
        line(report, "四、候选账号");
        if (candidates.isEmpty()) {
            if (candidatePage.get("totalCount") instanceof Number totalCount
                    && totalCount.intValue() == 0) {
                line(report, "完整候选总数为 0，这是有效零结果，不代表分析不可用。");
            } else {
                line(report, "当前候选页没有返回账号，但完整候选总数不为 0；请刷新候选页后重试。");
            }
        } else {
            for (Object value : candidates) {
                Map<?, ?> candidate = asMap(value);
                report.append("- 第 ").append(display(candidate.get("rank")))
                        .append(" 项：").append(bridgeAccountLabel(candidate))
                        .append("；所属社群 ")
                        .append(display(candidate.get("communityId")))
                        .append("；连接社群 ")
                        .append(display(candidate.get("connectedCommunityCount")))
                        .append(" 个；关联账号 ")
                        .append(display(candidate.get("distinctOtherAccountCount")))
                        .append(" 个；主动关系 ")
                        .append(display(candidate.get("outgoingRelationshipCount")))
                        .append(" 条；指向该账号 ")
                        .append(display(candidate.get("incomingRelationshipCount")))
                        .append(" 条。\n");
                List<?> communities = asList(candidate.get("communities"));
                if (!communities.isEmpty()) {
                    report.append("  逐社群：")
                            .append(communities.stream()
                                    .map(SocialNetworkPrintableSummaryGenerator::
                                            bridgeCommunityLabel)
                                    .collect(java.util.stream.Collectors.joining("；")))
                            .append('\n');
                }
            }
        }

        line(report, "");
        line(report, "五、关系证据");
        if ("not_selected".equals(evidencePage.get("state"))) {
            line(report, "本次未选择具体账号的关系证据页；候选统计不代替逐条关系来源。");
        } else if (evidenceItems.isEmpty()) {
            line(report, "所选账号、社群、方向和分页范围内没有可列出的关系证据。");
        } else {
            line(report, "账号 " + display(evidencePage.get("accountId"))
                    + "；连接社群 "
                    + display(evidencePage.get("communityId"))
                    + "；方向 " + bridgeDirectionLabel(evidencePage.get("direction"))
                    + "；本页 " + evidenceItems.size() + " 条；当前筛选条件下共 "
                    + display(evidencePage.get("totalCount")) + " 条。");
            if (Boolean.TRUE.equals(evidencePage.get("truncated"))) {
                line(report,
                        "本文件仅包含已选择的这一页证据；其他页未包含，不能视为完整关系证据。");
            } else {
                line(report,
                        "本文件包含当前账号、社群和方向筛选条件下的全部证据；其他筛选范围未包含。");
            }
            for (Object value : evidenceItems) {
                Map<?, ?> evidence = asMap(value);
                report.append("- ")
                        .append(display(evidence.get("sourceId")))
                        .append(" → ").append(display(evidence.get("targetId")))
                        .append("；")
                        .append(display(evidence.get("sourceCommunityId")))
                        .append(" → ").append(display(evidence.get("targetCommunityId")))
                        .append("；")
                        .append(bridgeDirectionLabel(evidence.get("direction")))
                        .append('\n');
                for (Object sourceValue : asList(evidence.get("sources"))) {
                    Map<?, ?> source = asMap(sourceValue);
                    report.append("  来源：")
                            .append(display(source.get("sourceFileName")))
                            .append(" 第 ").append(display(source.get("sourceRowNumber")))
                            .append(" 行");
                    Object arrayIndexValue = source.get("sourceArrayIndex");
                    if (arrayIndexValue instanceof Number arrayIndex
                            && arrayIndex.intValue() >= 0) {
                        report.append("；关注列表索引 ").append(arrayIndex.intValue());
                    }
                    report.append("；关系记录：")
                            .append(bridgeRelationshipStatus(source.get("relationStatus")))
                            .append(Boolean.TRUE.equals(source.get("analysisIncluded"))
                                    ? "，计入关系统计" : "，未计入关系统计")
                            .append("；端点资料：")
                            .append(bridgeAssociationStatus(source.get("sourceNodeStatus")))
                            .append(" → ")
                            .append(bridgeAssociationStatus(source.get("targetNodeStatus")))
                            .append("；规范化：")
                            .append(bridgeNormalizationStatus(
                                    source.get("normalizationStatus")))
                            .append('\n');
                }
            }
        }

        line(report, "");
        line(report, "六、复核信息");
        line(report, "生成时间：" + metadata.exportedAt());
        line(report, "数据集版本：" + metadata.datasetVersion());
        line(report, "分析结果身份：" + stringValue(
                identity.get("analysisResultId"), "未提供"));
        line(report, "数据指纹：" + stringValue(identity.get("dataFingerprint"), "未提供"));
        line(report, "数据来源：" + metadata.sourceDescription());
        if ("not_selected".equals(evidencePage.get("state"))) {
            line(report,
                    "复核建议：使用同一分析结果身份复查候选页；如需核对具体关系，请在平台打开目标账号的关系证据页后再导出。");
        } else if (evidenceItems.isEmpty()) {
            line(report,
                    "复核建议：当前所选筛选范围内没有证据条目；可调整社群或方向后再打开证据页核对。");
        } else {
            line(report,
                    "复核建议：使用同一分析结果身份复查候选页，并依据本报告所附来源文件和行号核对关系记录。");
        }
        for (String note : metadata.boundaryNotes()) {
            line(report, "边界说明：" + note);
        }
        return report.toString();
    }

    private static String bridgeAccountLabel(Map<?, ?> candidate) {
        String name = stringValue(candidate.get("displayName"), "");
        if (name.isBlank()) {
            name = stringValue(candidate.get("username"), "");
        }
        return (name.isBlank() ? "未命名账号" : name)
                + "（ID " + display(candidate.get("accountId")) + "）";
    }

    private static String bridgeCommunityLabel(Object value) {
        Map<?, ?> community = asMap(value);
        return display(community.get("communityId")) + "：主动 "
                + display(community.get("outgoingRelationshipCount"))
                + " 条，指向该账号 "
                + display(community.get("incomingRelationshipCount")) + " 条";
    }

    private static String bridgeDirectionLabel(Object direction) {
        return switch (String.valueOf(direction)) {
            case "outgoing" -> "候选账号关注其他社群";
            case "incoming" -> "其他社群关注候选账号";
            case "all" -> "全部方向";
            default -> "方向未提供";
        };
    }

    private static String bridgeRelationshipStatus(Object status) {
        return switch (String.valueOf(status)) {
            case "VALID" -> "有效关系";
            case "DUPLICATE" -> "重复来源";
            case "SELF_LOOP" -> "自环关系";
            case "INVALID" -> "无效关系";
            default -> "关系状态未提供";
        };
    }

    private static String bridgeAssociationStatus(Object status) {
        return switch (String.valueOf(status)) {
            case "LINKED" -> "账号资料已采集";
            case "EXTERNAL_NODE" -> "关系端点，资料未采集";
            case "UNASSIGNED_SOURCE" -> "来源未归属";
            case "UNLINKED_AUTHOR" -> "作者未关联";
            case "NOT_APPLICABLE" -> "不适用";
            default -> "资料关联状态未提供";
        };
    }

    private static String bridgeNormalizationStatus(Object status) {
        return switch (String.valueOf(status)) {
            case "NORMALIZED" -> "规范化通过";
            case "NORMALIZED_WITH_WARNINGS" -> "规范化完成但有提示";
            case "REJECTED_REQUIRED_FIELD" -> "缺少必需字段";
            case "REJECTED_INVALID_FORMAT" -> "格式无效";
            default -> "规范化状态未提供";
        };
    }

    private static String generateUsersReport(
            SocialNetworkExportMetadata metadata,
            Map<?, ?> result) {
        Map<?, ?> ranking = asMap(result.get("ranking"));
        List<?> items = asList(ranking.get("items"));
        String metric = stringValue(ranking.get("metric"),
                stringValue(metadata.query().get("metric"), "indegree"));
        String metricLabel = rankingMetricLabel(metric);
        StringBuilder report = reportHeader(metadata, "重要节点排行分析报告");

        line(report, "一、结论摘要");
        if (items.isEmpty()) {
            line(report, "当前排行没有可展示的账号。");
        } else {
            Map<?, ?> top = asMap(items.get(0));
            report.append("按").append(metricLabel).append("排序，当前领先账号为 ")
                    .append(userLabel(top)).append("，结果为 ")
                    .append(display(top.get("value"))).append("。\n");
        }
        line(report, "");
        line(report, "二、指标口径");
        line(report, "- " + metricLabel + "按完整规范化网络计算，结果由高到低排列。");
        line(report, "- 同分账号使用服务端排序结果展示；结构角色排行是独立补充，不与所选指标混算。");

        line(report, "");
        line(report, "三、结果范围");
        line(report, "本次排行指标：" + metricLabel + "。");
        appendNetworkScopeValue(report, "符合条件的账号数", ranking.get("sampleSize"));
        appendNetworkScopeValue(report, "本次返回的普通排行账号数", items.size());
        appendTruncation(report, "普通排行", ranking.get("truncated"));
        appendRoleScope(report, result);

        line(report, "");
        line(report, "四、排行证据");
        appendRankedItems(report, items, 5, "账号", "value");
        appendRoleEvidence(report, result);

        appendReview(report, metadata,
                "核对所选排行指标及前列账号；k-core 和介数中心性候选名单属于不同结构口径。");
        return report.toString();
    }

    private static StringBuilder reportHeader(
            SocialNetworkExportMetadata metadata,
            String title) {
        StringBuilder report = new StringBuilder();
        line(report, title);
        line(report, "数据集：" + metadata.datasetId());
        line(report, "数据版本：" + metadata.datasetVersion());
        line(report, "");
        return report;
    }

    private static void appendReview(
            StringBuilder report,
            SocialNetworkExportMetadata metadata,
            String recommendation) {
        line(report, "");
        line(report, "五、复核信息");
        line(report, "生成时间：" + metadata.exportedAt());
        line(report, "数据集版本：" + metadata.datasetVersion());
        line(report, "数据来源：" + metadata.sourceDescription());
        appendIfPresent(report, "导出范围", metadata.scope().get("range"));
        appendIfPresent(report, "本次导出记录数",
                metadata.sampleCounts().get("resultCount"));
        if (Boolean.TRUE.equals(metadata.scope().get("truncated"))) {
            line(report, "结果状态：服务端标记为部分结果。");
        }
        line(report, "复核建议：" + recommendation);
        for (String note : metadata.boundaryNotes()) {
            line(report, "边界说明：" + note);
        }
    }

    private static void appendRankedItems(
            StringBuilder report,
            List<?> items,
            int maximum,
            String label,
            String valueKey) {
        if (items.isEmpty()) {
            line(report, "- 当前没有可展示的排行记录。");
            return;
        }
        line(report, label + "排行（最多展示 " + maximum + " 项）：");
        int visibleCount = Math.min(items.size(), maximum);
        for (int index = 0; index < visibleCount; index++) {
            Map<?, ?> item = asMap(items.get(index));
            report.append("- 第 ").append(display(item.get("rank")))
                    .append(" 名：").append(userLabel(item))
                    .append("；数值：").append(display(item.get(valueKey)))
                    .append('\n');
        }
        appendOmittedCount(report, items.size(), visibleCount);
    }

    private static void appendRoleScope(StringBuilder report, Map<?, ?> result) {
        Map<?, ?> roles = asMap(result.get("structuralRoles"));
        Map<?, ?> core = asMap(roles.get("kCore"));
        Map<?, ?> betweenness = asMap(roles.get("betweenness"));
        line(report, "补充结构候选：k-core "
                + formatNumber(asList(core.get("items")).size())
                + " 项，介数中心性 "
                + formatNumber(asList(betweenness.get("items")).size()) + " 项。");
    }

    private static void appendRoleEvidence(StringBuilder report, Map<?, ?> result) {
        Map<?, ?> roles = asMap(result.get("structuralRoles"));
        appendRankedItems(report,
                asList(asMap(roles.get("kCore")).get("items")),
                3, "k-core 候选", "value");
        appendRankedItems(report,
                asList(asMap(roles.get("betweenness")).get("items")),
                3, "介数中心性候选", "value");
    }

    private static void appendInteractionRankings(
            StringBuilder report,
            List<?> tweets,
            List<?> users,
            List<?> periods) {
        line(report, "互动量较高的推文：");
        if (tweets.isEmpty()) {
            line(report, "- 当前没有可展示的推文排行。");
        } else {
            int visibleCount = Math.min(tweets.size(), 3);
            for (int index = 0; index < visibleCount; index++) {
                Map<?, ?> item = asMap(tweets.get(index));
                report.append("- 推文 ").append(display(item.get("tweetId")))
                        .append("：").append(display(item.get("value")))
                        .append(" 次。\n");
            }
            appendOmittedCount(report, tweets.size(), visibleCount);
        }
        line(report, "互动量较高的已关联用户：");
        if (users.isEmpty()) {
            line(report, "- 当前没有可展示的用户排行。");
        } else {
            int visibleCount = Math.min(users.size(), 3);
            for (int index = 0; index < visibleCount; index++) {
                Map<?, ?> item = asMap(users.get(index));
                report.append("- ").append(display(item.get("userId")))
                        .append("：合计 ").append(display(item.get("total")))
                        .append(" 次，基于 ")
                        .append(display(item.get("sampleCount")))
                        .append(" 条推文。\n");
            }
            appendOmittedCount(report, users.size(), visibleCount);
        }
        if (!periods.isEmpty()) {
            Map<?, ?> first = asMap(periods.get(0));
            Map<?, ?> last = asMap(periods.get(periods.size() - 1));
            report.append("时间范围：")
                    .append(display(first.get("period"))).append(" 至 ")
                    .append(display(last.get("period"))).append("；首末分组分别为 ")
                    .append(display(first.get("total"))).append(" 和 ")
                    .append(display(last.get("total"))).append(" 次。\n");
        }
    }

    private static void appendTopicEvidence(
            StringBuilder report,
            List<?> tweets,
            List<?> users,
            Map<?, ?> network) {
        line(report, "匹配推文样例：");
        if (tweets.isEmpty()) {
            line(report, "- 当前没有可展示的匹配推文。");
        } else {
            int visibleCount = Math.min(tweets.size(), 3);
            for (int index = 0; index < visibleCount; index++) {
                Map<?, ?> tweet = asMap(tweets.get(index));
                report.append("- 推文 ").append(display(tweet.get("tweetId")))
                        .append("；作者 ").append(display(tweet.get("authorUserId")))
                        .append("；内容：")
                        .append(truncateText(stringValue(
                                tweet.get("textOriginal"), ""), 120))
                        .append('\n');
            }
            appendOmittedCount(report, tweets.size(), visibleCount);
        }
        line(report, "关联用户样例：");
        if (users.isEmpty()) {
            line(report, "- 当前没有已关联用户。");
        } else {
            int visibleCount = Math.min(users.size(), 3);
            for (int index = 0; index < visibleCount; index++) {
                Map<?, ?> user = asMap(users.get(index));
                report.append("- ").append(display(user.get("userId")))
                        .append("：匹配推文 ")
                        .append(display(user.get("matchedTweetCount")))
                        .append(" 条。\n");
            }
            appendOmittedCount(report, users.size(), visibleCount);
        }
        line(report, "相关网络："
                + display(network.get("nodeCount")) + " 个用户节点，"
                + display(network.get("totalEdgeCount")) + " 条有效关注关系。");
    }

    private static void appendTruncation(
            StringBuilder report,
            String label,
            Object truncated) {
        if (Boolean.TRUE.equals(truncated)) {
            line(report, label
                    + "按服务端上限截取，完整范围见结构化导出元信息。");
        }
    }

    private static String userLabel(Map<?, ?> item) {
        Object displayName = item.get("displayName");
        if (displayName != null && !String.valueOf(displayName).isBlank()) {
            return String.valueOf(displayName);
        }
        Object username = item.get("username");
        if (username != null && !String.valueOf(username).isBlank()) {
            return "@" + username;
        }
        return display(item.get("nodeId"));
    }

    private static String firstText(Map<?, ?> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).strip();
            }
        }
        return "";
    }

    private static String truncateText(String text, int maximumLength) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        return normalized.length() <= maximumLength
                ? normalized
                : normalized.substring(0, maximumLength) + "…";
    }

    private static String stringValue(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank()
                ? fallback
                : String.valueOf(value);
    }

    private static String rankingMetricLabel(String metric) {
        return switch (metric.toLowerCase(Locale.ROOT)) {
            case "outdegree" -> "主动关注数";
            case "pagerank" -> "网络位置估算";
            default -> "被关注数";
        };
    }

    private static String interactionMetricLabel(String metric) {
        return switch (metric.toLowerCase(Locale.ROOT)) {
            case "reply_count" -> "评论数";
            case "repost_count" -> "转发数";
            default -> "点赞数";
        };
    }

    private static String generateInteractionsReport(
            SocialNetworkExportMetadata metadata,
            Map<?, ?> result) {
        String metric = stringValue(result.get("metric"),
                stringValue(metadata.query().get("metric"), "like_count"));
        String metricLabel = interactionMetricLabel(metric);
        Map<?, ?> summary = asMap(result.get("summary"));
        Map<?, ?> metrics = asMap(summary.get("metrics"));
        Map<?, ?> metricSummary = asMap(metrics.get(metric));
        Map<?, ?> tweets = asMap(result.get("tweets"));
        Map<?, ?> users = asMap(result.get("users"));
        Map<?, ?> timeseries = asMap(result.get("timeseries"));
        List<?> tweetItems = asList(tweets.get("items"));
        List<?> userItems = asList(users.get("items"));
        List<?> periods = asList(timeseries.get("items"));
        StringBuilder report = reportHeader(metadata, "推文互动分析报告");

        line(report, "一、结论摘要");
        if (metricSummary.isEmpty()) {
            line(report, "当前数据没有提供所选互动指标的汇总统计。");
        } else {
            report.append("本次分析的").append(metricLabel).append("合计 ")
                    .append(display(metricSummary.get("total")))
                    .append("，有效样本 ")
                    .append(display(metricSummary.get("validSampleCount")))
                    .append(" 条，单条平均 ")
                    .append(display(metricSummary.get("average"))).append("。\n");
        }
        if (!tweetItems.isEmpty()) {
            Map<?, ?> topTweet = asMap(tweetItems.get(0));
            report.append("该指标最高的已返回推文为 ")
                    .append(display(topTweet.get("tweetId"))).append("，数值 ")
                    .append(display(topTweet.get("value"))).append("。\n");
        }

        line(report, "");
        line(report, "二、指标口径");
        line(report, "- 当前选择：" + metricLabel + "；点赞、评论和转发分别统计，不合成为综合分。");
        line(report, "- 推文排行仅纳入该指标有有效数值的去重推文；用户排行仅纳入作者已关联的推文。");
        line(report, "- 时间分组依据推文发布时间；没有有效日期或指标值的记录不进入时间统计。");

        line(report, "");
        line(report, "三、结果范围");
        line(report, "互动指标：" + metricLabel + "。");
        appendNetworkScopeValue(report, "有效互动样本数",
                metricSummary.get("validSampleCount"));
        appendNetworkScopeValue(report, "返回推文排行数", tweetItems.size());
        appendNetworkScopeValue(report, "返回用户排行数", userItems.size());
        appendNetworkScopeValue(report, "返回时间分组数", periods.size());
        appendTruncation(report, "互动分析列表", result.get("truncated"));
        appendTruncation(report, "推文排行", tweets.get("truncated"));
        appendTruncation(report, "用户排行", users.get("truncated"));
        appendTruncation(report, "时间分组", timeseries.get("truncated"));

        line(report, "");
        line(report, "四、互动证据");
        if (!metricSummary.isEmpty()) {
            line(report, "- 合计：" + display(metricSummary.get("total"))
                    + "；中位数：" + display(metricSummary.get("median"))
                    + "；缺失值：" + display(metricSummary.get("missingCount"))
                    + "；零值：" + display(metricSummary.get("zeroCount")) + "。");
        }
        appendInteractionRankings(report, tweetItems, userItems, periods);

        appendReview(report, metadata,
                "对照本报告标出的互动指标与 JSON/CSV 中同名指标字段；不同互动类型不可互相替代。");
        return report.toString();
    }

    private static String generateTopicReport(
            SocialNetworkExportMetadata metadata,
            Map<?, ?> result) {
        String term = stringValue(result.get("query"),
                stringValue(metadata.query().get("term"), ""));
        List<?> tweets = asList(result.get("tweets"));
        List<?> users = asList(result.get("users"));
        Map<?, ?> network = asMap(result.get("network"));
        StringBuilder report = reportHeader(metadata, "主题查询分析报告");

        line(report, "一、结论摘要");
        report.append("精确词元“").append(term).append("”匹配到 ")
                .append(display(result.get("matchedTweetCount")))
                .append(" 条推文，关联 ")
                .append(display(result.get("relatedUserCount")))
                .append(" 个用户。\n");
        report.append("这些已关联用户之间存在 ")
                .append(display(network.get("totalEdgeCount")))
                .append(" 条真实关注关系；该关系只表示关注记录，不代表主题传播路径。\n");

        line(report, "");
        line(report, "二、指标口径");
        line(report, "- 主题命中：推文规范化后的分词结果中，存在与查询词完全相同的词元。");
        line(report, "- 关联用户：至少发布一条匹配推文且能关联到用户资料的账号。");
        line(report, "- 主题关系：只统计关联用户之间已有的有效关注关系，不推断因果或传播。");

        line(report, "");
        line(report, "三、结果范围");
        line(report, "本次查询词：" + (term.isBlank() ? "未提供" : term) + "。");
        appendNetworkScopeValue(report, "命中推文总数", result.get("matchedTweetCount"));
        appendNetworkScopeValue(report, "本次返回推文数", tweets.size());
        appendNetworkScopeValue(report, "关联用户总数", result.get("relatedUserCount"));
        appendNetworkScopeValue(report, "本次返回用户数", users.size());
        appendNetworkScopeValue(report, "相关用户间有效关注关系数",
                network.get("totalEdgeCount"));
        appendTruncation(report, "主题查询结果", result.get("truncated"));
        appendTruncation(report, "关注关系明细", network.get("truncated"));

        line(report, "");
        line(report, "四、主题证据");
        appendTopicEvidence(report, tweets, users, network);

        appendReview(report, metadata,
                "确认报告中的查询词与上次成功提交的查询一致；原文、作者和完整关系明细以对应结构化结果为准。");
        return report.toString();
    }

    private static String generateTweetsReport(
            SocialNetworkExportMetadata metadata,
            List<?> tweets) {
        StringBuilder report = reportHeader(metadata, "推文样本导出报告");
        long linkedAuthors = tweets.stream()
                .filter(item -> asMap(item).get("authorUserId") != null)
                .count();

        line(report, "一、结论摘要");
        report.append("本次导出包含 ")
                .append(formatNumber(tweets.size()))
                .append(" 条去重且纳入分析的推文；其中 ")
                .append(formatNumber(linkedAuthors))
                .append(" 条带有关联作者账号。\n");

        line(report, "");
        line(report, "二、指标口径");
        line(report, "- 推文样本来自当前数据集，且已通过去重与分析纳入检查。");
        line(report, "- 本报告说明推文范围和少量样例，不替代 CSV/JSON 中的原始字段与完整记录。");

        line(report, "");
        line(report, "三、结果范围");
        line(report, "导出范围：全部去重且纳入分析的推文样本。");
        line(report, "本次返回：" + formatNumber(tweets.size()) + " 条推文。");
        appendNetworkScopeValue(report, "数据集推文总数",
                metadata.sampleCounts().get("tweetCount"));

        line(report, "");
        line(report, "四、推文样例");
        int visibleCount = Math.min(tweets.size(), 5);
        if (visibleCount == 0) {
            line(report, "- 当前没有可展示的推文样例。");
        }
        for (int index = 0; index < visibleCount; index++) {
            Map<?, ?> tweet = asMap(tweets.get(index));
            Map<?, ?> fields = asMap(tweet.get("fields"));
            report.append("- 推文 ").append(index + 1).append("：")
                    .append(display(tweet.get("tweetId")))
                    .append("；作者：").append(display(tweet.get("authorUserId")));
            String text = firstText(fields, "text", "full_text", "text_original", "content");
            if (!text.isBlank()) {
                report.append("；内容：").append(truncateText(text, 120));
            }
            report.append('\n');
        }
        appendOmittedCount(report, tweets.size(), visibleCount);

        appendReview(report, metadata,
                "逐条核对时请以本次 JSON/CSV 导出的原始推文记录为准；本报告中的样例可能被截短。");
        return report.toString();
    }

    private static String generateMetadataReport(
            SocialNetworkExportMetadata metadata,
            String exportType) {
        StringBuilder report = reportHeader(metadata, "社交网络分析导出报告");
        line(report, "一、结论摘要");
        line(report, "当前导出类型没有可读报告生成规则，未将机器字段路径冒充为分析结论。");
        line(report, "");
        line(report, "二、指标口径");
        line(report, "该导出类型：" + exportType + "。");
        line(report, "");
        line(report, "三、结果范围");
        line(report, stringValue(metadata.scope().get("range"), "范围未提供。"));
        line(report, "");
        line(report, "四、证据");
        line(report, "本次报告未获得可解释的业务结果；请使用对应的 JSON/CSV 结构化导出。");
        appendReview(report, metadata, "使用对应的结构化结果复核；本摘要不包含业务结论。");
        return report.toString();
    }

    private static String generateQualityReport(
            SocialNetworkExportMetadata metadata,
            Map<?, ?> quality) {
        Map<?, ?> summary = asMap(quality.get("summary"));
        Map<?, ?> coverage = asMap(quality.get("coverage"));
        Map<?, ?> users = asMap(quality.get("userQuality"));
        Map<?, ?> relationships = asMap(quality.get("relationshipQuality"));
        Map<?, ?> tweets = asMap(quality.get("tweetQuality"));
        Map<?, ?> text = asMap(quality.get("textQuality"));
        List<?> issues = asList(quality.get("issues"));
        List<?> warnings = asList(quality.get("warnings"));
        Map<String, Object> scope = metadata.scope();
        StringBuilder report = new StringBuilder();

        line(report, "社交网络数据质量分析报告");
        report.append("数据集：").append(metadata.datasetId()).append('\n');
        report.append("数据版本：").append(metadata.datasetVersion()).append('\n');

        line(report, "");
        line(report, "一、结论摘要");
        report.append("数据状态：")
                .append(datasetStatusLabel(quality.get("datasetStatus")))
                .append('\n');
        Object issueCount = summary.get("quality_issue_count");
        Object impactCount = summary.get("capability_impact_count");
        report.append("本次检查生成 ")
                .append(display(issueCount))
                .append(" 个质量问题项，")
                .append(display(impactCount))
                .append(" 项分析功能受到影响。问题项数不等于受影响的原始记录数。")
                .append('\n');
        report.append("本次检查归并后有 ")
                .append(formatNumber(warnings.size()))
                .append(" 条质量提示；具体内容见“质量证据”。")
                .append('\n');
        line(report,
                "每条提示后的归并问题项数不等于受影响的原始记录数。");

        line(report, "");
        line(report, "二、指标口径");
        line(report, "- 文件数：参与本次质量检查的输入文件个数。");
        line(report, "- 质量问题项：检查生成的问题提示条目数，不代表问题类型数或异常原始行数。");
        line(report, "- 受影响分析功能：因数据质量受到限制的功能种类数。");
        line(report,
                "- 归并提示：将规则、范围和说明相同的问题项合并展示；提示后的数量是合并的问题项数。");
        appendCoverageBasis(report, "用户记录纳入率",
                users.get("normalized_user_count"), users.get("raw_user_count"),
                "通过必要字段检查、可进入后续分析的用户数",
                "原始用户记录数");
        appendCoverageBasis(report, "关注关系有效比例",
                relationships.get("normalized_relationship_count"),
                relationships.get("candidate_relationship_count"),
                "检查后保留的有效关注关系数",
                "关注关系候选记录数");
        appendCoverageBasis(report, "推文作者匹配率",
                tweets.get("linked_author_count"), tweets.get("raw_tweet_count"),
                "作者能匹配到用户资料的推文数",
                "原始推文记录数");
        appendCoverageBasis(report, "非空文本比例",
                text.get("valid_text_count"), tweets.get("raw_tweet_count"),
                "规范化后含非空文本的推文数",
                "原始推文记录数");
        line(report,
                "分母为 0 时服务端将比例记为 0；这表示没有可比较记录，不代表有记录但覆盖率为 0。");
        line(report, "非空只表示文本字段有内容，不代表文本适合主题分析。");

        line(report, "");
        line(report, "三、结果范围");
        line(report,
                "检查范围：当前数据集的质量摘要与服务端返回的聚合提示；不受页面分类和本地分页筛选影响。");
        appendIfPresent(report, "输入文件数", summary.get("file_count"));
        appendIfPresent(report, "原始用户记录", summary.get("user_record_count"));
        appendIfPresent(report, "原始关注关系记录",
                summary.get("relationship_record_count"));
        appendIfPresent(report, "关注关系候选记录",
                summary.get("relationship_candidate_count"));
        appendIfPresent(report, "原始推文记录", summary.get("tweet_record_count"));
        Object truncated = scope.get("truncated");
        if (Boolean.TRUE.equals(truncated)) {
            line(report, "结果状态：服务端已标记为截断，不能视作完整结果。");
        }

        line(report, "");
        line(report, "四、质量证据");
        appendCoverageEvidence(report, "用户记录纳入率",
                coverage.get("user_id_coverage"),
                users.get("normalized_user_count"), users.get("raw_user_count"));
        appendCoverageEvidence(report, "关注关系有效比例",
                coverage.get("relationship_inclusion_rate"),
                relationships.get("normalized_relationship_count"),
                relationships.get("candidate_relationship_count"));
        appendCoverageEvidence(report, "推文作者匹配率",
                coverage.get("tweet_author_link_rate"),
                tweets.get("linked_author_count"), tweets.get("raw_tweet_count"));
        appendCoverageEvidence(report, "非空文本比例",
                coverage.get("valid_text_rate"),
                text.get("valid_text_count"), tweets.get("raw_tweet_count"));
        appendIssues(report, issues);
        appendWarnings(report, warnings);

        line(report, "");
        line(report, "五、复核信息");
        line(report, "生成时间：" + metadata.exportedAt());
        line(report, "数据集版本：" + metadata.datasetVersion());
        line(report, "数据规范化版本：" + metadata.normalizationVersion());
        line(report, "网络指标版本：" + metadata.metricVersion());
        line(report, "文本处理版本：" + metadata.textProcessingVersion());
        line(report, "解释版本：" + metadata.explanationVersion());
        line(report, "数据来源：" + metadata.sourceDescription());
        line(report,
                "复核建议：将本报告中的问题提示与原始文件及 JSON 结构化结果对照；聚合提示不提供逐行定位。");
        return report.toString();
    }

    private static String generateNetworkReport(
            SocialNetworkExportMetadata metadata,
            Map<?, ?> network) {
        Map<?, ?> pageRank = asMap(network.get("pageRank"));
        Map<?, ?> evidenceSummary = asMap(network.get("evidenceSummary"));
        List<?> facts = asList(evidenceSummary.get("facts"));
        Map<String, Object> scope = metadata.scope();
        StringBuilder report = new StringBuilder();

        line(report, "社交网络结构分析报告");
        report.append("数据集：").append(metadata.datasetId()).append('\n');
        report.append("数据版本：").append(metadata.datasetVersion()).append('\n');

        line(report, "");
        line(report, "一、结论摘要");
        Object nodeCount = network.get("nodeCount");
        Object edgeCount = network.get("edgeCount");
        report.append("当前完整网络包含 ")
                .append(display(nodeCount)).append(" 个账号节点和 ")
                .append(display(edgeCount)).append(" 条有效关注关系。\n");
        if (network.get("largestWeaklyConnectedComponentNodeCount") != null
                && network.get("largestWeaklyConnectedComponentRatio") != null) {
            report.append("忽略关注方向后，最大连接组包含 ")
                    .append(display(network.get(
                            "largestWeaklyConnectedComponentNodeCount")))
                    .append(" 个账号，占全网 ")
                    .append(formatPercent(
                            network.get("largestWeaklyConnectedComponentRatio")))
                    .append("；另有 ")
                    .append(display(network.get("isolatedNodeCount")))
                    .append(" 个没有有效关系的账号。\n");
        }
        if (pageRank.get("converged") instanceof Boolean converged) {
            report.append("网络位置估算")
                    .append(converged ? "已稳定。" : "在迭代上限内尚未稳定。")
                    .append("该结果描述网络结构位置，不代表现实影响力。\n");
        }

        line(report, "");
        line(report, "二、指标口径");
        line(report,
                "- 有效关注关系：按 A 关注 B 记录方向，重复关系合并，自关注和无效关系不计入。");
        line(report,
                "- 连接组：为识别关系链路而暂时忽略关注方向；同属一组不代表账号之间有直接关系或存在单向可达路径。");
        line(report,
                "- 网络位置估算（PageRank）：依据有方向的关注关系反复计算账号的相对结构位置，不等同于真实互动、社会影响力或账号价值。");
        line(report,
                "- 网络密度：有效有向关系数除以不同账号之间可能形成的有向关系数。");
        line(report,
                "- 互相关注比例：存在反向关注的关系条数占全部有效关注关系的比例；双向关注按两条有向关系计数。");
        line(report,
                "- 平均聚类系数：忽略关系方向后，统计账号相邻节点之间彼此相连的程度，再对全体账号取平均。");
        line(report,
                "- 最大连接组平均路径长度：忽略关系方向后，计算最大连接组内账号两两之间最短关系步数的平均值。");

        line(report, "");
        line(report, "三、结果范围");
        line(report, "统计范围：当前数据集的完整规范化关注网络，不受页面分页影响。");
        appendNetworkScopeValue(report, "账号节点", nodeCount);
        appendNetworkScopeValue(report, "资料账号", network.get("profileUserCount"));
        appendNetworkScopeValue(report, "关系中出现但无资料的账号",
                network.get("externalNodeCount"));
        appendNetworkScopeValue(report, "有效有向关注关系", edgeCount);
        appendNetworkScopeValue(report, "连接组数量",
                network.get("weaklyConnectedComponentCount"));
        appendNetworkScopeValue(report, "最大连接组账号数",
                network.get("largestWeaklyConnectedComponentNodeCount"));
        appendNetworkScopeValue(report, "孤立账号数", network.get("isolatedNodeCount"));
        appendNetworkScopeValue(report, "网络密度", network.get("density"));
        appendNetworkScopeValue(report, "互相关注比例", network.get("reciprocityRate"));
        Object truncated = scope.get("truncated");
        if (Boolean.TRUE.equals(truncated)) {
            line(report, "结果状态：服务端已标记为截断，不能视作完整结果。");
        }

        line(report, "");
        line(report, "四、证据");
        appendNetworkEvidence(report, network, facts);

        line(report, "");
        line(report, "五、复核信息");
        line(report, "生成时间：" + metadata.exportedAt());
        line(report, "数据集版本：" + metadata.datasetVersion());
        line(report, "资料外账号：" +
                (Boolean.TRUE.equals(network.get("includeExternalNodes"))
                        ? "纳入统计" : "未纳入或未提供"));
        line(report, "自关注关系：" +
                (Boolean.TRUE.equals(network.get("excludeSelfLoops"))
                        ? "已排除" : "未排除或未提供"));
        line(report, "数据来源：" + metadata.sourceDescription());
        line(report,
                "复核建议：将本报告中的网络规模、连接组和位置估算结果与同一数据版本的 JSON/CSV 结构化结果对照。");
        return report.toString();
    }

    private static void appendNetworkEvidence(
            StringBuilder report,
            Map<?, ?> network,
            List<?> facts) {
        appendEvidenceValue(report, "最大连接组占比",
                network.get("largestWeaklyConnectedComponentRatio"), true);
        appendEvidenceValue(report, "平均聚类系数",
                network.get("averageClusteringCoefficient"), true);
        appendEvidenceValue(report, "最大连接组平均路径长度",
                network.get("largestWeaklyConnectedComponentAveragePathLength"), false);

        line(report, "可核验事实：");
        if (facts.isEmpty()) {
            line(report, "- 本报告未附逐账号或逐关系的原始记录证据；以上数值是按当前数据集计算的网络汇总指标。");
            return;
        }
        int visibleCount = Math.min(facts.size(), 3);
        for (int index = 0; index < visibleCount; index++) {
            Map<?, ?> fact = asMap(facts.get(index));
            report.append("- ")
                    .append(networkFactText(fact))
                    .append("（核验状态：")
                    .append(evidenceStateLabel(fact.get("state")))
                    .append("）\n");
            Object reason = fact.get("reason");
            if (reason != null && !String.valueOf(reason).isBlank()) {
                report.append("  说明：").append(reason).append('\n');
            }
            List<?> references = asList(fact.get("evidenceReferences"));
            if (!references.isEmpty()) {
                report.append("  来源：")
                        .append(references.stream()
                                .map(SocialNetworkPrintableSummaryGenerator::
                                        evidenceReferenceLabel)
                                .distinct()
                                .collect(java.util.stream.Collectors.joining("；")))
                        .append('\n');
            }
        }
        appendOmittedCount(report, facts.size(), visibleCount);
    }

    private static String networkFactText(Map<?, ?> fact) {
        Map<?, ?> values = asMap(fact.get("evidenceValues"));
        return switch (String.valueOf(fact.get("id"))) {
            case "network-scope" -> "全网包含 "
                    + display(values.get("nodeCount")) + " 个账号节点、"
                    + display(values.get("edgeCount")) + " 条有效关注关系；其中资料账号 "
                    + display(values.get("profileUserCount")) + " 个，资料外账号 "
                    + display(values.get("externalNodeCount")) + " 个。";
            case "connectivity" -> "忽略关注方向后，账号分为 "
                    + display(values.get("weaklyConnectedComponentCount"))
                    + " 个连接组；最大组包含 "
                    + display(values.get("largestWeaklyConnectedComponentNodeCount"))
                    + " 个账号（占全网 "
                    + formatPercent(values.get("largestWeaklyConnectedComponentRatio"))
                    + "），孤立账号 "
                    + display(values.get("isolatedNodeCount")) + " 个。";
            case "k-core-distribution" -> "关系圈层分析覆盖 "
                    + display(values.get("sampleSize"))
                    + " 个账号；最高连接层级为 "
                    + display(values.get("maxCoreNumber")) + "，该层包含 "
                    + display(values.get("highestCoreCount"))
                    + " 个账号。层级只描述连接结构，不代表账号重要性。";
            default -> display(fact.get("text"));
        };
    }

    private static void appendEvidenceValue(
            StringBuilder report,
            String label,
            Object value,
            boolean percent) {
        if (value == null) {
            return;
        }
        report.append("- ").append(label).append("：")
                .append(percent ? formatPercent(value) : display(value))
                .append('\n');
    }

    private static void appendNetworkScopeValue(
            StringBuilder report,
            String label,
            Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            report.append(label).append("：").append(display(value)).append('\n');
        }
    }

    private static String evidenceStateLabel(Object state) {
        if (state == null) {
            return "未提供";
        }
        return switch (String.valueOf(state).toLowerCase(Locale.ROOT)) {
            case "available" -> "可核验";
            case "limited" -> "有限";
            case "unavailable" -> "不可用";
            case "empty" -> "暂无数据";
            default -> "未提供";
        };
    }

    private static String evidenceReferenceLabel(Object reference) {
        String value = String.valueOf(reference);
        if (value.contains("/network/summary")) {
            return "网络汇总指标";
        }
        if (value.contains("/network/structure-roles")) {
            return "网络结构角色分析";
        }
        if (value.contains("/network/evidence/nodes")) {
            return "全网节点证据";
        }
        return "网络分析证据";
    }

    private static void appendCoverageBasis(
            StringBuilder report,
            String label,
            Object numerator,
            Object denominator,
            String numeratorDescription,
            String denominatorDescription) {
        report.append("- ").append(label).append("：")
                .append(numeratorDescription).append(" ÷ ")
                .append(denominatorDescription).append("（")
                .append(display(numerator)).append(" ÷ ")
                .append(display(denominator)).append("）。\n");
    }

    private static void appendCoverageEvidence(
            StringBuilder report,
            String label,
            Object rate,
            Object numerator,
            Object denominator) {
        report.append("- ").append(label).append("：")
                .append(formatPercent(rate))
                .append("（").append(display(numerator)).append(" / ")
                .append(display(denominator)).append("）\n");
    }

    private static void appendIssues(StringBuilder report, List<?> issues) {
        line(report, "检查问题项：");
        if (issues.isEmpty()) {
            line(report, "- 当前导出接口未提供逐项质量问题明细。");
            return;
        }
        int visibleCount = Math.min(issues.size(), 30);
        for (int index = 0; index < visibleCount; index++) {
            Map<?, ?> issue = asMap(issues.get(index));
            report.append("- [")
                    .append(severityLabel(issue.get("severity")))
                    .append("] ")
                    .append(display(issue.get("message")));
            appendEvidenceLocation(report, issue);
            report.append('\n');
        }
        appendOmittedCount(report, issues.size(), visibleCount);
    }

    private static void appendWarnings(StringBuilder report, List<?> warnings) {
        line(report, "归并提示：");
        if (warnings.isEmpty()) {
            line(report, "- 服务端未返回质量提示。");
            return;
        }
        int visibleCount = Math.min(warnings.size(), 30);
        for (int index = 0; index < visibleCount; index++) {
            Map<?, ?> warning = asMap(warnings.get(index));
            report.append("- [")
                    .append(severityLabel(warning.get("severity")))
                    .append("] ")
                    .append(display(warning.get("message")))
                    .append("（作用范围：")
                    .append(dimensionLabel(warning.get("affectedScope")))
                    .append("；归并的问题项数：")
                    .append(display(warning.get("count")))
                    .append("）\n");
        }
        appendOmittedCount(report, warnings.size(), visibleCount);
    }

    private static void appendEvidenceLocation(
            StringBuilder report,
            Map<?, ?> issue) {
        Object fileName = issue.get("sourceFileName");
        if (fileName != null && !String.valueOf(fileName).isBlank()) {
            report.append("（文件：").append(fileName);
            List<?> rows = asList(issue.get("sourceRowNumbers"));
            if (!rows.isEmpty()) {
                report.append("；涉及行：")
                        .append(rows.stream().map(String::valueOf)
                                .collect(java.util.stream.Collectors.joining("、")));
            }
            report.append("）");
        }
    }

    private static void appendOmittedCount(
            StringBuilder report,
            int totalCount,
            int visibleCount) {
        if (totalCount > visibleCount) {
            report.append("- 另有 ")
                    .append(formatNumber(totalCount - visibleCount))
                    .append(" 项未在本摘要中展开，请通过 JSON 结构化结果复核。\n");
        }
    }

    private static void appendIfPresent(
            StringBuilder report,
            String label,
            Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            report.append(label).append(": ").append(display(value)).append('\n');
        }
    }

    private static String datasetStatusLabel(Object status) {
        if (status == null) {
            return "未提供";
        }
        return switch (String.valueOf(status).toLowerCase(Locale.ROOT)) {
            case "healthy" -> "健康";
            case "available" -> "可用";
            case "available_with_warnings" -> "可用（含质量提示）";
            case "partial", "limited" -> "部分可用";
            case "unavailable" -> "不可用";
            case "empty" -> "暂无数据";
            case "failed" -> "检查失败";
            default -> "未提供";
        };
    }

    private static String severityLabel(Object severity) {
        if (severity == null) {
            return "级别未提供";
        }
        return switch (String.valueOf(severity).toLowerCase(Locale.ROOT)) {
            case "blocking" -> "阻断";
            case "error" -> "错误";
            case "warning" -> "警告";
            case "info" -> "提示";
            default -> "级别未提供";
        };
    }

    private static String dimensionLabel(Object dimension) {
        if (dimension == null) {
            return "未提供";
        }
        return switch (String.valueOf(dimension).toLowerCase(Locale.ROOT)) {
            case "users", "user" -> "用户";
            case "relationships", "relationship" -> "关注关系";
            case "tweets", "tweet" -> "推文";
            case "interactions", "interaction" -> "互动";
            case "text" -> "文本";
            case "other", "file" -> "文件或其他";
            default -> String.valueOf(dimension);
        };
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static String display(Object value) {
        if (value == null) {
            return "未提供";
        }
        return value instanceof Number number
                ? formatNumber(number)
                : String.valueOf(value);
    }

    private static String formatNumber(Number value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.CHINA);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    private static String formatPercent(Object value) {
        if (!(value instanceof Number number)
                || !Double.isFinite(number.doubleValue())) {
            return "未提供";
        }
        NumberFormat format = NumberFormat.getPercentInstance(Locale.CHINA);
        format.setMaximumFractionDigits(1);
        return format.format(number.doubleValue());
    }

    private static void line(StringBuilder summary, String label, String value) {
        summary.append(label).append(": ").append(value).append('\n');
    }

    private static void line(StringBuilder summary, String title) {
        summary.append(title).append('\n');
    }

}
