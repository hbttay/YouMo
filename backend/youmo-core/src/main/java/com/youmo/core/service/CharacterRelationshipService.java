package com.youmo.core.service;

import com.youmo.common.entity.CharacterRelationship;
import java.util.List;
import java.util.Map;

public interface CharacterRelationshipService {

    List<CharacterRelationship> listByBook(Long bookId);

    CharacterRelationship create(CharacterRelationship relationship);

    CharacterRelationship update(Long id, CharacterRelationship update);

    void delete(Long id);

    /** Return graph data: {nodes: [...], edges: [...]} */
    Map<String, Object> getGraph(Long bookId);
}
