package com.ctip.infrastructure.persistence;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * 註冊 PostgreSQL 專用的 HQL 函式(經 META-INF/services 載入)。
 * ctip_tags_contain_all:text[] 欄位的 `@>` 包含判斷(吃 ix_indicators_tags GIN 索引)。
 * 需要顯式 cast 的原因:Hibernate 將 String[] 參數綁為 varchar[],而 PostgreSQL 的
 * anyarray 運算子不對 varchar[] → text[] 做隱式統一,`text[] @> varchar[]` 直接報錯;
 * 顯式 cast 為 text[] 後運算子解析與 GIN 皆正常。
 */
public final class PostgresFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        functionContributions
                .getFunctionRegistry()
                .registerPattern(
                        "ctip_tags_contain_all",
                        "(?1 @> cast(?2 as text[]))",
                        functionContributions
                                .getTypeConfiguration()
                                .getBasicTypeRegistry()
                                .resolve(StandardBasicTypes.BOOLEAN));
    }
}
