package com.youmo.core.repository;

import com.youmo.common.entity.ChapterStructure;
import com.youmo.common.enums.NodeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChapterStructureRepository extends JpaRepository<ChapterStructure, Long> {

    List<ChapterStructure> findByBookId(Long bookId);

    List<ChapterStructure> findByParentId(Long parentId);

    List<ChapterStructure> findByBookIdAndNodeType(Long bookId, NodeType nodeType);
}
