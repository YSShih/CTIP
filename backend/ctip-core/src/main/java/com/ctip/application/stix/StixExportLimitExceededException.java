package com.ctip.application.stix;

/** bundle 物件數超過方案上限(§7.8.5):API 層映射 403 PLAN_LIMIT_EXCEEDED。 */
public class StixExportLimitExceededException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public StixExportLimitExceededException(int maxObjects) {
        super("bundle 物件數超過上限 " + maxObjects + "(PLAN_LIMIT_EXCEEDED)");
    }
}
