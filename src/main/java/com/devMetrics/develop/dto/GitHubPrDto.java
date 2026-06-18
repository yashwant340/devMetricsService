package com.devMetrics.develop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPrDto(

        @JsonProperty("number")       Integer number,
        @JsonProperty("title")        String title,
        @JsonProperty("state")        String state,
        @JsonProperty("additions")    Integer additions,
        @JsonProperty("deletions")    Integer deletions,
        @JsonProperty("changed_files") Integer changedFiles,
        @JsonProperty("created_at")   String createdAt,
        @JsonProperty("merged_at")    String mergedAt,
        @JsonProperty("closed_at")    String closedAt,
        @JsonProperty("user")         UserDto user,
        @JsonProperty("merged")       Boolean merged
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserDto(
            @JsonProperty("login")      String login,
            @JsonProperty("avatar_url") String avatarUrl
    ) {}
}
