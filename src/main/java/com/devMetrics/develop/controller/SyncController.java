package com.devMetrics.develop.controller;

import com.devMetrics.develop.entity.Repository;
import com.devMetrics.develop.entity.User;
import com.devMetrics.develop.exceptions.RepoNotFoundException;
import com.devMetrics.develop.repository.RepositoryRepository;
import com.devMetrics.develop.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final RepositoryRepository repositoryRepository;
    private final SyncService syncService;

    // POST /api/sync/{repoId} — trigger sync for one repo
    @PostMapping("/{repoId}")
    public ResponseEntity<?> syncRepo(
            @PathVariable UUID repoId,
            Authentication auth) {

        User user = (User) auth.getPrincipal();

        Repository repo = repositoryRepository.findById(repoId)
                .orElseThrow(() ->
                        new RepoNotFoundException("Repo not found"));

        if (!repo.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Not your repo"));
        }

        // Run sync in a separate thread so the HTTP call returns immediately
        CompletableFuture.runAsync(() -> syncService.syncRepo(repo));

        return ResponseEntity.accepted()
                .body(Map.of("message",
                        "Sync started for " + repo.getFullName()));
    }

    // POST /api/sync/all — trigger sync for all user's repos
    @PostMapping("/all")
    public ResponseEntity<?> syncAll(Authentication auth) {
        User user = (User) auth.getPrincipal();

        List<Repository> repos = repositoryRepository.findByOwner(user);

        repos.forEach(repo ->
                CompletableFuture.runAsync(() ->
                        syncService.syncRepo(repo)));

        return ResponseEntity.accepted()
                .body(Map.of(
                        "message", "Sync started",
                        "repos",   repos.size()
                ));
    }
}
