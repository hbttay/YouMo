package com.youmo.core.service.impl;

import com.youmo.common.entity.Character;
import com.youmo.common.entity.CharacterRelationship;
import com.youmo.core.repository.CharacterRelationshipRepository;
import com.youmo.core.repository.CharacterRepository;
import com.youmo.core.service.CharacterRelationshipService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CharacterRelationshipServiceImpl implements CharacterRelationshipService {

    private final CharacterRelationshipRepository relationshipRepository;
    private final CharacterRepository characterRepository;

    @Override
    public List<CharacterRelationship> listByBook(Long bookId) {
        return relationshipRepository.findByBookId(bookId);
    }

    @Override
    @Transactional
    public CharacterRelationship create(CharacterRelationship relationship) {
        return relationshipRepository.save(relationship);
    }

    @Override
    @Transactional
    public CharacterRelationship update(Long id, CharacterRelationship update) {
        CharacterRelationship existing = relationshipRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("关系不存在"));
        existing.setRelationshipType(update.getRelationshipType());
        existing.setDescription(update.getDescription());
        existing.setIntimacyLevel(update.getIntimacyLevel());
        existing.setSourceCharacter(update.getSourceCharacter());
        existing.setTargetCharacter(update.getTargetCharacter());
        existing.setStartChapter(update.getStartChapter());
        existing.setEndChapter(update.getEndChapter());
        return relationshipRepository.save(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        relationshipRepository.deleteById(id);
    }

    @Override
    public Map<String, Object> getGraph(Long bookId) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        List<Character> characters = characterRepository.findByBookId(bookId);
        for (Character c : characters) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", c.getId());
            node.put("name", c.getName());
            node.put("identity", c.getIdentity());
            node.put("depth", c.getDepthLevel() != null ? c.getDepthLevel().name() : "L1");
            node.put("gender", c.getGender());
            nodes.add(node);
        }

        List<CharacterRelationship> relationships = relationshipRepository.findByBookId(bookId);
        for (CharacterRelationship r : relationships) {
            Map<String, Object> edge = new HashMap<>();
            edge.put("id", r.getId());
            edge.put("source", r.getSourceCharacter() != null ? r.getSourceCharacter().getId() : null);
            edge.put("target", r.getTargetCharacter() != null ? r.getTargetCharacter().getId() : null);
            edge.put("type", r.getRelationshipType());
            edge.put("description", r.getDescription());
            edge.put("intimacy", r.getIntimacyLevel());
            edges.add(edge);
        }

        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }
}
