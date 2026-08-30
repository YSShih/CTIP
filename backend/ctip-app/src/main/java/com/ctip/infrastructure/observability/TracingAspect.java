package com.ctip.infrastructure.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * 追蹤鏈(docs/spec/13-platform-ops.md §13.6):
 * {@code API request → application service → DB / Redis / Kafka / Elasticsearch}。
 *
 * <p>HTTP 那一段由 Boot 的 {@code ServerHttpObservationFilter} 產生;這裡補的是它下游的四段。
 * 用一個切面而不是在幾十個 adapter 各寫一次 {@code Observation}:追蹤是橫切關注,
 * 而 adapter 的建構子多半已經接近 checkstyle 的參數上限(Phase 21 的稽核也是同一個理由)。
 *
 * <p>切入點只點名 adapter 與 consumer,不是整個套件:套件內還有 {@code IndicatorSearchIndex}、
 * {@code KafkaTopics} 這類 {@code final} 類別,被切到時 CGLIB 直接建不出代理,
 * 整個 context 起不來(實測:{@code SearchFallbackTest} 的 ES context)。
 *
 * <p>span 名稱刻意是低基數的五個({@code ctip.service} / {@code ctip.db} / {@code ctip.redis} /
 * {@code ctip.search} / {@code ctip.kafka});類別與方法名放在 contextual name 與高基數欄位,
 * 因為 {@code Observation} 同時也會產生計時指標,低基數欄位會變成指標的 tag。
 */
@Aspect
public class TracingAspect {

    private final ObservationRegistry observations;

    public TracingAspect(ObservationRegistry observations) {
        this.observations = observations;
    }

    @Around("execution(public * com.ctip.application..*Service.*(..))")
    public Object traceApplicationService(ProceedingJoinPoint point) throws Throwable {
        return trace("ctip.service", point);
    }

    @Around("execution(public * com.ctip.infrastructure.persistence..*Adapter.*(..))")
    public Object traceDatabase(ProceedingJoinPoint point) throws Throwable {
        return trace("ctip.db", point);
    }

    @Around("execution(public * com.ctip.infrastructure.redis..*.*(..))")
    public Object traceRedis(ProceedingJoinPoint point) throws Throwable {
        return trace("ctip.redis", point);
    }

    @Around("execution(public * com.ctip.infrastructure.elasticsearch..*Adapter.*(..))")
    public Object traceElasticsearch(ProceedingJoinPoint point) throws Throwable {
        return trace("ctip.search", point);
    }

    @Around("execution(public * com.ctip.infrastructure.kafka.KafkaEventForwarder.*(..))"
            + " || execution(public * com.ctip.infrastructure.kafka.NotificationEventConsumer.*(..))")
    public Object traceKafka(ProceedingJoinPoint point) throws Throwable {
        return trace("ctip.kafka", point);
    }

    private Object trace(String name, ProceedingJoinPoint point) throws Throwable {
        String operation = operationOf(point);
        Observation.CheckedCallable<Object, Throwable> call = point::proceed;
        return Observation.createNotStarted(name, observations)
                .contextualName(operation)
                .highCardinalityKeyValue("operation", operation)
                .observeChecked(call);
    }

    private static String operationOf(ProceedingJoinPoint point) {
        return point.getSignature().getDeclaringType().getSimpleName() + "#"
                + point.getSignature().getName();
    }
}
