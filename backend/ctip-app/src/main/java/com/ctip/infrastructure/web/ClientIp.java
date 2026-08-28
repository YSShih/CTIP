package com.ctip.infrastructure.web;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;

/**
 * 匿名 client 的 IP 正規化(docs/spec/10-identity-plans.md §10.7):
 * IPv4 取完整位址;<strong>IPv6 取 {@code /64} 前綴</strong>——一般使用者手上就有整個 /64,
 * 不收斂等於讓限流與同步節流雙雙可被繞過。
 *
 * <p>反向代理下真實 client IP 取決於 {@code server.forward-headers-strategy} 與信任的代理來源
 * (§10.7 的警告,記載於 {@code docs/deployment/})。
 */
public final class ClientIp {

    private ClientIp() {}

    public static String normalize(String remoteAddr) {
        try {
            InetAddress address = InetAddress.getByName(remoteAddr);
            byte[] bytes = address.getAddress();
            if (bytes.length == 4) {
                return address.getHostAddress();
            }
            return "v6-" + HexFormat.of().formatHex(bytes, 0, 8);
        } catch (UnknownHostException e) {
            return remoteAddr;
        }
    }
}
