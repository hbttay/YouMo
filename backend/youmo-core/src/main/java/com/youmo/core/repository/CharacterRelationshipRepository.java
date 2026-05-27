package com.youmo.core.repository;

import com.youmo.common.entity.CharacterRelationship;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CharacterRelationshipRepository extends JpaRepository<CharacterRelationship, Long> {

    List<CharacterRelationship> findByBookId(Long bookId);

    List<CharacterRelationship> findBySourceCharacterId(Long sourceCharacterId);

    List<CharacterRelationship> findByTargetCharacterId(Long targetCharacterId);
}
