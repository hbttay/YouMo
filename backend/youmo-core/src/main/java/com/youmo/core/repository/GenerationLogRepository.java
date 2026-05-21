package com.youmo.core.repository;

import com.youmo.common.entity.GenerationLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GenerationLogRepository extends JpaRepository<GenerationLog, Long> {

    List<GenerationLog> findByStructureId(Long structureId);
}
