package com.youmo.core.service.impl;

import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.ChapterStructure;
import com.youmo.common.enums.NodeStatus;
import com.youmo.core.repository.ChapterStructureRepository;
import com.youmo.core.service.ChapterStructureService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChapterStructureServiceImpl implements ChapterStructureService {

    private final ChapterStructureRepository chapterStructureRepository;

    @Override
    @Transactional
    public ChapterStructure createNode(ChapterStructure node) {
        return chapterStructureRepository.save(node);
    }

    @Override
    public List<ChapterStructure> getTree(Long bookId) {
        // 返回全书大纲，Controller 层递归组装为树形 JSON
        return chapterStructureRepository.findByBookId(bookId);
    }

    @Override
    public Optional<ChapterStructure> getById(Long id) {
        return chapterStructureRepository.findById(id);
    }

    @Override
    @Transactional
    public void moveNode(Long id, Long newParentId, Integer newSequence) {
        ChapterStructure node = chapterStructureRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "大纲节点不存在"));
        node.setSequence(newSequence);
        if (newParentId != null) {
            ChapterStructure newParent = chapterStructureRepository.findById(newParentId)
                    .orElseThrow(() -> new BusinessException(404, "目标父节点不存在"));
            node.setParent(newParent);
        } else {
            node.setParent(null);
        }
        chapterStructureRepository.save(node);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, NodeStatus status) {
        ChapterStructure node = chapterStructureRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "大纲节点不存在"));
        node.setStatus(status);
        chapterStructureRepository.save(node);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!chapterStructureRepository.existsById(id)) {
            throw new BusinessException(404, "大纲节点不存在");
        }
        chapterStructureRepository.deleteById(id);
    }
}
