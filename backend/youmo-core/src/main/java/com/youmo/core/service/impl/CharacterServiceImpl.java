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
        if (updates.getName() != null) character.setName(updates.getName());
        if (updates.getGender() != null) character.setGender(updates.getGender());
        if (updates.getAgeDescription() != null) character.setAgeDescription(updates.getAgeDescription());
        if (updates.getAppearance() != null) character.setAppearance(updates.getAppearance());
        if (updates.getOrigin() != null) character.setOrigin(updates.getOrigin());
        if (updates.getIdentity() != null) character.setIdentity(updates.getIdentity());
        if (updates.getRace() != null) character.setRace(updates.getRace());
        if (updates.getDepthLevel() != null) character.setDepthLevel(updates.getDepthLevel());
        if (updates.getIsArchived() != null) character.setIsArchived(updates.getIsArchived());
        if (updates.getAppearChapters() != null) character.setAppearChapters(updates.getAppearChapters());
        if (updates.getExtraAttributes() != null) character.setExtraAttributes(updates.getExtraAttributes());
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
