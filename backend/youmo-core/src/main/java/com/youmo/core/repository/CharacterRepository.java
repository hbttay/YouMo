package com.youmo.core.repository;

import com.youmo.common.entity.Character;
import com.youmo.common.enums.DepthLevel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CharacterRepository extends JpaRepository<Character, Long> {

    List<Character> findByBookId(Long bookId);

    List<Character> findByBookIdAndDepthLevel(Long bookId, DepthLevel depthLevel);
}
