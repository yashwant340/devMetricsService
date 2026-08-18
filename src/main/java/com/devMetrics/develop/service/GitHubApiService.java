package com.devMetrics.develop.service;

import com.devMetrics.develop.dto.GitHubCommitDto;
import com.devMetrics.develop.dto.GitHubPrDto;
import com.devMetrics.develop.dto.GitHubReviewDto;
import com.devMetrics.develop.exceptions.GitHubApiException;
import com.devMetrics.develop.exceptions.RepoNotFoundException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class GitHubApiService {

    private final WebClient webClient;

    public GitHubApiService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    // Fetch repos the authenticated user can access
    public List<GitHubRepoDto> fetchUserRepos(String accessToken) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/user/repos")
                        .queryParam("sort", "updated")
                        .queryParam("per_page", "100")
                        .queryParam("affiliation", "owner,collaborator,organization_member")
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToFlux(GitHubRepoDto.class)
                .collectList()
                .block();
    }

    // Fetch a single repo by full name — used to validate on connect
    public GitHubRepoDto fetchRepo(String accessToken, String fullName) {
        return webClient.get()
                .uri("/repos/" + fullName)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, res -> {
                    if (res.statusCode() == HttpStatus.NOT_FOUND) {
                        return Mono.error(new RepoNotFoundException(
                                "Repo not found: " + fullName));
                    }
                    return Mono.error(new GitHubApiException(
                            "GitHub API error: " + res.statusCode()));
                })
                .bodyToMono(GitHubRepoDto.class)
                .block();
    }

    // DTO that maps GitHub's /repos response
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubRepoDto(
            @JsonProperty("id")          Long id,
            @JsonProperty("name")        String name,
            @JsonProperty("full_name")   String fullName,
            @JsonProperty("private")     boolean isPrivate,
            @JsonProperty("description") String description,
            @JsonProperty("language")    String language,
            @JsonProperty("stargazers_count") Integer starsCount,
            @JsonProperty("default_branch")   String defaultBranch,
            @JsonProperty("owner")       OwnerDto owner,
            @JsonProperty("updated_at")  String updatedAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OwnerDto(
            @JsonProperty("login") String login
    ) {}

    // Fetch all PRs for a repo — paginates through all pages
    public List<GitHubPrDto> fetchPullRequests(
            String accessToken, String fullName) {

        log.info("Fetching pull requests for full name {}", fullName);
        List<GitHubPrDto> all = new ArrayList<>();
        int page = 1;

        while (true) {
            log.info("Fetching pull requests inside while loop {}", fullName);
            final int p = page;
            List<GitHubPrDto> batch = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/" + fullName + "/pulls")
                            .queryParam("state", "all")
                            .queryParam("per_page", "100")
                            .queryParam("page", p)
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToFlux(GitHubPrDto.class)
                    .collectList()
                    .block();

            log.info("Fetched pull request{}", fullName);
            if (batch == null || batch.isEmpty()) break;
            all.addAll(batch);
            if (batch.size() < 100) break;
            page++;
        }

        log.info("Fetched {} PRs for {}", all.size(), fullName);
        return all;
    }

    // Fetch reviews for a single PR
    public List<GitHubReviewDto> fetchReviews(
            String accessToken, String fullName, Integer prNumber) {

        return webClient.get()
                .uri("/repos/" + fullName + "/pulls/" + prNumber + "/reviews")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToFlux(GitHubReviewDto.class)
                .collectList()
                .block();
    }

    // Fetch commits for a repo — paginates
    public List<GitHubCommitDto> fetchCommits(
            String accessToken, String fullName) {

        List<GitHubCommitDto> all = new ArrayList<>();
        int page = 1;

        // Only sync last 90 days of commits to stay within rate limits
        String since = Instant.now()
                .minus(90, ChronoUnit.DAYS)
                .toString();

        while (true) {
            final int p = page;
            List<GitHubCommitDto> batch = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/" + fullName + "/commits")
                            .queryParam("per_page", "100")
                            .queryParam("page", p)
                            .queryParam("since", since)
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToFlux(GitHubCommitDto.class)
                    .collectList()
                    .block();

            if (batch == null || batch.isEmpty()) break;
            all.addAll(batch);
            if (batch.size() < 100) break;
            page++;
        }

        log.info("Fetched {} commits for {}", all.size(), fullName);
        return all;
    }

    // Fetch a single commit for stats (additions/deletions)
    // The list endpoint doesn't include stats — need individual fetch
    public GitHubCommitDto fetchCommit(
            String accessToken, String fullName, String sha) {

        return webClient.get()
                .uri("/repos/" + fullName + "/commits/" + sha)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(GitHubCommitDto.class)
                .block();
    }
}
