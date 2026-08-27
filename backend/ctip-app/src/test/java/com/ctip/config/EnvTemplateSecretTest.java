package com.ctip.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * `.env.*.example` 出貨的 JWT_SECRET 樣板值必須同時滿足兩件事:
 * <ul>
 *   <li>是明顯的假值(含 {@code CHANGE_ME}),使 prod 啟動守衛與 up.sh 都能擋下未設定真實 secret 的部署</li>
 *   <li>長度 ≥ 32 bytes——HS256 的金鑰下限。否則照 README 快速開始複製樣板的**全新環境會直接啟動失敗**</li>
 * </ul>
 *
 * <p>Phase 13 之前沒有任何東西消費 JWT_SECRET,樣板值 {@code CHANGE_ME_MIN_32_BYTES}(實際只有 22 bytes)
 * 因此一直沒被發現;JWT 上線後它使 mvp 環境無法啟動。本測試是該回歸的鎖。
 */
@Tag("unit")
class EnvTemplateSecretTest {

    private static final int HS256_MIN_BYTES = 32;
    private static final String FAKE_VALUE_MARKER = "CHANGE_ME";

    static Stream<Path> templates() throws IOException {
        Path environment =
                Path.of("").toAbsolutePath().resolve("../../environment").normalize();
        try (Stream<Path> files = Files.list(environment)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".example")).sorted().toList().stream();
        }
    }

    @ParameterizedTest
    @MethodSource("templates")
    void shippedJwtSecretIsAnObviousFakeThatStillSatisfiesHs256(Path template) throws IOException {
        String secret = valueOf(template);
        assertThat(secret).as("%s 必須宣告 JWT_SECRET", template.getFileName()).isNotNull();
        assertThat(secret).as("%s 的樣板值必須是明顯假值", template.getFileName()).contains(FAKE_VALUE_MARKER);
        assertThat(secret.getBytes(StandardCharsets.UTF_8).length)
                .as("%s 的樣板值長度必須 >= %d bytes(HS256 金鑰下限)", template.getFileName(), HS256_MIN_BYTES)
                .isGreaterThanOrEqualTo(HS256_MIN_BYTES);
    }

    private static String valueOf(Path template) throws IOException {
        return Files.readAllLines(template).stream()
                .filter(line -> line.startsWith("JWT_SECRET="))
                .map(line ->
                        line.substring("JWT_SECRET=".length()).split("\\s+#")[0].trim())
                .findFirst()
                .orElse(null);
    }
}
