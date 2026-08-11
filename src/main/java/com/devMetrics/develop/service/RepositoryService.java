package com.devMetrics.develop.service;

import com.devMetrics.develop.entity.Repository;
import com.devMetrics.develop.entity.User;
import com.devMetrics.develop.exceptions.RepoAlreadyConnectedException;
import com.devMetrics.develop.exceptions.RepoNotFoundException;
import com.devMetrics.develop.repository.RepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryService {

    private final RepositoryRepository repositoryRepository;
    private final GitHubApiService gitHubApiService;

    // Return all repos already connected by this user
    public List<Repository> getConnectedRepos(User user) {
        return repositoryRepository.findByOwnerAndConnectedTrue(user);
    }

    // Fetch all repos visible to the user from GitHub (for the picker UI)
    public List<GitHubApiService.GitHubRepoDto> getAvailableRepos(User user) {
        return gitHubApiService.fetchUserRepos(user.getAccessToken());
    }

    // Connect a repo: validate it on GitHub, then save it
    public Repository connectRepo(User user, String fullName) {

        GitHubApiService.GitHubRepoDto ghRepo =
                gitHubApiService.fetchRepo(user.getAccessToken(), fullName);

        Repository existing = repositoryRepository.findByGithubRepoId(ghRepo.id())
                .orElse(null);
        if (existing != null && !existing.getOwner().getId().equals(user.getId())) {
            throw new RepoAlreadyConnectedException(
                    fullName + " is already connected");
        }

        // Reconnect the original row rather than creating a replacement. This
        // preserves foreign-key relationships to contributors and other data.
        if (existing != null) {
            if (existing.isConnected()) {
                throw new RepoAlreadyConnectedException(
                        fullName + " is already connected");
            }
            existing.setConnected(true);
            Repository saved = repositoryRepository.save(existing);
            log.info("Repo reconnected: {} by user: {}", fullName, user.getLogin());
            return saved;
        }

        Repository repo = Repository.builder()
                .owner(user)
                .githubRepoId(ghRepo.id())
                .fullName(ghRepo.fullName())
                .name(ghRepo.name())
                .ownerLogin(ghRepo.owner().login())
                .description(ghRepo.description())
                .defaultBranch(ghRepo.defaultBranch())
                .isPrivate(ghRepo.isPrivate())
                .starsCount(ghRepo.starsCount())
                .language(ghRepo.language())
                .connected(true)
                .build();

        Repository saved = repositoryRepository.save(repo);
        log.info("Repo connected: {} by user: {}", fullName, user.getLogin());
        return saved;
    }

    // Disconnect without deleting the row or its dependent records.
    public void disconnectRepo(User user, UUID repoId) {
        Repository repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new RepoNotFoundException(
                        "Repo not found: " + repoId));

        if (!repo.getOwner().getId().equals(user.getId())) {
            throw new SecurityException("Not your repo");
        }

        repo.setConnected(false);
        repositoryRepository.save(repo);
        log.info("Repo disconnected: {} by user: {}",
                repo.getFullName(), user.getLogin());
    }
}
