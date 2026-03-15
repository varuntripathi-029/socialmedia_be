package com.socialmedia.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.socialmedia.app.model.UsernameHistory;

@Repository
public interface UsernameHistoryRepository extends JpaRepository<UsernameHistory, Long> {

    boolean existsByUsername(String username);

    List<UsernameHistory> findByUserId(Long userId);
}
