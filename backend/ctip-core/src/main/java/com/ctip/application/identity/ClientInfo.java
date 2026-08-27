package com.ctip.application.identity;

/** Refresh token 的稽核欄位(docs/spec/04-data-dictionary.md 表 15:user_agent、ip)。 */
public record ClientInfo(String userAgent, String ip) {

    private static final int USER_AGENT_MAX_LENGTH = 512;

    public ClientInfo {
        if (userAgent != null && userAgent.length() > USER_AGENT_MAX_LENGTH) {
            userAgent = userAgent.substring(0, USER_AGENT_MAX_LENGTH);
        }
    }

    public static ClientInfo unknown() {
        return new ClientInfo(null, null);
    }
}
