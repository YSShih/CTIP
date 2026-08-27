package com.ctip.application.port;

import com.ctip.domain.user.RefreshToken;
import com.ctip.domain.user.RefreshTokenId;
import com.ctip.domain.user.TokenFamilyId;
import com.ctip.domain.user.TokenHash;
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

    void saveAll(List<RefreshToken> tokens);

    RefreshToken save(RefreshToken token);
}
