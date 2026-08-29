package com.ctip.infrastructure.web;

import java.util.List;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * 信任的反向代理來源(docs/spec/10-identity-plans.md §10.7 的
 * 「必須設定 {@code server.forward-headers-strategy=framework} 並<strong>限定信任的代理來源</strong>」)。
 *
 * <p>只有直連對端(TCP peer)落在這個清單裡時,{@code X-Forwarded-*} / {@code Forwarded} 才會被採信。
 * 否則任何 client 都能自己送一個 {@code X-Forwarded-For},匿名限流的 IP 維度就等於不存在
 * ——每個請求都可以偽裝成不同的 IP。
 *
 * <p><strong>預設為空 = 誰都不信</strong>:此時 {@code getRemoteAddr()} 就是直連對端。
 * 直接對外時這是正確的;在反向代理後面而忘了設定,則所有 client 會被算成同一個 IP
 * (代理的位址),限流變得過嚴而不是被繞過——fail-closed。限制與設定方式記於
 * {@code docs/deployment/rate-limiting.md}。
 */
public final class TrustedProxies {

    private final List<IpAddressMatcher> matchers;

    /** @param cidrs CIDR 或單一位址(例:{@code 10.0.0.0/8}、{@code 192.168.1.10}) */
    public TrustedProxies(List<String> cidrs) {
        this.matchers = cidrs.stream()
                .map(String::trim)
                .filter(cidr -> !cidr.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();
    }

    public boolean isEmpty() {
        return matchers.isEmpty();
    }

    public boolean contains(String remoteAddr) {
        if (remoteAddr == null) {
            return false;
        }
        return matchers.stream().anyMatch(matcher -> matches(matcher, remoteAddr));
    }

    /** IpAddressMatcher 對格式不合的位址會丟例外;那代表「不匹配」,不該讓請求失敗。 */
    private static boolean matches(IpAddressMatcher matcher, String remoteAddr) {
        try {
            return matcher.matches(remoteAddr);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
