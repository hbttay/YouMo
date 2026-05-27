package com.youmo.core.repository;

import com.youmo.common.entity.BookStyleProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookStyleProfileRepository extends JpaRepository<BookStyleProfile, Long> {

    Optional<BookStyleProfile> findByBookId(Long bookId);
}
