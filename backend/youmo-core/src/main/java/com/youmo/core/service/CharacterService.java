package com.youmo.core.service;

import com.youmo.common.entity.Character;
import java.util.List;
import java.util.Optional;

public interface CharacterService {

    Character create(Character character);

    Optional<Character> getById(Long id);

    List<Character> listByBook(Long bookId);

    Character update(Long id, Character updates);

    void delete(Long id);
}
