package com.youmo.core.repository;

import com.youmo.common.entity.CharacterDetail;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CharacterDetailRepository extends JpaRepository<CharacterDetail, Long> {

    Optional<CharacterDetail> findByCharacterId(Long characterId);
}
