package com.ctip.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.indicator.IocValue;
import com.ctip.domain.indicator.ValidityPeriod;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.IocHashType;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 值物件驗證(docs/spec/02-ddd-model.md §2.6)。 */
@Tag("unit")
class SharedValueObjectTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void cursorRequiresBothComponents() {
        assertThat(new Cursor(T0, new UUID(0, 1))).isNotNull();
        assertThatThrownBy(() -> new Cursor(null, new UUID(0, 1))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Cursor(T0, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void cursorEncodesAndDecodesRoundTrip() {
        Cursor cursor = new Cursor(T0, new UUID(1, 2));
        Cursor decoded = Cursor.decode(cursor.encode());
        assertThat(decoded).isEqualTo(cursor);
        assertThatThrownBy(() -> Cursor.decode("not-a-cursor")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Cursor.decode(":missing")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Cursor.decode("1234:00000000-0000-0000-0000-000000000001"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cursorPreservesSubMillisecondPrecision() {
        // last_seen 為 TIMESTAMPTZ(微秒);內部編碼截到毫秒會使 keyset 漏掉頁界後同毫秒的資料列
        Cursor cursor = new Cursor(T0.plusNanos(123_456_789), new UUID(3, 4));
        Cursor decoded = Cursor.decode(cursor.encode());
        assertThat(decoded.lastSeen()).isEqualTo(T0.plusNanos(123_456_789));
        assertThat(decoded).isEqualTo(cursor);
    }

    @Test
    void cursorPageDefensivelyCopiesItems() {
        CursorPage<String> page = new CursorPage<>(List.of("a"), "next", true);
        assertThat(page.items()).containsExactly("a");
        assertThat(CursorPage.lastPage(List.<String>of()).hasMore()).isFalse();
        assertThatThrownBy(() -> new CursorPage<>(null, null, false)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void visibilityFactoriesEncodeTlpVisibilityTable() {
        Visibility anonymous = Visibility.anonymous();
        assertThat(anonymous.viewerTenantId()).isEqualTo(TenantId.PUBLIC);
        assertThat(anonymous.maxPublicTlp()).isEqualTo(Tlp.CLEAR);

        TenantId demo = new TenantId(new UUID(0, 1));
        Visibility authenticated = Visibility.authenticated(demo);
        assertThat(authenticated.viewerTenantId()).isEqualTo(demo);
        assertThat(authenticated.maxPublicTlp()).isEqualTo(Tlp.GREEN);
    }

    @Test
    void validityPeriodRejectsUntilNotAfterFrom() {
        assertThat(new ValidityPeriod(T0, null).isExpiredAt(T0.plusSeconds(1))).isFalse();
        assertThat(new ValidityPeriod(T0, T0.plusSeconds(60)).isExpiredAt(T0.plusSeconds(120)))
                .isTrue();
        assertThatThrownBy(() -> new ValidityPeriod(T0, T0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void iocValueEnforcesHashTypeIffFileHash() {
        assertThat(new IocValue(IocType.FILE_HASH, IocHashType.SHA256, "ABC", "abc"))
                .isNotNull();
        assertThatThrownBy(() -> new IocValue(IocType.DOMAIN, IocHashType.MD5, "a.b", "a.b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IocValue(IocType.FILE_HASH, null, "abc", "abc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IocValue(IocType.DOMAIN, null, "a.b", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IocValue(IocType.URL, null, "x".repeat(2049), "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
