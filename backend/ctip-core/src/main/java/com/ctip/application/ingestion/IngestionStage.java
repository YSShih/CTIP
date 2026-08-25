package com.ctip.application.ingestion;

/**
 * Pipeline stage(docs/spec/08-ingestion-sdk.md §8.2)。禁止以抽象基底類別 + 繼承實作;
 * 順序由 IngestionPipelineConfig 的顯式 List.of 決定,不依賴 @Order。
 */
public interface IngestionStage {

    String name();

    IngestionContext execute(IngestionContext context);
}
