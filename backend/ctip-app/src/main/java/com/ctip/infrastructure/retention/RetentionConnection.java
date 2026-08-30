package com.ctip.infrastructure.retention;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 保留清理專用的資料庫連線({@code ctip_retention};docs/spec/13-platform-ops.md §13.5 規則 2)。
 *
 * <p><strong>不是 {@code DataSource} 型別的 bean</strong>:Boot 的 {@code DataSourceAutoConfiguration}
 * 帶 {@code @ConditionalOnMissingBean(DataSource.class)},多宣告一個 DataSource bean 會讓
 * <em>主</em>資料源整個不建立——整個應用起不來。包一層是為了避開那個條件,不是為了抽象。
 *
 * <p>池子只開兩條:清理是每天幾次的背景任務,借走的每一條連線都是業務請求拿不到的。
 */
public class RetentionConnection implements AutoCloseable {

    private static final int POOL_SIZE = 2;

    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbc;

    public RetentionConnection(String url, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(POOL_SIZE);
        config.setPoolName("ctip-retention");
        this.dataSource = new HikariDataSource(config);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
