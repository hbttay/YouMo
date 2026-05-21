package com.youmo.core.repository;

import com.youmo.common.entity.WorldSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorldSettingRepository extends JpaRepository<WorldSetting, Long> {

    Optional<WorldSetting> findByBookId(Long bookId);
}
