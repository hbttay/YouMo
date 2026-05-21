package com.youmo.api.controller;

import com.youmo.api.dto.request.CreateCharacterRequest;
import com.youmo.api.dto.response.CharacterResponse;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.entity.Character;
import com.youmo.common.entity.Book;
import com.youmo.core.service.CharacterService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/{bookId}/characters")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    @PostMapping
    public ApiResponse<CharacterResponse> create(@PathVariable Long bookId, @RequestBody CreateCharacterRequest req) {
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
        character.setDepthLevel(req.getDepthLevel());
        return ApiResponse.ok(CharacterResponse.from(characterService.create(character)));
    }

    @GetMapping
    public ApiResponse<List<CharacterResponse>> list(@PathVariable Long bookId) {
        List<CharacterResponse> list = characterService.listByBook(bookId)
                .stream().map(CharacterResponse::from).toList();
        return ApiResponse.ok(list);
    }

    @GetMapping("/{id}")
    public ApiResponse<CharacterResponse> getById(@PathVariable Long id) {
        return characterService.getById(id)
                .map(c -> ApiResponse.ok(CharacterResponse.from(c)))
                .orElse(ApiResponse.fail(404, "角色不存在"));
    }

    @PutMapping("/{id}")
    public ApiResponse<CharacterResponse> update(@PathVariable Long id, @RequestBody CreateCharacterRequest req) {
        Character updates = new Character();
        updates.setName(req.getName());
        updates.setGender(req.getGender());
        updates.setAgeDescription(req.getAgeDescription());
        updates.setAppearance(req.getAppearance());
        updates.setOrigin(req.getOrigin());
        updates.setIdentity(req.getIdentity());
        updates.setDepthLevel(req.getDepthLevel());
        return ApiResponse.ok(CharacterResponse.from(characterService.update(id, updates)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        characterService.delete(id);
        return ApiResponse.ok();
    }
}
