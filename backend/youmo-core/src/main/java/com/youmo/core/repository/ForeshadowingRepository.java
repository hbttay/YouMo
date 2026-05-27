package com.youmo.core.repository;

import com.youmo.common.entity.Foreshadowing;
import com.youmo.common.enums.ForeshadowingStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ForeshadowingRepository extends JpaRepository<Foreshadowing, Long> {

    List<Foreshadowing> findByBookId(Long bookId);

    List<Foreshadowing> findByBookIdAndStatus(Long bookId, ForeshadowingStatus status);

    List<Foreshadowing> findByBookIdAndStatusOrderByCreatedAtDesc(Long bookId, ForeshadowingStatus status);
}
