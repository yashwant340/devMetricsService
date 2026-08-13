package com.devMetrics.develop.service;

import com.devMetrics.develop.entity.Commit;
import com.devMetrics.develop.entity.MetricsSnapshot;
import com.devMetrics.develop.entity.PullRequest;
import com.devMetrics.develop.entity.Repository;
import com.devMetrics.develop.repository.CommitRepository;
import com.devMetrics.develop.repository.MetricsSnapshotRepository;
import com.devMetrics.develop.repository.PullRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MetricsComputationService {

    private final PullRequestRepository pullRequestRepository;
    private final CommitRepository commitRepository;
    private final MetricsSnapshotRepository metricsSnapshotRepository;

    public void computeAndSnapshot(Repository repo) {
        LocalDate today = LocalDate.now();
        MetricsSnapshot snapshot = metricsSnapshotRepository
                .findByRepoAndSnapshotDate(repo, today)
                .orElseGet(() -> MetricsSnapshot.builder()
                        .repo(repo)
                        .snapshotDate(today)
                        .build());

        List<PullRequest> prs = pullRequestRepository.findByRepo(repo);
        List<Commit> commits = commitRepository.findByRepo(repo);

        computePrMetrics(snapshot, prs);
        computeChurnMetrics(snapshot, commits);
        computeVelocityMetrics(snapshot, prs, commits);
        computeHealthScore(snapshot);

        metricsSnapshotRepository.save(snapshot);
        log.info("Metrics snapshot saved for {}", repo.getFullName());
    }

    private void computePrMetrics(MetricsSnapshot snapshot, List<PullRequest> prs) {
        long openCount = prs.stream().filter(p -> "open".equals(p.getState())).count();
        long mergedCount = prs.stream().filter(p -> "merged".equals(p.getState())).count();
        long closedCount = prs.stream().filter(p -> "closed".equals(p.getState())).count();

        snapshot.setOpenPrCount((int) openCount);
        snapshot.setMergedPrCount((int) mergedCount);
        snapshot.setClosedPrCount((int) closedCount);

        OptionalDouble avgMergeHours = prs.stream()
                .filter(p -> "merged".equals(p.getState()))
                .filter(p -> p.getOpenedAt() != null && p.getMergedAt() != null)
                .mapToLong(p -> Duration.between(p.getOpenedAt(), p.getMergedAt()).toMinutes())
                .average();
        snapshot.setAvgPrMergeHours(avgMergeHours.isPresent()
                ? round2(avgMergeHours.getAsDouble() / 60.0)
                : null);

        OptionalDouble avgReviewHours = prs.stream()
                .filter(p -> p.getOpenedAt() != null && p.getFirstReviewAt() != null)
                .mapToLong(p -> Duration.between(p.getOpenedAt(), p.getFirstReviewAt()).toMinutes())
                .average();
        snapshot.setAvgTimeToFirstReviewHours(avgReviewHours.isPresent()
                ? round2(avgReviewHours.getAsDouble() / 60.0)
                : null);
    }

    private void computeChurnMetrics(MetricsSnapshot snapshot, List<Commit> commits) {
        int totalAdded = commits.stream()
                .filter(c -> c.getAdditions() != null)
                .mapToInt(Commit::getAdditions)
                .sum();
        int totalDeleted = commits.stream()
                .filter(c -> c.getDeletions() != null)
                .mapToInt(Commit::getDeletions)
                .sum();

        snapshot.setTotalLinesAdded(totalAdded);
        snapshot.setTotalLinesDeleted(totalDeleted);
        snapshot.setTotalCommits(commits.size());

        int total = totalAdded + totalDeleted;
        snapshot.setChurnRatio(total > 0 ? round2((double) totalDeleted / total) : 0.0);
    }

    private void computeVelocityMetrics(MetricsSnapshot snapshot, List<PullRequest> prs, List<Commit> commits) {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        Set<String> activeContributorLogins = new HashSet<>();

        commits.stream()
                .filter(c -> c.getCommittedAt() != null && c.getCommittedAt().isAfter(thirtyDaysAgo))
                .filter(c -> c.getAuthor() != null)
                .forEach(c -> activeContributorLogins.add(c.getAuthor().getGithubLogin()));

        prs.stream()
                .filter(p -> p.getOpenedAt() != null && p.getOpenedAt().isAfter(thirtyDaysAgo))
                .filter(p -> p.getAuthor() != null)
                .forEach(p -> activeContributorLogins.add(p.getAuthor().getGithubLogin()));

        snapshot.setActiveContributors(activeContributorLogins.size());
        if (activeContributorLogins.isEmpty()) {
            snapshot.setAvgPrsPerContributorPerWeek(0.0);
            snapshot.setAvgCommitsPerContributorPerWeek(0.0);
            return;
        }

        Instant fourWeeksAgo = Instant.now().minus(28, ChronoUnit.DAYS);
        double contributors = activeContributorLogins.size();
        long recentPrs = prs.stream().filter(p -> p.getOpenedAt() != null && p.getOpenedAt().isAfter(fourWeeksAgo)).count();
        long recentCommits = commits.stream().filter(c -> c.getCommittedAt() != null && c.getCommittedAt().isAfter(fourWeeksAgo)).count();

        snapshot.setAvgPrsPerContributorPerWeek(round2(recentPrs / contributors / 4.0));
        snapshot.setAvgCommitsPerContributorPerWeek(round2(recentCommits / contributors / 4.0));
    }

    private void computeHealthScore(MetricsSnapshot snapshot) {
        int score = 0;
        if (snapshot.getAvgTimeToFirstReviewHours() != null) {
            double reviewHours = snapshot.getAvgTimeToFirstReviewHours();
            if (reviewHours < 4) score += 25;
            else if (reviewHours < 12) score += 20;
            else if (reviewHours < 24) score += 15;
            else if (reviewHours < 48) score += 8;
        }
        if (snapshot.getAvgPrMergeHours() != null) {
            double mergeHours = snapshot.getAvgPrMergeHours();
            if (mergeHours < 24) score += 25;
            else if (mergeHours < 48) score += 20;
            else if (mergeHours < 96) score += 12;
            else if (mergeHours < 168) score += 6;
        }
        if (snapshot.getChurnRatio() != null) {
            double churn = snapshot.getChurnRatio();
            if (churn >= 0.2 && churn <= 0.4) score += 25;
            else if (churn >= 0.1 && churn <= 0.5) score += 18;
            else if (churn >= 0.05) score += 10;
        }
        int activeContribs = snapshot.getActiveContributors() != null ? snapshot.getActiveContributors() : 0;
        double commitVelocity = snapshot.getAvgCommitsPerContributorPerWeek() != null
                ? snapshot.getAvgCommitsPerContributorPerWeek() : 0;
        if (activeContribs >= 3 && commitVelocity >= 3) score += 25;
        else if (activeContribs >= 2 && commitVelocity >= 2) score += 18;
        else if (activeContribs >= 1 && commitVelocity >= 1) score += 10;
        else if (activeContribs >= 1) score += 5;

        snapshot.setHealthScore(Math.min(100, score));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
