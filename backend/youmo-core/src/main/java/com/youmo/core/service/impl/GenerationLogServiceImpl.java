package com.youmo.core.service.impl;

import com.youmo.common.entity.GenerationLog;
import com.youmo.core.repository.GenerationLogRepository;
import com.youmo.core.service.GenerationLogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GenerationLogServiceImpl implements GenerationLogService {

    private final GenerationLogRepository generationLogRepository;

    @Override
    @Transactional
    public GenerationLog log(GenerationLog log) {
        return generationLogRepository.save(log);
    }

    @Override
    public List<GenerationLog> listByStructureId(Long structureId) {
        return generationLogRepository.findByStructureId(structureId);
    }
}
