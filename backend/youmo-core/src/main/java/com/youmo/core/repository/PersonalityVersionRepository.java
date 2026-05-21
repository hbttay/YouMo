package com.youmo.core.repository;

import com.youmo.common.entity.PersonalityVersion;
import com.youmo.common.enums.VersionStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonalityVersionRepository extends JpaRepository<PersonalityVersion, Long> {

    List<PersonalityVersion> findByCharacterId(Long characterId);

    List<PersonalityVersion> findByCharacterIdAndStatus(Long characterId, VersionStatus status);
}
