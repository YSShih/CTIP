package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.support.TestPlans;
import com.redis.testcontainers.RedisContainer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * DoD M2-09:Redis 限流在<strong>兩個 app 實例</strong>下正確
 * (docs/spec/10-identity-plans.md §10.7、15 §15.2)。
 *
 * <p>這是 {@code RATE_LIMIT_BACKEND=redis} 唯一能被證明的地方:記憶體後端在兩個實例下
 * 各有一份桶,配額等於變成兩倍——單一實例的測試無論怎麼寫都看不出差別。
 * 因此本測試真的起<strong>兩個 Spring context</strong>(各自的 web server、各自的連線池),
 * 只共用同一個 PostgreSQL 與同一個 Redis,並斷言:實例 1 把配額用完後,
 * 同一個 client 打實例 2 <strong>立刻</strong>被拒。
 *
 * <p>順帶驗證分散式快取({@code CachePort}):方案配額在實例 1 改動後,實例 2 必須立即看到
 * ——行程內的 map 做不到這件事(舊值會活到 TTL 到期),而那正是 Phase 17 把兩個
 * ad-hoc 快取換成 {@code CachePort} 的理由。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "ctip.rate-limit.backend=redis")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DistributedRateLimitTest extends AbstractPostgresIntegrationTest {

    /** 打滿 3 次就超限;真實的匿名配額是 60/min,打 60 次只是讓測試變慢。 */
    private static final int PER_MINUTE = 3;

    private static final RedisContainer REDIS = new RedisContainer("redis:8-alpine");

    static {
        REDIS.start();
    }

    @LocalServerPort
    private int firstPort;

    @Autowired
    private PlanRepository plans;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private IdGeneratorPort idGenerator;

    @Autowired
    private ClockPort clock;

    /**
     * 用 JDK 的 HttpClient 而不是 {@code TestRestTemplate}:Boot 4 把後者移到了
     * {@code spring-boot-restclient-test},而版本表沒有列那個模組(規則 6:不得自行增加相依)。
     */
    private final HttpClient http = HttpClient.newHttpClient();

    private ConfigurableApplicationContext secondInstance;
    private int secondPort;
    private TestPlans planAdmin;
    private Plan originalAnonymous;

    @DynamicPropertySource
    static void redisEnvironment(DynamicPropertyRegistry registry) {
        // 走 §5.7 的環境變數對應(REDIS_HOST/PORT → spring.data.redis.*),連這條也一起驗
        registry.add("REDIS_HOST", REDIS::getRedisHost);
        registry.add("REDIS_PORT", REDIS::getRedisPort);
    }

    /**
     * 第二個實例。刻意用 {@link SpringApplicationBuilder} 真的啟動一個完整的 app,
     * 而不是在同一個 context 裡多做一個 {@code RedisRateLimiter}——後者證明不了
     * 「兩個行程共用配額」,只證明了「兩個物件指向同一個 Redis」。
     */
    @BeforeAll
    void startSecondInstance() {
        secondInstance = new SpringApplicationBuilder(CtipApplication.class)
                .profiles("mvp")
                .properties(secondInstanceProperties())
                .run(OVERRIDES);
        secondPort = Integer.parseInt(secondInstance.getEnvironment().getProperty("local.server.port", "0"));
        // 兩個實例必須真的是兩個 web server,而且都接在同一個 Redis 後端上——
        // 這兩件事若不成立,後面的斷言會以難懂的數字失敗(實測過:第二個實例綁到固定的
        // 8080 時,請求打到的是別的 app,錯誤訊息只會說「expected 3 but was 60」)
        assertThat(secondPort).isNotZero().isNotEqualTo(firstPort);
        assertThat(secondInstance.getEnvironment().getProperty("ctip.rate-limit.backend"))
                .as("第二個實例也必須用 redis 後端,否則量到的是兩份各自的桶")
                .isEqualTo("redis");
    }

    @AfterAll
    void stopSecondInstance() {
        if (secondInstance != null) {
            secondInstance.close();
        }
    }

    /**
     * 這幾項必須以<strong>命令列參數</strong>傳入,不能放進 {@code properties(...)}。
     *
     * <p>{@code SpringApplication.setDefaultProperties} 是<strong>優先序最低</strong>的來源,
     * 排在 `application.yml` 與 `application-mvp.yml` 之後——實測時
     * {@code server.port=0} 被 `application.yml` 的 {@code ${SERVER_PORT:8080}} 蓋掉,
     * 第二個實例因此綁在固定的 8080;`dod.sh mvp` 的 M1-38 在 mvp 容器已佔用 8080 時執行,
     * 於是三個案例全紅(請求打到的是容器裡的另一個 app)。命令列參數的優先序高於 yml。
     * 只有名稱帶底線的那些(下面的 map)可以留在 defaultProperties——它們只被
     * {@code ${...}} 佔位符引用,佔位符會在整個 Environment 裡找得到。
     */
    private static final String[] OVERRIDES = {
        "--server.port=0", "--spring.sql.init.mode=never", "--spring.devtools.restart.enabled=false"
    };

    private Map<String, Object> secondInstanceProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("POSTGRES_HOST", POSTGRES.getHost());
        properties.put("POSTGRES_PORT", POSTGRES.getFirstMappedPort());
        properties.put("POSTGRES_DB", POSTGRES.getDatabaseName());
        properties.put("POSTGRES_USER", POSTGRES.getUsername());
        properties.put("POSTGRES_PASSWORD", POSTGRES.getPassword());
        properties.put("POSTGRES_APP_USER", APP_USER);
        properties.put("POSTGRES_APP_PASSWORD", APP_PASSWORD);
        // 保留清理連線(Phase 21):空密碼會讓 Hikari 在啟動時就丟 SCRAM 認證失敗
        properties.put("POSTGRES_RETENTION_USER", RETENTION_USER);
        properties.put("POSTGRES_RETENTION_PASSWORD", RETENTION_PASSWORD);
        properties.put("ENVIRONMENT", "mvp");
        properties.put("JWT_SECRET", TEST_JWT_SECRET);
        properties.put("WEBHOOK_SECRET_KEK", "integration-test-only-webhook-kek-0123456789");
        // 與 AbstractPostgresIntegrationTest 同一個理由:每個 context 一個連線池,
        // 預設 10 條乘上快取住的 context 數會撞上 PostgreSQL 的 max_connections
        properties.put("spring.datasource.hikari.maximum-pool-size", 4);
        properties.put("CORS_ALLOWED_ORIGINS", "http://localhost:5173");
        properties.put("SCHEDULER_ENABLED", "false");
        properties.put("BLOOM_STORAGE_DIR", BLOOM_DIR.toString());
        properties.put("BLOOM_PUBLIC_CAPACITY", "100000");
        properties.put("BLOOM_TENANT_DEFAULT_CAPACITY", "10000");
        properties.put("REDIS_HOST", REDIS.getRedisHost());
        properties.put("REDIS_PORT", REDIS.getRedisPort());
        // 用環境變數名而非 ctip.* :後者會被 application.yml 蓋掉(同 OVERRIDES 的理由)
        properties.put("RATE_LIMIT_BACKEND", "redis");
        return properties;
    }

    /**
     * 每個測試方法都從空的 Redis 開始。桶存在 Redis 而兩個實例共用同一個 client IP
     * (loopback),不清就會把上一個方法用掉的配額帶進來——這正是本測試要證明的行為,
     * 因此不能靠「換一個 IP」迴避,只能真的清掉。
     */
    @BeforeEach
    void shrinkAnonymousQuota() throws Exception {
        REDIS.execInContainer("redis-cli", "FLUSHALL");
        planAdmin = new TestPlans(plans, subscriptions, idGenerator, clock);
        originalAnonymous = planAdmin.plan(PlanCode.ANONYMOUS);
        planAdmin.save(TestPlans.requestsPerMinute(PER_MINUTE).apply(originalAnonymous));
    }

    @AfterEach
    void restoreAnonymousQuota() {
        planAdmin.save(originalAnonymous);
    }

    /**
     * M2-09 的判準句:「單實例耗盡後另一實例也被拒」。
     * 兩個實例都在 loopback 上,client IP 因此是同一個——正是維度 4 的鍵。
     */
    @Test
    void quotaConsumedOnOneInstanceIsAlreadyGoneOnTheOther() throws Exception {
        for (int i = 0; i < PER_MINUTE; i++) {
            HttpResponse<String> response = get(firstPort);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(header(response, "X-RateLimit-Limit")).isEqualTo(String.valueOf(PER_MINUTE));
            assertThat(header(response, "X-RateLimit-Remaining")).isEqualTo(String.valueOf(PER_MINUTE - 1 - i));
        }

        HttpResponse<String> onSecond = get(secondPort);

        assertThat(onSecond.statusCode()).isEqualTo(429);
        assertThat(onSecond.body()).contains("\"code\":\"RATE_LIMIT_EXCEEDED\"");
        assertThat(header(onSecond, "Retry-After")).isNotNull();
    }

    /** 餘額是共用的計數,不是各算各的:兩個實例交替打,剩餘量必須連續遞減。 */
    @Test
    void remainingCountsDownAcrossBothInstances() throws Exception {
        assertThat(header(get(firstPort), "X-RateLimit-Remaining")).isEqualTo("2");
        assertThat(header(get(secondPort), "X-RateLimit-Remaining")).isEqualTo("1");
        assertThat(header(get(firstPort), "X-RateLimit-Remaining")).isEqualTo("0");
    }

    /**
     * 分散式快取:方案在實例 1 改動後,實例 2 的下一個請求就必須用新的限額。
     * 行程內快取在這裡會失敗——實例 2 會一直用舊值直到自己的 TTL 到期。
     */
    @Test
    void planChangeOnOneInstanceIsVisibleOnTheOther() throws Exception {
        assertThat(header(get(secondPort), "X-RateLimit-Limit")).isEqualTo(String.valueOf(PER_MINUTE));

        planAdmin.save(TestPlans.requestsPerMinute(PER_MINUTE + 5).apply(originalAnonymous));

        assertThat(header(get(secondPort), "X-RateLimit-Limit")).isEqualTo(String.valueOf(PER_MINUTE + 5));
    }

    private HttpResponse<String> get(int port) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/health"))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String header(HttpResponse<String> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }
}
