package com.youmo.api.controller;

import com.youmo.api.dto.request.CreateCharacterRequest;
import com.youmo.api.dto.response.CharacterResponse;
import com.youmo.api.security.SecurityUtil;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.Character;
import com.youmo.common.entity.Book;
import com.youmo.core.service.BookService;
import com.youmo.core.service.CharacterService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/books/{bookId}/characters")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;
    private final BookService bookService;

    private void assertOwnership(Long bookId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Book book = bookService.getById(bookId)
            .orElseThrow(() -> new BusinessException(404, "书籍不存在"));
        if (book.getOwner() == null || !book.getOwner().getId().equals(userId)) {
            throw new BusinessException(403, "无权访问此书");
        }
    }

    @PostMapping
    public ApiResponse<CharacterResponse> create(@PathVariable Long bookId, @RequestBody CreateCharacterRequest req) {
        assertOwnership(bookId);
        Character character = new Character();
        Book book = new Book();
        book.setId(bookId);
        character.setBook(book);
        character.setName(req.getName());
        character.setGender(req.getGender());
        character.setAgeDescription(req.getAgeDescription());
        character.setAppearance(req.getAppearance());
        character.setOrigin(req.getOrigin());
        character.setIdentity(req.getIdentity());
        character.setRace(req.getRace());
        character.setExtraAttributes(req.getExtraAttributes());
        character.setDepthLevel(req.getDepthLevel());
        Character created = characterService.create(character);
        log.info("Character created: bookId={}, name={}, id={}", bookId, created.getName(), created.getId());
        return ApiResponse.ok(CharacterResponse.from(created));
    }

    @GetMapping
    public ApiResponse<List<CharacterResponse>> list(@PathVariable Long bookId) {
        assertOwnership(bookId);
        List<CharacterResponse> list = characterService.listByBook(bookId)
                .stream().map(CharacterResponse::from).toList();
        return ApiResponse.ok(list);
    }

    @GetMapping("/{id}")
    public ApiResponse<CharacterResponse> getById(@PathVariable Long bookId, @PathVariable Long id) {
        assertOwnership(bookId);
        return characterService.getById(id)
                .map(c -> ApiResponse.ok(CharacterResponse.from(c)))
                .orElse(ApiResponse.fail(404, "角色不存在"));
    }

    @PutMapping("/{id}")
    public ApiResponse<CharacterResponse> update(@PathVariable Long bookId, @PathVariable Long id, @RequestBody CreateCharacterRequest req) {
        assertOwnership(bookId);
        Character updates = new Character();
        updates.setName(req.getName());
        updates.setGender(req.getGender());
        updates.setAgeDescription(req.getAgeDescription());
        updates.setAppearance(req.getAppearance());
        updates.setOrigin(req.getOrigin());
        updates.setIdentity(req.getIdentity());
        updates.setRace(req.getRace());
        updates.setExtraAttributes(req.getExtraAttributes());
        updates.setDepthLevel(req.getDepthLevel());
        Character updated = characterService.update(id, updates);
        log.info("Character updated: bookId={}, id={}, name={}", bookId, id, updated.getName());
        return ApiResponse.ok(CharacterResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long bookId, @PathVariable Long id) {
        assertOwnership(bookId);
        characterService.delete(id);
        log.info("Character deleted: bookId={}, id={}", bookId, id);
        return ApiResponse.ok();
    }
}
