package com.devMetrics.develop.scheduler;

import com.devMetrics.develop.entity.Repository;
import com.devMetrics.develop.repository.RepositoryRepository;
import com.devMetrics.develop.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SyncScheduler {

    private final RepositoryRepository repositoryRepository;
    private final SyncService syncService;

    // Runs every 6 hours — adjust as needed
    @Scheduled(cron = "0 0 */6 * * *")
    public void scheduledSync() {
        log.info("Scheduled sync started");

        List<Repository> repos = repositoryRepository.findAll();

        if (repos.isEmpty()) {
            log.info("No repos to sync");
            return;
        }

        repos.forEach(repo -> {
            try {
                syncService.syncRepo(repo);
            } catch (Exception e) {
                log.error("Scheduled sync failed for {}: {}",
                        repo.getFullName(), e.getMessage());
            }
        });

        log.info("Scheduled sync complete for {} repos", repos.size());
    }
}
