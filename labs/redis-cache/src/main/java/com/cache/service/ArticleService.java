package com.cache.service;

import com.cache.domain.Article;
import com.cache.persistence.ArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Cache-aside on reads: {@link Cacheable} runs the method body only on cache miss.
 * Writes evict the key so the next read reloads from the simulated slow store.
 */
@Service
public class ArticleService {

    private static final Logger log = LoggerFactory.getLogger(ArticleService.class);

    private final ArticleRepository articleRepository;

    @Value("${app.simulated-db-delay-ms:0}")
    private long simulatedDbDelayMs;

    public ArticleService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    @Cacheable(cacheNames = "articles", key = "#id")
    @Transactional(readOnly = true)
    public Article getById(Long id) {
        simulateSlowDatastore("getById(" + id + ")");
        return articleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Article not found: " + id));
    }

    @CacheEvict(cacheNames = "articles", key = "#id")
    // @CachePut(cacheNames = "articles", key = "#id")
    @Transactional
    public Article update(Long id, String title, String content) {
        simulateSlowDatastore("update(" + id + ")");
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Article not found: " + id));
        article.setTitle(title);
        article.setContent(content);
        return articleRepository.save(article);
    }

    @CacheEvict(cacheNames = "articles", key = "#id")
    @Transactional
    public void deleteById(Long id) {
        simulateSlowDatastore("deleteById(" + id + ")");
        if (!articleRepository.existsById(id)) {
            throw new ResponseStatusException(NOT_FOUND, "Article not found: " + id);
        }
        articleRepository.deleteById(id);
    }

    private void simulateSlowDatastore(String operation) {
        if (simulatedDbDelayMs <= 0) {
            return;
        }
        try {
            log.debug("Simulating slow datastore: {} ({} ms)", operation, simulatedDbDelayMs);
            Thread.sleep(simulatedDbDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating datastore latency", e);
        }
    }
}
