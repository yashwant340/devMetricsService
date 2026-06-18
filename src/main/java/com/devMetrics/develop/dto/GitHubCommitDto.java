package com.devMetrics.develop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommitDto(

        @JsonProperty("sha")    String sha,
        @JsonProperty("commit") CommitDetail commit,
        @JsonProperty("author") AuthorDto author,          // may be null
        @JsonProperty("stats")  StatsDto stats             // only on single fetch
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommitDetail(
            @JsonProperty("message") String message,
            @JsonProperty("author")  CommitAuthor author
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommitAuthor(
            @JsonProperty("date") String date
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthorDto(
            @JsonProperty("login")      String login,
            @JsonProperty("avatar_url") String avatarUrl
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatsDto(
            @JsonProperty("additions") Integer additions,
            @JsonProperty("deletions") Integer deletions,
            @JsonProperty("total")     Integer total
    ) {}
}
