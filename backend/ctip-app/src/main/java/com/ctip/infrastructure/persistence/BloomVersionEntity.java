package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** bloom_versions(docs/spec/04-data-dictionary.md 表 22)。寫入後不再變更(append-only)。 */
@Entity
@Table(name = "bloom_versions")
class BloomVersionEntity {

    @Id
    UUID id;

    @Column(nullable = false, length = 16)
    String scope;

    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    @Column(name = "dataset_version", nullable = false)
    long datasetVersion;

    @Column(name = "bloom_version", nullable = false)
    long bloomVersion;

    @Column(name = "fingerprint_algorithm", nullable = false, length = 16)
    String fingerprintAlgorithm;

    @Column(name = "hash_function_count", nullable = false)
    short hashFunctionCount;

    @Column(name = "bit_size", nullable = false)
    long bitSize;

    @Column(nullable = false)
    long capacity;

    @Column(name = "false_positive_rate", nullable = false)
    double falsePositiveRate;

    @Column(name = "member_count", nullable = false)
    long memberCount;

    @Column(name = "is_full_snapshot", nullable = false)
    boolean fullSnapshot;

    @Column(name = "base_bloom_version")
    Long baseBloomVersion;

    @Column(name = "generated_at", nullable = false)
    Instant generatedAt;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
