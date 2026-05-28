package com.youmo.core.service.impl;

import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.ChapterContent;
import com.youmo.common.entity.ChapterStructure;
import com.youmo.common.enums.NodeStatus;
import com.youmo.core.repository.ChapterContentRepository;
import com.youmo.core.repository.ChapterStructureRepository;
import com.youmo.core.service.ChapterStructureService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChapterStructureServiceImpl implements ChapterStructureService {

    private final ChapterStructureRepository chapterStructureRepository;
    private final ChapterContentRepository chapterContentRepository;

    @Override
    @Transactional
    public ChapterStructure createNode(ChapterStructure node) {
        return chapterStructureRepository.save(node);
    }

    @Override
    public List<ChapterStructure> getTree(Long bookId) {
        List<ChapterStructure> nodes = chapterStructureRepository.findByBookIdOrderBySequenceAsc(bookId);
        // Populate word counts from latest content
        List<Long> ids = nodes.stream().map(ChapterStructure::getId).collect(Collectors.toList());
        if (!ids.isEmpty()) {
            List<ChapterContent> contents = chapterContentRepository.findLatestByStructureIds(ids);
            Map<Long, Integer> wcMap = contents.stream()
                .filter(c -> c.getStructure() != null)
                .collect(Collectors.toMap(
                    c -> c.getStructure().getId(),
                    c -> c.getWordCount() != null ? c.getWordCount() : 0,
                    (a, b) -> a));
            nodes.forEach(n -> n.setWordCount(wcMap.getOrDefault(n.getId(), 0)));
        }

        // Aggregate: parent nodes sum their children's word counts (bottom-up)
        Map<Long, List<ChapterStructure>> childrenByParent = new HashMap<>();
        for (ChapterStructure n : nodes) {
            if (n.getParent() != null) {
                childrenByParent
                    .computeIfAbsent(n.getParent().getId(), k -> new ArrayList<>())
                    .add(n);
            }
        }
        // Process deepest first by sorting on parent chain length, then accumulate
        List<ChapterStructure> sorted = new ArrayList<>(nodes);
        sorted.sort((a, b) -> Integer.compare(depth(b), depth(a)));
        for (ChapterStructure n : sorted) {
            List<ChapterStructure> children = childrenByParent.get(n.getId());
            if (children != null) {
                int sum = children.stream().mapToInt(c -> c.getWordCount() != null ? c.getWordCount() : 0).sum();
                n.setWordCount(sum);
            }
        }

        return nodes;
    }

    private int depth(ChapterStructure node) {
        int d = 0;
        ChapterStructure p = node.getParent();
        while (p != null) {
            d++;
            p = p.getParent();
        }
        return d;
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
    public ChapterStructure updateNode(Long id, ChapterStructure updates) {
        ChapterStructure node = chapterStructureRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "大纲节点不存在"));
        if (updates.getTitle() != null) node.setTitle(updates.getTitle());
        if (updates.getNodeType() != null) node.setNodeType(updates.getNodeType());
        if (updates.getSequence() != null) node.setSequence(updates.getSequence());
        if (updates.getWritingGoal() != null) node.setWritingGoal(updates.getWritingGoal());
        return chapterStructureRepository.save(node);
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
