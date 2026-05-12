package com.devMetrics.develop.service;

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
}
