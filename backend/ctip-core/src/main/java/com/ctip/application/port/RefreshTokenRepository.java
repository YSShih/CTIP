package com.ctip.application.port;

import com.ctip.domain.user.RefreshToken;
import com.ctip.domain.user.RefreshTokenId;
import com.ctip.domain.user.RevokedReason;
import com.ctip.domain.user.TokenFamilyId;
import com.ctip.domain.user.TokenHash;
import com.ctip.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * RefreshToken 是 User 聚合的內部實體,但認證熱路徑是「以雜湊定位單一枚」,
 * 隨 User 全量載入不可行,故獨立為 out-port(ADR 0012 決策 4)。
 */
public interface RefreshTokenRepository {

    Optional<RefreshToken> findByHash(TokenHash hash);

    Optional<RefreshToken> findById(RefreshTokenId id);

    List<RefreshToken> findByFamily(TokenFamilyId familyId);

    /**
     * 該使用者尚未撤銷的全部 token(跨 family)。
     *
     * <p>用於改密碼時的全撤(ADR 0015):以 family 為單位的 {@link #findByFamily} 做不到
     * ——使用者可能同時有多個裝置,每個裝置一個 family。
     */
    List<RefreshToken> findActiveByUser(UserId userId);

    void saveAll(List<RefreshToken> tokens);

    RefreshToken save(RefreshToken token);

    /**
     * 把已過期但尚未撤銷的 token 標為已撤銷,一次最多 {@code batchSize} 列,回傳實際筆數。
     *
     * <p>過期 token 清理(08 §8.7 的 {@code TOKEN_CLEANUP_CRON};表 15 的
     * {@code revoked_reason} 早就為它保留了 {@code EXPIRED_CLEANUP},索引也叫 {@code ix_rt_gc})。
     * 列本身留著——{@code ip} 與 {@code user_agent} 屬個資,由資料主體刪除負責移除,
     * 不是這個任務的職責。
     *
     * <p><strong>為什麼是批次 UPDATE 而不是載入聚合再改</strong>:這是跨全體使用者的清掃,
     * 而本 port 存在的理由正是「隨 User 全量載入不可行」。
     * 領域規則「已撤銷不可清除,重複撤銷保留最初原因」由述詞 {@code revoked_at IS NULL}
     * 保證——這一點與 {@link com.ctip.domain.user.RefreshToken} 的 revoke 行為必須維持一致。
     */
    int revokeExpired(Instant now, RevokedReason reason, int batchSize);

    /**
     * 刪除該使用者的全部 refresh token。
     *
     * <p>供資料主體刪除使用({@code DELETE /api/v1/admin/data-subjects/{userId}};13 §13.4):
     * 表 15 的 {@code ip} 與 {@code user_agent} 是個資,撤銷只是讓它們失效,列還在。
     *
     * @return 刪除的列數
     */
    int deleteByUser(UserId userId);
}
