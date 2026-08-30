package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 實際 schema ↔ 規格的三方比對:{@code pg_tables}、
 * {@code docs/spec/04-data-dictionary.md} §4.1 的表清單、{@code docs/spec/03-diagrams.md} §3.3 的 ERD。
 *
 * <p><strong>本測試存在的理由</strong>(2026-08-30 全專案複查;
 * {@code docs/architecture/decisions/0045-full-project-review-doc-sync.md}):
 * §3.3 的 ERD 標為「🔴 規範·自動驗證」,內文寫「由 CI 比對 Flyway migration 產生的實際 schema 與 04」
 * ——但那個比對<strong>從來不存在</strong>。後果是 Phase 14 新增的 {@code import_jobs}(表 18b)
 * 只進了 §4.3 的欄位定義與 §4.7 的 migration 對應,§4.1 的表清單、§3.3 的 ERD 與兩處檔尾計數
 * 全部漏了同步,而且連續九個 phase 沒有任何檢查變紅。這與 ADR 0016 第 3 項
 * ({@code 15 §15.5} 寫了「必須實作」卻沒人實作)是同一類缺口:規格宣告了自動化,自動化卻不存在。
 *
 * <p>比對採<strong>雙向</strong>:少一張(規格漏登)與多一張(建了表沒寫規格)都要紅。
 * 這是 {@link MigrationIntegrationTest} 的 {@code contains} 斷言做不到的——那些只驗「有沒有」,
 * 驗不到「規格是不是也知道」。
 */
class DataDictionaryConsistencyTest extends AbstractPostgresIntegrationTest {

    /** Flyway 自己的簿記表,不屬於 04 的資料字典。 */
    private static final Set<String> NOT_IN_DICTIONARY = Set.of("flyway_schema_history");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void dataDictionaryTableListMatchesActualSchema() {
        assertThat(specificationTables())
                .as("§4.1 的表清單必須與實際 schema 完全一致(雙向);新增表時要一併補進 §4.1、§4.7 與 §3.3 的 ERD")
                .containsExactlyInAnyOrderElementsOf(actualTables());
    }

    @Test
    void erdCoversEveryTable() {
        assertThat(erdEntities())
                .as("§3.3 的 ERD 必須畫入每一張表;該圖標為「規範·自動驗證」,本測試就是那個驗證")
                .containsExactlyInAnyOrderElementsOf(actualTables());
    }

    /** §4.1 的檔尾計數是人工維護的,順手一起釘住。 */
    @Test
    void declaredTableCountMatchesActualSchema() {
        String text = readSpecification("04-data-dictionary.md");
        Matcher declared = Pattern.compile("表數：(\\d+)").matcher(text);
        assertThat(declared.find()).as("04 的檔尾必須留著「表數：n」,那是唯一的人工計數").isTrue();
        assertThat(Integer.parseInt(declared.group(1)))
                .as("04 檔尾宣告的表數必須等於實際 schema 的表數")
                .isEqualTo(actualTables().size());
    }

    private List<String> actualTables() {
        return jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class).stream()
                .filter(table -> !NOT_IN_DICTIONARY.contains(table))
                .toList();
    }

    /**
     * 解析 §4.1 的表清單。列格式為 {@code | <編號> | `<表名>` | <里程碑> | … }——
     * 編號可帶字尾(例如 {@code 18b}),因為 ADR 0019 為了不動既有表號而以字尾插入 {@code import_jobs}。
     */
    private static Set<String> specificationTables() {
        String section = section(readSpecification("04-data-dictionary.md"), "## 4.1", "## 4.2");
        Matcher row = Pattern.compile("^\\|\\s*\\d+[a-z]?\\s*\\|\\s*`([a-z_]+)`\\s*\\|", Pattern.MULTILINE)
                .matcher(section);
        Set<String> tables = new LinkedHashSet<>();
        while (row.find()) {
            tables.add(row.group(1));
        }
        if (tables.isEmpty()) {
            throw new IllegalStateException("§4.1 的表清單解析不出任何一列——表格格式是否被改過?");
        }
        return tables;
    }

    /** 解析 §3.3 的 mermaid ERD;實體名一律大寫,關聯兩側都算。 */
    private static Set<String> erdEntities() {
        String section = section(readSpecification("03-diagrams.md"), "## 3.3", "## 3.4");
        // mermaid ER 的關聯是 <左基數><線><右基數>,基數為 ||／o{／}o／|{／}|／o| 六種,線為 -- 或 ..
        Matcher relation = Pattern.compile(
                        "^\\s*([A-Z_]+)\\s+[|o{}]{2}[.-]{2}[|o{}]{2}\\s+([A-Z_]+)\\s*:", Pattern.MULTILINE)
                .matcher(section);
        Set<String> entities = new LinkedHashSet<>();
        while (relation.find()) {
            entities.add(relation.group(1).toLowerCase());
            entities.add(relation.group(2).toLowerCase());
        }
        if (entities.isEmpty()) {
            throw new IllegalStateException("§3.3 的 ERD 解析不出任何關聯——mermaid 格式是否被改過?");
        }
        return entities;
    }

    private static String section(String text, String from, String to) {
        int start = text.indexOf(from);
        int end = text.indexOf(to, start);
        if (start < 0 || end < 0) {
            throw new IllegalStateException("找不到章節 " + from + " … " + to + "——標題是否被改過?");
        }
        return text.substring(start, end);
    }

    private static String readSpecification(String fileName) {
        Path spec = Path.of("")
                .toAbsolutePath()
                .resolve("../../docs/spec/" + fileName)
                .normalize();
        try {
            return Files.readString(spec);
        } catch (IOException e) {
            throw new IllegalStateException("讀不到規格 " + spec, e);
        }
    }
}
