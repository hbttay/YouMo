package com.youmo.core.service;

import com.youmo.common.entity.ChapterStructure;
import com.youmo.common.enums.NodeStatus;
import java.util.List;
import java.util.Optional;

public interface ChapterStructureService {

    ChapterStructure createNode(ChapterStructure node);

    List<ChapterStructure> getTree(Long bookId);

    Optional<ChapterStructure> getById(Long id);

    void moveNode(Long id, Long newParentId, Integer newSequence);

    void updateStatus(Long id, NodeStatus status);

    ChapterStructure updateNode(Long id, ChapterStructure updates);

    void delete(Long id);
}
