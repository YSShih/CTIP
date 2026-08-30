package com.ctip.application.port;

import com.ctip.domain.user.RefreshToken;
import com.ctip.domain.user.RefreshTokenId;
import com.ctip.domain.user.TokenFamilyId;
import com.ctip.domain.user.TokenHash;
import com.ctip.domain.user.UserId;
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
     * 刪除該使用者的全部 refresh token。
     *
     * <p>供資料主體刪除使用({@code DELETE /api/v1/admin/data-subjects/{userId}};13 §13.4):
     * 表 15 的 {@code ip} 與 {@code user_agent} 是個資,撤銷只是讓它們失效,列還在。
     *
     * @return 刪除的列數
     */
    int deleteByUser(UserId userId);
}
