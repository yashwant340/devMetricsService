package com.devMetrics.develop.service;

import com.devMetrics.develop.entity.Commit;
import com.devMetrics.develop.entity.MetricsSnapshot;
import com.devMetrics.develop.entity.PullRequest;
import com.devMetrics.develop.entity.Repository;
import com.devMetrics.develop.repository.CommitRepository;
import com.devMetrics.develop.repository.ContributorRepository;
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
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MetricsComputationService {

    private final PullRequestRepository pullRequestRepository;
    private final CommitRepository commitRepository;
    private final ContributorRepository contributorRepository;
    private final MetricsSnapshotRepository metricsSnapshotRepository;

    // Called after every sync completes
    public void computeAndSnapshot(Repository repo) {
        log.info("Computing metrics for: {}", repo.getFullName());

        LocalDate today = LocalDate.now();

        // Always recompute today's snapshot — overwrite if exists
        MetricsSnapshot existing = metricsSnapshotRepository
                .findByRepoAndSnapshotDate(repo, today)
                .orElse(null);

        MetricsSnapshot snapshot = existing != null
                ? existing
                : MetricsSnapshot.builder()
                .repo(repo)
                .snapshotDate(today)
                .build();

        // Fetch raw data
        List<PullRequest> allPrs     = pullRequestRepository.findByRepo(repo);
        List<Commit>      allCommits = commitRepository.findByRepo(repo);

        // Compute each metric group
        computePrMetrics(snapshot, allPrs);
        computeChurnMetrics(snapshot, allCommits);
        computeVelocityMetrics(snapshot, repo, allPrs, allCommits);
        computeHealthScore(snapshot);

        metricsSnapshotRepository.save(snapshot);

        log.info("Metrics snapshot saved for: {} | health: {}",
                repo.getFullName(), snapshot.getHealthScore());
    }

    // ── PR Metrics ──────────────────────────────────────────────────────

    private void computePrMetrics(
            MetricsSnapshot snapshot, List<PullRequest> prs) {

        long openCount   = prs.stream()
                .filter(p -> "open".equals(p.getState()))
                .count();

        long mergedCount = prs.stream()
                .filter(p -> "merged".equals(p.getState()))
                .count();

        long closedCount = prs.stream()
                .filter(p -> "closed".equals(p.getState()))
                .count();

        snapshot.setOpenPrCount((int) openCount);
        snapshot.setMergedPrCount((int) mergedCount);
        snapshot.setClosedPrCount((int) closedCount);

        // Avg merge time — only PRs that were actually merged
        OptionalDouble avgMergeHours = prs.stream()
                .filter(p -> "merged".equals(p.getState()))
                .filter(p -> p.getOpenedAt() != null
                        && p.getMergedAt() != null)
                .mapToLong(p -> Duration.between(
                        p.getOpenedAt(), p.getMergedAt()).toMinutes())
                .average();

        snapshot.setAvgPrMergeHours(
                avgMergeHours.isPresent()
                        ? round2(avgMergeHours.getAsDouble() / 60.0)
                        : null
        );

        // Avg time to first review
        OptionalDouble avgReviewHours = prs.stream()
                .filter(p -> p.getOpenedAt() != null
                        && p.getFirstReviewAt() != null)
                .mapToLong(p -> Duration.between(
                        p.getOpenedAt(), p.getFirstReviewAt()).toMinutes())
                .average();

        snapshot.setAvgTimeToFirstReviewHours(
                avgReviewHours.isPresent()
                        ? round2(avgReviewHours.getAsDouble() / 60.0)
                        : null
        );
    }

    // ── Code Churn ──────────────────────────────────────────────────────

    private void computeChurnMetrics(
            MetricsSnapshot snapshot, List<Commit> commits) {

        int totalAdded   = commits.stream()
                .filter(c -> c.getAdditions() != null)
                .mapToInt(Commit::getAdditions)
                .sum();

        int totalDeleted = commits.stream()
                .filter(c -> c.getDeletions() != null)
                .mapToInt(Commit::getDeletions)
                .sum();

        int totalFiles   = commits.stream()
                .filter(c -> c.getChangedFiles() != null)
                .mapToInt(Commit::getChangedFiles)
                .sum();

        snapshot.setTotalLinesAdded(totalAdded);
        snapshot.setTotalLinesDeleted(totalDeleted);
        snapshot.setTotalCommits(commits.size());

        // Churn ratio: what proportion of all touched lines were deletions?
        // High churn ratio = lots of rewrites. Healthy range: 0.2 – 0.45
        int total = totalAdded + totalDeleted;
        snapshot.setChurnRatio(
                total > 0
                        ? round2((double) totalDeleted / total)
                        : 0.0
        );
    }

    // ── Contributor Velocity ─────────────────────────────────────────────

    private void computeVelocityMetrics(
            MetricsSnapshot snapshot,
            Repository repo,
            List<PullRequest> prs,
            List<Commit> commits) {

        // Active = contributed at least once in last 30 days
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        Set<String> activeContributorLogins = new HashSet<>();

        commits.stream()
                .filter(c -> c.getCommittedAt() != null
                        && c.getCommittedAt().isAfter(thirtyDaysAgo))
                .filter(c -> c.getAuthor() != null)
                .forEach(c -> activeContributorLogins
                        .add(c.getAuthor().getGithubLogin()));

        prs.stream()
                .filter(p -> p.getOpenedAt() != null
                        && p.getOpenedAt().isAfter(thirtyDaysAgo))
                .filter(p -> p.getAuthor() != null)
                .forEach(p -> activeContributorLogins
                        .add(p.getAuthor().getGithubLogin()));

        snapshot.setActiveContributors(activeContributorLogins.size());

        if (activeContributorLogins.isEmpty()) {
            snapshot.setAvgPrsPerContributorPerWeek(0.0);
            snapshot.setAvgCommitsPerContributorPerWeek(0.0);
            return;
        }

        // Velocity window — last 4 weeks
        Instant fourWeeksAgo = Instant.now().minus(28, ChronoUnit.DAYS);
        double weeks = 4.0;

        long recentPrs = prs.stream()
                .filter(p -> p.getOpenedAt() != null
                        && p.getOpenedAt().isAfter(fourWeeksAgo))
                .count();

        long recentCommits = commits.stream()
                .filter(c -> c.getCommittedAt() != null
                        && c.getCommittedAt().isAfter(fourWeeksAgo))
                .count();

        double contributors = activeContributorLogins.size();

        snapshot.setAvgPrsPerContributorPerWeek(
                round2(recentPrs / contributors / weeks));

        snapshot.setAvgCommitsPerContributorPerWeek(
                round2(recentCommits / contributors / weeks));
    }

    // ── Health Score (0–100) ─────────────────────────────────────────────

    // Composite score — weighted across 4 dimensions.
    // Each dimension scores 0–25, total max = 100.
    private void computeHealthScore(MetricsSnapshot snapshot) {
        int score = 0;

        // 1. Review speed (25 pts)
        //    < 4h  = 25, < 12h = 20, < 24h = 15, < 48h = 8, else = 0
        if (snapshot.getAvgTimeToFirstReviewHours() != null) {
            double reviewHours = snapshot.getAvgTimeToFirstReviewHours();
            if      (reviewHours < 4)  score += 25;
            else if (reviewHours < 12) score += 20;
            else if (reviewHours < 24) score += 15;
            else if (reviewHours < 48) score += 8;
        }

        // 2. Merge speed (25 pts)
        //    < 24h = 25, < 48h = 20, < 96h = 12, < 168h = 6, else = 0
        if (snapshot.getAvgPrMergeHours() != null) {
            double mergeHours = snapshot.getAvgPrMergeHours();
            if      (mergeHours < 24)  score += 25;
            else if (mergeHours < 48)  score += 20;
            else if (mergeHours < 96)  score += 12;
            else if (mergeHours < 168) score += 6;
        }

        // 3. Churn health (25 pts)
        //    0.2–0.4 is ideal (balanced rewrites)
        //    < 0.1 = no cleanup, > 0.6 = too much churn
        if (snapshot.getChurnRatio() != null) {
            double churn = snapshot.getChurnRatio();
            if      (churn >= 0.2 && churn <= 0.4) score += 25;
            else if (churn >= 0.1 && churn <= 0.5) score += 18;
            else if (churn >= 0.05)                 score += 10;
        }

        // 4. Team activity (25 pts)
        //    Based on active contributors + commit velocity
        int activeContribs = snapshot.getActiveContributors() != null
                ? snapshot.getActiveContributors() : 0;
        double commitVelocity = snapshot
                .getAvgCommitsPerContributorPerWeek() != null
                ? snapshot.getAvgCommitsPerContributorPerWeek() : 0;

        if      (activeContribs >= 3 && commitVelocity >= 3) score += 25;
        else if (activeContribs >= 2 && commitVelocity >= 2) score += 18;
        else if (activeContribs >= 1 && commitVelocity >= 1) score += 10;
        else if (activeContribs >= 1)                         score += 5;

        snapshot.setHealthScore(Math.min(100, score));
    }

    // ── Helper ───────────────────────────────────────────────────────────

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // Find snapshot by repo + date — used for upsert
    private Optional<MetricsSnapshot> findByRepoAndSnapshotDate(
            Repository repo, LocalDate date) {
        return metricsSnapshotRepository
                .findByRepoSince(repo, date)
                .stream()
                .filter(m -> m.getSnapshotDate().equals(date))
                .findFirst();
    }
}
