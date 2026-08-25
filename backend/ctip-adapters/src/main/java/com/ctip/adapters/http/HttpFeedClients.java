package com.ctip.adapters.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

/**
 * 共用 HTTP feed 基礎設施(docs/spec/08-ingestion-sdk.md §8.5 的 timeout 契約):
 * connect 5s / read 30s 由 {@link ResiliencePolicy} 提供。真實外部 adapter(§8.4,M2 起)
 * 一律經此建立 client 與 request,不得自帶 timeout 設定。
 */
public final class HttpFeedClients {

    private HttpFeedClients() {}

    public static HttpClient newClient(ResiliencePolicy policy) {
        return HttpClient.newBuilder()
                .connectTimeout(policy.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** 讀取逾時掛在 request 層(JDK HttpClient 的設計);呼叫端補上 URI 專屬標頭後送出。 */
    public static HttpRequest.Builder feedRequest(URI uri, ResiliencePolicy policy) {
        return HttpRequest.newBuilder(uri).timeout(policy.readTimeout()).GET();
    }
}
