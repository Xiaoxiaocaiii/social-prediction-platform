package com.prediction.socialnetwork.export;

public final class SocialNetworkExportMessages {

    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_COMPLETED_LABEL = "已完成";
    public static final String BAD_REQUEST_CODE = "SN_BAD_REQUEST";
    public static final String EMPTY_RESULT_CODE = "SN_EXPORT_EMPTY_RESULT";
    public static final String BAD_REQUEST_MESSAGE =
            "导出参数无效：格式仅支持 JSON、CSV 或可打印摘要，且必须提供导出类型。";
    public static final String EMPTY_RESULT_MESSAGE =
            "当前查询条件和筛选范围没有可导出的真实结果。";

    private SocialNetworkExportMessages() {
    }
}
