package com.ctip.application.port;

import com.ctip.application.ingestion.RejectedRecord;

/** ingestion_rejections 的寫入 port(兩模型表,無 domain model;append-only)。 */
public interface RejectionLogPort {

    void record(RejectedRecord rejected);
}
