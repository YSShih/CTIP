package com.ctip.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * §5.5 對稱性:`application.yml` 用到的每一個環境變數,都必須宣告於 compose 的 backend
 * 環境變數區塊,並列於 `05 §5.4` 的變數清單。
 *
 * <p>這條規則規格寫了兩次(§5.4.5 註腳自陳「第三項缺陷」、`application.yml` 兩處註解引用它來
 * 拒絕開放新變數),仍然在 Phase 6/8/9 復發——當時有 <strong>11 個</strong>變數兩處皆未宣告。
 * 純靠人工比對顯然守不住,故以測試強制(ADR 0016)。
 *
 * <p>不對稱的後果不是理論性的:compose 的 backend 環境變數是<strong>明列白名單</strong>,
 * 沒有 {@code env_file}。未列進 compose 的變數,使用者寫進 {@code .env} 也到不了容器
 * ——設定看似可調,實際完全無效。
 *
 * <p>{@code .env.*.example} 樣板只列該環境需要覆寫的項目,有 compose 預設值者不強制出現,
 * 故不在本測試的檢查範圍。
 */
@Tag("unit")
class ConfigSymmetryTest {

    /** {@code ${VAR}} / {@code ${VAR:default}}。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)[:}]");

    /** 由 Spring profile 機制而非 compose 環境變數提供。 */
    private static final Set<String> NOT_A_CONTAINER_VARIABLE = Set.of("SPRING_PROFILES_ACTIVE");

    private static Path repoRoot() {
        return Path.of("").toAbsolutePath().resolve("../..").normalize();
    }

    private static Set<String> applicationYmlVariables() throws IOException {
        String yml = Files.readString(repoRoot().resolve("backend/ctip-app/src/main/resources/application.yml"));
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(yml);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!NOT_A_CONTAINER_VARIABLE.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * compose 對「有 autoconfig 綁在上面」的變數不得給空字串預設值。
     *
     * <p>{@code ELASTICSEARCH_URL} 曾是 {@code ${ELASTICSEARCH_URL:-}}——Boot 的 ES autoconfig 對空
     * {@code uris} 直接丟 {@code hosts must not be null nor empty},<strong>應用完全無法啟動</strong>,
     * 即使 {@code SEARCH_BACKEND=postgres}、一個 ES bean 都沒建立(autoconfig 是 Boot 自己的,
     * 不受本專案的條件裝配影響)。這一條在 mvp 的 backend 上是 crash-loop,由 DoD M2-01 抓到。
     *
     * <p>{@code KAFKA_BOOTSTRAP_SERVERS}(Phase 20)是同一個型態:Boot 的 Kafka autoconfig 對空
     * {@code bootstrap-servers} 丟 {@code ConfigException},即使 {@code NOTIFICATION_TRANSPORT=in-process}
     * 也一樣。此處預先納入,不必等到再壞一次 mvp 才發現。
     *
     * <p>守衛放在這裡而不是 {@code StartupValidator}:autoconfig 在 context refresh 期間就失敗了,
     * 任何 bean 形式的檢查都來不及執行——那會是一條永遠不會觸發的規則(執行規則 16)。
     */
    @Test
    void variablesBoundToAutoConfigurationHaveNonEmptyComposeDefaults() throws IOException {
        String compose = Files.readString(repoRoot().resolve("environment/docker-compose.yml"));
        for (String name : List.of("ELASTICSEARCH_URL", "KAFKA_BOOTSTRAP_SERVERS")) {
            Matcher matcher = Pattern.compile("\\$\\{" + name + "(:-([^}]*))?}").matcher(compose);
            assertThat(matcher.find()).as("compose 必須宣告 %s", name).isTrue();
            assertThat(matcher.group(2))
                    .as("%s 的 compose 預設值不得為空——Boot 的 autoconfig 會因此使應用無法啟動", name)
                    .isNotBlank();
        }
    }

    @Test
    void everyApplicationYmlVariableReachesTheContainer() throws IOException {
        String compose = Files.readString(repoRoot().resolve("environment/docker-compose.yml"));
        List<String> missing = applicationYmlVariables().stream()
                .filter(name -> !compose.contains(name))
                .toList();
        assertThat(missing)
                .as("docker-compose.yml 缺少 application.yml 使用中的變數宣告(§5.5 對稱性)")
                .isEmpty();
    }

    /** 每個變數都必須出現在 `05 §5.4` 的某一個小節(5.4.1–5.4.6 依用途分節)。 */
    @Test
    void everyApplicationYmlVariableIsCataloguedInTheSpec() throws IOException {
        String spec = Files.readString(repoRoot().resolve("docs/spec/05-environment.md"));
        String section = spec.substring(spec.indexOf("### 5.4.1"), spec.indexOf("## 5.5"));
        List<String> missing = applicationYmlVariables().stream()
                .filter(name -> !section.contains(name))
                .toList();
        assertThat(missing).as("05 §5.4 的變數清單缺少 application.yml 使用中的變數").isEmpty();
    }
}
