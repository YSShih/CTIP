package com.ctip.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/** 11 條 ArchUnit 規則(docs/spec/01-architecture.md §1.9),跨模組掃描。 */
@Tag("unit")
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.ctip");
    }

    @Test
    void rule1DomainMustNotDependOnFrameworks() {
        noClasses()
                .that()
                .resideInAPackage("com.ctip.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        // Boot 4 的 Jackson 是 3.x,套件是 tools.jackson..(06 §6.3.6 第 6 條)。
                        // 只擋 com.fasterxml 等於這條防線是空的——IDE 自動 import 會直接放行
                        "com.fasterxml.jackson..",
                        "tools.jackson..",
                        "org.apache.kafka..",
                        "io.lettuce..",
                        "redis.clients..",
                        "org.elasticsearch..",
                        "co.elastic.clients..")
                .check(classes);
    }

    @Test
    void rule2SdkMustNotDependOnSpringOrJpa() {
        noClasses()
                .that()
                .resideInAPackage("com.ctip.sdk..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.persistence..")
                .check(classes);
    }

    @Test
    void rule3InterfacesMustNotImportPersistence() {
        noClasses()
                .that()
                .resideInAPackage("com.ctip.interfaces..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.ctip.infrastructure.persistence..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void rule4RestMustNotDependOnRepositoryPortsDirectly() {
        noClasses()
                .that()
                .resideInAPackage("..interfaces.rest..")
                .should()
                .dependOnClassesThat(new DescribedPredicate<>("application.port 的 Repository") {
                    @Override
                    public boolean test(JavaClass input) {
                        return input.getPackageName().startsWith("com.ctip.application.port")
                                && input.getSimpleName().endsWith("Repository");
                    }
                })
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void rule5NoPackageCycles() {
        SlicesRuleDefinition.slices()
                .matching("com.ctip.(*)..")
                .should()
                .beFreeOfCycles()
                .check(classes);
    }

    @Test
    void rule6NoAutowiredFields() {
        noFields()
                .that()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(Service.class)
                .or()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(Component.class)
                .or()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(Repository.class)
                .or()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(RestController.class)
                .should()
                .beAnnotatedWith(Autowired.class)
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void rule7RestDtosMustBeRecords() {
        classes()
                .that()
                .resideInAPackage("..interfaces.rest.dto..")
                .should()
                .beRecords()
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void rule8ApplicationMustNotUseSpringDataDomain() {
        noClasses()
                .that()
                .resideInAPackage("com.ctip.application..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework.data.domain..")
                .check(classes);
    }

    @Test
    void rule9DomainMustNotCallNowOrRandomUuid() {
        noClasses()
                .that()
                .resideInAPackage("com.ctip.domain..")
                .should()
                .callMethodWhere(target(owner(JavaClass.Predicates.belongToAnyOf(Instant.class, LocalDate.class))
                                .and(name("now")))
                        .or(target(owner(JavaClass.Predicates.belongToAnyOf(UUID.class))
                                .and(name("randomUUID")))))
                .check(classes);
    }

    /**
     * 規則 11:基礎設施 client 的型別不得洩漏進 application 層
     * (phase-17:「不得讓 {@code CachePort} 洩漏 Lettuce/Redis 型別到 application 層」;
     * phase-19:「不得讓 {@code ElasticsearchSearchAdapter} 的型別洩漏到 {@code application} 層」)。
     *
     * <p>規則 1 已擋住 domain 對這些套件的依賴,但 <strong>port 定義在 application 層</strong>
     * ——真正會發生洩漏的地方是那裡:一個回傳 {@code RedisFuture} 或收 {@code SearchRequest}
     * 的 port 簽章,會讓 06 §6.5 要求的「Redis → Valkey、Elasticsearch → OpenSearch 只需改
     * infrastructure」變成不可能。Bucket4j 與 Resilience4j 一併擋:限流與降級的實作細節
     * 同樣不該出現在 port 上(§13.7 的 circuit breaker 屬於 {@code FallbackSearchAdapter})。
     */
    @Test
    void rule11ApplicationMustNotDependOnInfrastructureClientInternals() {
        noClasses()
                .that()
                .resideInAPackage("com.ctip.application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "io.lettuce..",
                        "redis.clients..",
                        "org.springframework.data.redis..",
                        "io.github.bucket4j..",
                        "co.elastic.clients..",
                        "org.elasticsearch..",
                        "org.springframework.data.elasticsearch..",
                        "io.github.resilience4j..",
                        "org.apache.kafka..",
                        "org.springframework.kafka..")
                .check(classes);
    }

    /**
     * 規則 10:Ubiquitous Language 詞彙表(02 §2.1)「常見誤用」欄的命名不得出現在類別名。
     *
     * <p>{@code 15 §15.5} 對人工項 P-02 明文要求「**可自動化部分必須實作**(列為 ArchUnit
     * 規則的擴充)」——這條規則自 M1 就該存在,一直沒有實作,也沒有任何 DoD 項目檢查它(ADR 0016)。
     * 語意層的詞彙遵守仍屬人工項;這裡只擋詞彙表已明文列為禁止的具體類別名。
     */
    @Test
    void rule10ClassNamesMustNotUseForbiddenVocabulary() {
        for (String forbidden : List.of(
                "Ioc",
                "IocEntity",
                "Observable",
                "SourceRecord",
                "Observation",
                "Feed",
                "Provider",
                "Vendor",
                "HashType",
                "HashAlgorithm",
                "Organization",
                "Account",
                "Workspace",
                "Tier",
                "Classification")) {
            noClasses()
                    .that()
                    .resideInAnyPackage("com.ctip.domain..", "com.ctip.sdk..")
                    .should()
                    .haveSimpleName(forbidden)
                    .because("02 §2.1 詞彙表把 " + forbidden + " 列為禁止的命名")
                    .check(classes);
        }
    }
}
