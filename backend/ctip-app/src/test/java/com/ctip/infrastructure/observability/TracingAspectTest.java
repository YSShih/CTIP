package com.ctip.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.probe.ProbeService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aspectj.lang.annotation.Around;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

/**
 * 追蹤鏈的切面(docs/spec/13-platform-ops.md §13.6)。
 *
 * <p>行為以 {@link ProbeService}(位於 {@code com.ctip.application..} 下的測試類別)驗證;
 * Redis / Kafka / Elasticsearch 三段在 mvp profile 下沒有後端可連,
 * 因此以**切入點清單**驗證它們沒有在重構中被漏掉——那是這條規格最容易靜默失守的地方。
 */
@Tag("unit")
class TracingAspectTest {

    private final List<String> observed = new ArrayList<>();
    private final ObservationRegistry registry = registryRecordingNames();

    @Test
    void anApplicationServiceCallCreatesASpan() {
        ProbeService probe = proxied(new ProbeService());

        assertThat(probe.work()).isEqualTo("done");
        assertThat(observed).containsExactly("ctip.service");
    }

    /** 例外一樣要留下 span(否則失敗的呼叫在追蹤上完全看不到)。 */
    @Test
    void aFailingCallStillCreatesASpan() {
        ProbeService probe = proxied(new ProbeService());

        assertThat(catchThrowable(() -> probe.explode())).isInstanceOf(IllegalStateException.class);
        assertThat(observed).containsExactly("ctip.service");
    }

    @Test
    void everyLayerOfTheChainHasAPointcut() {
        List<String> pointcuts = pointcuts();

        assertThat(pointcuts).hasSize(5);
        assertThat(String.join("\n", pointcuts))
                .contains("com.ctip.application..*Service")
                .contains("com.ctip.infrastructure.persistence..*Adapter")
                .contains("com.ctip.infrastructure.redis..")
                .contains("com.ctip.infrastructure.elasticsearch..*Adapter")
                .contains("com.ctip.infrastructure.kafka.KafkaEventForwarder")
                .contains("com.ctip.infrastructure.kafka.NotificationEventConsumer");
    }

    /**
     * 切入點刻意不是整個套件:套件內的 {@code final} 類別被切到時 CGLIB 建不出代理,
     * 整個 context 起不來(ADR 0032 §8)。
     */
    @Test
    void thePointcutsDoNotCoverWholePackages() {
        assertThat(pointcuts())
                .noneMatch(expression -> expression.contains("com.ctip.infrastructure.elasticsearch..*.*("))
                .noneMatch(expression -> expression.contains("com.ctip.infrastructure.kafka..*.*("));
    }

    private static List<String> pointcuts() {
        return Arrays.stream(TracingAspect.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Around.class))
                .filter(around -> around != null)
                .map(Around::value)
                .toList();
    }

    private ProbeService proxied(ProbeService target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new TracingAspect(registry));
        return factory.getProxy();
    }

    private static Throwable catchThrowable(Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private ObservationRegistry registryRecordingNames() {
        ObservationRegistry created = ObservationRegistry.create();
        created.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public void onStart(Observation.Context context) {
                observed.add(context.getName());
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });
        return created;
    }
}
