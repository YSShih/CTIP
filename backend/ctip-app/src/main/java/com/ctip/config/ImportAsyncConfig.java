package com.ctip.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 匯入的背景執行緒池(docs/spec/09-api.md §9.7:{@code POST /iocs/import} 回 202 後在背景處理)。
 *
 * <p>刻意<strong>有界</strong>:佇列滿了就由呼叫端執行緒自己跑({@code CallerRunsPolicy}),
 * 匯入因此退化成同步、慢下來,但不會被無限堆積的 job 拖垮——無界佇列在這裡等於
 * 「用記憶體換一個看不見的排隊」,而每個 job 還帶著整份已解碼的記錄清單。
 *
 * <p>M1–M3 皆為單一實例(§8.7 不引入 Quartz),因此不需要分散式佇列;
 * 代價是實例重啟時 RUNNING 的 job 會停在該狀態,{@code finished_at} 為 null——
 * 與 {@code source_sync} 的 RUNNING 列同一個語意。
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class ImportAsyncConfig {

    @Bean("importTaskExecutor")
    Executor importTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ctip-import-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return new TaskExecutorAdapter(executor.getThreadPoolExecutor());
    }
}
