package com.devMetrics.develop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubReviewDto(

        @JsonProperty("id")           Long id,
        @JsonProperty("state")        String state,
        @JsonProperty("submitted_at") String submittedAt,
        @JsonProperty("user")         UserDto user
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserDto(
            @JsonProperty("login")      String login,
            @JsonProperty("avatar_url") String avatarUrl
    ) {}
}