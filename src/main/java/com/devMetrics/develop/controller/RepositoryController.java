package com.devMetrics.develop.controller;

import com.devMetrics.develop.entity.Repository;
import com.devMetrics.develop.entity.User;
import com.devMetrics.develop.service.RepositoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
public class RepositoryController {

    private final RepositoryService repositoryService;

    // GET /api/repositories — all connected repos for the current user
    @GetMapping
    public ResponseEntity<List<RepoResponse>> getConnectedRepos(
            Authentication auth) {
        User user = (User) auth.getPrincipal();
        List<RepoResponse> repos = repositoryService
                .getConnectedRepos(user)
                .stream()
                .map(RepoResponse::from)
                .toList();
        return ResponseEntity.ok(repos);
    }

    // GET /api/repositories/available — repos visible on GitHub (for picker)
    @GetMapping("/available")
    public ResponseEntity<?> getAvailableRepos(Authentication auth) {
        User user = (User) auth.getPrincipal();
        return ResponseEntity.ok(
                repositoryService.getAvailableRepos(Objects.requireNonNull(user)));
    }

    // POST /api/repositories — connect a new repo
    @PostMapping
    public ResponseEntity<?> connectRepo(
            @RequestBody ConnectRepoRequest request,
            Authentication auth) {
        User user = (User) auth.getPrincipal();
        Repository repo = repositoryService
                .connectRepo(Objects.requireNonNull(user), request.fullName());
        return ResponseEntity.status(201).body(RepoResponse.from(repo));
    }

    // DELETE /api/repositories/{id} — disconnect a repo
    @DeleteMapping("/{id}")
    public ResponseEntity<?> disconnectRepo(
            @PathVariable UUID id,
            Authentication auth) {
        User user = (User) auth.getPrincipal();
        repositoryService.disconnectRepo(Objects.requireNonNull(user), id);
        return ResponseEntity.noContent().build();
    }

    // Request / Response records
    public record ConnectRepoRequest(String fullName) {}

    public record RepoResponse(
            UUID id,
            String fullName,
            String name,
            String ownerLogin,
            String description,
            String language,
            Integer starsCount,
            String defaultBranch,
            boolean isPrivate,
            Instant lastSyncedAt,
            Instant createdAt
    ) {
        public static RepoResponse from(Repository r) {
            return new RepoResponse(
                    r.getId(), r.getFullName(), r.getName(),
                    r.getOwnerLogin(), r.getDescription(),
                    r.getLanguage(), r.getStarsCount(),
                    r.getDefaultBranch(), r.isPrivate(),
                    r.getLastSyncedAt(), r.getCreatedAt()
            );
        }
    }
}