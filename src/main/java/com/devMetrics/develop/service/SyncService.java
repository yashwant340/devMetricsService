package com.devMetrics.develop.service;

import com.devMetrics.develop.dto.GitHubCommitDto;
import com.devMetrics.develop.dto.GitHubPrDto;
import com.devMetrics.develop.dto.GitHubReviewDto;
import com.devMetrics.develop.entity.*;
import com.devMetrics.develop.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SyncService {

    private final RepositoryRepository repositoryRepository;
    private final ContributorRepository contributorRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PrReviewRepository prReviewRepository;
    private final CommitRepository commitRepository;
    private final GitHubApiService        gitHubApiService;

    // Entry point — sync a single repo
    public void syncRepo(Repository repo) {
        log.info("Starting sync for repo: {}", repo.getFullName());

        // Guard — owner or token missing means we can't call GitHub API
        if (repo.getOwner() == null) {
            log.error("Sync aborted for {}: owner is null", repo.getFullName());
            return;
        }
        // Get the owner's GitHub access token
        String token = repo.getOwner().getAccessToken();
        if (token == null || token.isBlank()) {
            log.error("Sync aborted for {}: access token is null or blank",
                    repo.getFullName());
            return;
        }
        log.info("Token: {}", token);
        try {
            syncPullRequests(repo, token);
            syncCommits(repo, token);

            // Mark last synced timestamp
            repo.setLastSyncedAt(Instant.now());
            repositoryRepository.save(repo);

            log.info("Sync complete for: {}", repo.getFullName());

        } catch (Exception e) {
            log.error("Sync failed for {}: {}",
                    repo.getFullName(), e.getMessage(), e);
        }
    }

    // ── Pull Requests ──────────────────────────────────────────────────

    private void syncPullRequests(Repository repo, String token) {
        log.info("Starting syncing pull request for repo: {}", repo.getFullName());
        List<GitHubPrDto> prs = gitHubApiService
                .fetchPullRequests(token, repo.getFullName());

        for (GitHubPrDto dto : prs) {
            upsertPullRequest(repo, dto, token);
        }
    }

    private void upsertPullRequest(
            Repository repo, GitHubPrDto dto, String token) {

        PullRequest pr = pullRequestRepository
                .findByRepoAndGithubPrNumber(repo, dto.number())
                .orElse(null);

        // If still getting duplicates during transition, use existsBy check
        if (pr == null) {
            pr = PullRequest.builder()
                    .repo(repo)
                    .githubPrNumber(dto.number())
                    .build();
        }

        if (dto.user() != null) {
            Contributor author = resolveContributor(
                    repo, dto.user().login(), dto.user().avatarUrl());
            pr.setAuthor(author);
        }

        pr.setTitle(dto.title());
        pr.setAdditions(dto.additions());
        pr.setDeletions(dto.deletions());
        pr.setChangedFiles(dto.changedFiles());
        pr.setOpenedAt(parseInstant(dto.createdAt()));
        pr.setMergedAt(parseInstant(dto.mergedAt()));
        pr.setClosedAt(parseInstant(dto.closedAt()));

        if (dto.merged() != null && dto.merged()) {
            pr.setState("merged");
        } else {
            pr.setState(dto.state());
        }

        PullRequest saved = pullRequestRepository.save(pr);
        syncReviews(repo, saved, token);
    }

    // ── Reviews ────────────────────────────────────────────────────────

    private void syncReviews(
            Repository repo, PullRequest pr, String token) {

        List<GitHubReviewDto> reviews = gitHubApiService
                .fetchReviews(token, repo.getFullName(),
                        pr.getGithubPrNumber());

        Instant firstReview = null;

        for (GitHubReviewDto dto : reviews) {

            // Skip if already stored
            if (prReviewRepository.existsByGithubReviewId(dto.id())) {
                continue;
            }

            Contributor reviewer = null;
            if (dto.user() != null) {
                reviewer = resolveContributor(
                        repo, dto.user().login(), dto.user().avatarUrl());
            }

            Instant submittedAt = parseInstant(dto.submittedAt());

            PrReview review = PrReview.builder()
                    .pullRequest(pr)
                    .reviewer(reviewer)
                    .githubReviewId(dto.id())
                    .state(dto.state())
                    .submittedAt(submittedAt)
                    .build();

            prReviewRepository.save(review);

            // Track the earliest review for first_review_at
            if (submittedAt != null) {
                if (firstReview == null
                        || submittedAt.isBefore(firstReview)) {
                    firstReview = submittedAt;
                }
            }
        }

        // Update first_review_at on the PR if we found one
        if (firstReview != null && pr.getFirstReviewAt() == null) {
            pr.setFirstReviewAt(firstReview);
            pullRequestRepository.save(pr);
        }
    }

    // ── Commits ────────────────────────────────────────────────────────

    private void syncCommits(Repository repo, String token) {
        List<GitHubCommitDto> commits = gitHubApiService
                .fetchCommits(token, repo.getFullName());

        for (GitHubCommitDto dto : commits) {

            // Skip if already stored — SHAs are immutable
            if (commitRepository.existsBySha(dto.sha())) {
                continue;
            }

            Contributor author = null;
            if (dto.author() != null) {
                author = resolveContributor(
                        repo,
                        dto.author().login(),
                        dto.author().avatarUrl());
            }

            // Fetch individual commit for stats (additions/deletions)
            // Only do this for commits without stats to save API calls
            GitHubCommitDto detailed = gitHubApiService
                    .fetchCommit(token, repo.getFullName(), dto.sha());

            Integer additions = null, deletions = null, changedFiles = null;
            if (detailed != null && detailed.stats() != null) {
                additions    = detailed.stats().additions();
                deletions    = detailed.stats().deletions();
                changedFiles = detailed.stats().total();
            }

            Instant committedAt = null;
            if (dto.commit() != null && dto.commit().author() != null) {
                committedAt = parseInstant(dto.commit().author().date());
            }

            String message = dto.commit() != null
                    ? dto.commit().message() : null;

            Commit commit = Commit.builder()
                    .repo(repo)
                    .author(author)
                    .sha(dto.sha())
                    .message(message)
                    .additions(additions)
                    .deletions(deletions)
                    .changedFiles(changedFiles)
                    .committedAt(committedAt)
                    .build();

            commitRepository.save(commit);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    // Find existing contributor or create a new one for this repo
    private Contributor resolveContributor(
            Repository repo, String login, String avatarUrl) {

        return contributorRepository
                .findByRepoAndGithubLogin(repo, login)
                .orElseGet(() -> contributorRepository.save(
                        Contributor.builder()
                                .repo(repo)
                                .githubLogin(login)
                                .avatarUrl(avatarUrl)
                                .displayName(login)
                                .build()
                ));
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            log.warn("Could not parse date: {}", value);
            return null;
        }
    }
}
