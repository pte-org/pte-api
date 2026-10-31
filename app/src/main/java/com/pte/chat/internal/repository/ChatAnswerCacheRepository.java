package com.pte.chat.internal.repository;

import com.pte.chat.internal.domain.ChatAnswerCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatAnswerCacheRepository extends JpaRepository<ChatAnswerCache, Long> {

    Optional<ChatAnswerCache> findByCacheKey(String cacheKey);
}
