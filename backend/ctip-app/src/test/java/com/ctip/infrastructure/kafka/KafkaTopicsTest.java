package com.ctip.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.event.ApiKeyEvents;
import com.ctip.domain.event.BloomEvents;
import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.event.IndicatorEvents;
import com.ctip.domain.event.IngestionEvents;
import com.ctip.domain.event.SourceEvents;
import com.ctip.domain.event.SubscriptionEvents;
import com.ctip.domain.event.TenantEvents;
import com.ctip.domain.event.ThreatEvents;
import com.ctip.domain.event.UserEvents;
import com.ctip.domain.event.WebhookEvents;
import com.ctip.domain.tenant.TenantId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 六個 topic 的命名與 domain event 對照表(docs/spec/13-platform-ops.md §13.1)。
 *
 * <p>§13.1 明文「domain event → topic 對應表<strong>必須</strong>寫入
 * {@code docs/api/events/README.md}」。對照表因此有三份來源:規格 §2.4 的事件清單、
 * {@code com.ctip.domain.event} 的實際型別、以及那份文件——三者任一漂移都必須轉紅
 * (與 {@code RbacMatrixTest} 對 §10.3 的作法相同)。
 */
@Tag("unit")
class KafkaTopicsTest {

    /** §2.4 事件清單的列格式:{@code | `EventName` | 發佈者 | Phase | 消費者 |}。 */
    private static final Pattern SPEC_ROW =
            Pattern.compile("^\\|\\s*`([A-Za-z]+)`(?:\\s*/\\s*`([A-Za-z]+)`)?\\s*\\|", Pattern.MULTILINE);

    @Test
    void theSixTopicNamesFollowTheSpecifiedFormat() {
        assertThat(KafkaTopics.ALL).hasSize(6);
        assertThat(KafkaTopics.ALL).allMatch(name -> name.matches("ctip\\.[a-z]+\\.[a-z]+\\.v\\d+"));
        assertThat(KafkaTopics.ALL)
                .containsExactly(
                        "ctip.threat.ingest.v1",
                        "ctip.threat.normalized.v1",
                        "ctip.indicator.updated.v1",
                        "ctip.audit.events.v1",
                        "ctip.system.alert.v1",
                        "ctip.notification.events.v1");
    }

    @Test
    void everyTopicIsDocumentedInTheEventsReadme() throws IOException {
        String readme = readme();
        assertThat(KafkaTopics.ALL.stream()
                        .filter(topic -> !readme.contains(topic))
                        .toList())
                .as("docs/api/events/README.md 缺少 topic 的說明")
                .isEmpty();
    }

    /** 規格 §2.4 列出的每一個事件,程式碼裡都必須有對應的型別。 */
    @Test
    void everyEventInTheSpecificationExistsInCode() throws IOException {
        assertThat(declaredEventNames()).containsAll(specifiedEventNames());
    }

    /** 程式碼裡的每一個事件都必須出現在對照表文件裡,否則消費端不知道去哪個 topic 訂閱。 */
    @Test
    void everyDomainEventIsMappedInTheEventsReadme() throws IOException {
        String readme = readme();
        assertThat(declaredEventNames().stream()
                        .filter(name -> !readme.contains(name))
                        .toList())
                .as("docs/api/events/README.md 的對照表缺少事件")
                .isEmpty();
    }

    /** 每一個事件都必須有指定的 topic,而且那個 topic 必須在六個之內。 */
    @Test
    void everyDomainEventHasATopic() {
        for (Class<?> event : eventClasses().toList()) {
            String topic = KafkaTopics.of(instantiate(event));
            assertThat(KafkaTopics.ALL).as("%s 的 topic", event.getSimpleName()).contains(topic);
        }
    }

    /** 未對應的事件型別必須是明確的失敗,不得靜默丟進某個預設 topic。 */
    @Test
    void anUnmappedEventTypeFailsLoudly() {
        DomainEvent unknown = () -> TenantId.PUBLIC;
        assertThatThrownBy(() -> KafkaTopics.of(unknown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docs/api/events/README.md");
    }

    private static Stream<Class<?>> eventClasses() {
        return Stream.of(
                        IndicatorEvents.class,
                        ThreatEvents.class,
                        IngestionEvents.class,
                        SourceEvents.class,
                        TenantEvents.class,
                        UserEvents.class,
                        ApiKeyEvents.class,
                        SubscriptionEvents.class,
                        BloomEvents.class,
                        WebhookEvents.class)
                .flatMap(holder -> Arrays.stream(holder.getDeclaredClasses()))
                .filter(DomainEvent.class::isAssignableFrom);
    }

    private static Set<String> declaredEventNames() {
        return eventClasses()
                .map(Class::getSimpleName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /** 只造出型別正確的空殼:{@link KafkaTopics#of} 只看型別,不看欄位值。 */
    private static DomainEvent instantiate(Class<?> event) {
        Object[] arguments = Arrays.stream(event.getRecordComponents())
                .map(component -> defaultValue(component.getType()))
                .toArray();
        try {
            var constructor = event.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return (DomainEvent) constructor.newInstance(arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("無法建立事件實例:" + event.getSimpleName(), e);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == TenantId.class) {
            return TenantId.PUBLIC;
        }
        return null;
    }

    private static Set<String> specifiedEventNames() throws IOException {
        String spec = Files.readString(repoRoot().resolve("docs/spec/02-ddd-model.md"));
        String section = spec.substring(spec.indexOf("## 2.4"), spec.indexOf("## 2.5"));
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = SPEC_ROW.matcher(section);
        while (matcher.find()) {
            names.add(matcher.group(1));
            if (matcher.group(2) != null) {
                names.add(matcher.group(2));
            }
        }
        assertThat(names).as("§2.4 的事件清單解析不出任何一列——表格格式是否被改過?").isNotEmpty();
        return names;
    }

    private static String readme() throws IOException {
        return Files.readString(repoRoot().resolve("docs/api/events/README.md"));
    }

    private static Path repoRoot() {
        return Path.of("").toAbsolutePath().resolve("../..").normalize();
    }
}
