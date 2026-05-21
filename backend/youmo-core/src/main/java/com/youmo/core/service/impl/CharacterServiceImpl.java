package com.youmo.core.service.impl;

import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.Character;
import com.youmo.core.repository.CharacterRepository;
import com.youmo.core.service.CharacterService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CharacterServiceImpl implements CharacterService {

    private final CharacterRepository characterRepository;

    @Override
    @Transactional
    public Character create(Character character) {
        return characterRepository.save(character);
    }

    @Override
    public Optional<Character> getById(Long id) {
        return characterRepository.findById(id);
    }

    @Override
    public List<Character> listByBook(Long bookId) {
        return characterRepository.findByBookId(bookId);
    }

    @Override
    @Transactional
    public Character update(Long id, Character updates) {
        Character character = characterRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "角色不存在"));
        character.setName(updates.getName());
        character.setGender(updates.getGender());
        character.setAgeDescription(updates.getAgeDescription());
        character.setAppearance(updates.getAppearance());
        character.setOrigin(updates.getOrigin());
        character.setIdentity(updates.getIdentity());
        character.setDepthLevel(updates.getDepthLevel());
        character.setIsArchived(updates.getIsArchived());
        character.setAppearChapters(updates.getAppearChapters());
        character.setExtraAttributes(updates.getExtraAttributes());
        return characterRepository.save(character);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!characterRepository.existsById(id)) {
            throw new BusinessException(404, "角色不存在");
        }
        characterRepository.deleteById(id);
    }
}
