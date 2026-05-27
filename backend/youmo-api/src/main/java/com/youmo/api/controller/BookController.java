package com.youmo.api.controller;

import com.youmo.api.dto.request.CreateBookRequest;
import com.youmo.api.dto.response.BookResponse;
import com.youmo.api.security.SecurityUtil;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.entity.Book;
import com.youmo.common.entity.User;
import com.youmo.common.enums.BookStatus;
import com.youmo.common.enums.CharacterMode;
import com.youmo.common.enums.CreationMode;
import com.youmo.common.enums.LengthType;
import com.youmo.core.service.BookService;
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
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @PostMapping
    public ApiResponse<BookResponse> create(@RequestBody CreateBookRequest req) {
        Book book = new Book();
        book.setTitle(req.getTitle());
        book.setCoreIdea(req.getCoreIdea());
        book.setTheme(req.getTheme());
        book.setToneLabels(req.getToneLabels());
        book.setOneSentence(req.getOneSentence());
        book.setTargetReaderProfile(req.getTargetReaderProfile());
        book.setViolenceLevel(req.getViolenceLevel());
        book.setRomanceLevel(req.getRomanceLevel());
        book.setPoliticsLevel(req.getPoliticsLevel());
        book.setCivilityLevel(req.getCivilityLevel());
        book.setCreationMode(req.getCreationMode() != null ? req.getCreationMode() : CreationMode.LINEAR);
        book.setCharacterMode(req.getCharacterMode() != null ? req.getCharacterMode() : CharacterMode.FIXED);
        book.setLengthType(req.getTargetLength() != null
                ? LengthType.valueOf(req.getTargetLength())
                : req.getLengthType() != null ? req.getLengthType() : LengthType.MEDIUM);
        book.setStatus(BookStatus.DRAFT);
        book.setEstimatedWords(req.getEstimatedWords());
        book.setExtraAttributes(req.getExtraAttributes());
        book.setNegativeConstraints(req.getNegativeConstraints());
        User owner = new User();
        owner.setId(SecurityUtil.getCurrentUserId());
        book.setOwner(owner);
        var created = bookService.create(book);
        log.info("Book created: id={}, title={}, userId={}", created.getId(), created.getTitle(), owner.getId());
        return ApiResponse.ok(BookResponse.from(created));
    }

    @GetMapping
    public ApiResponse<List<BookResponse>> listAll() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<BookResponse> list = bookService.listByOwner(userId)
                .stream().map(BookResponse::from).toList();
        return ApiResponse.ok(list);
    }

    @GetMapping("/{id}")
    public ApiResponse<BookResponse> getById(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        Book book = bookService.getOwnedBook(id, userId);
        return ApiResponse.ok(BookResponse.from(book));
    }

    @PutMapping("/{id}")
    public ApiResponse<BookResponse> update(@PathVariable Long id, @RequestBody CreateBookRequest req) {
        Long userId = SecurityUtil.getCurrentUserId();
        // Verify ownership
        bookService.getOwnedBook(id, userId);
        Book updates = new Book();
        updates.setTitle(req.getTitle());
        updates.setTheme(req.getTheme());
        updates.setCoreIdea(req.getCoreIdea());
        updates.setToneLabels(req.getToneLabels());
        updates.setOneSentence(req.getOneSentence());
        updates.setTargetReaderProfile(req.getTargetReaderProfile());
        updates.setViolenceLevel(req.getViolenceLevel());
        updates.setRomanceLevel(req.getRomanceLevel());
        updates.setPoliticsLevel(req.getPoliticsLevel());
        updates.setCivilityLevel(req.getCivilityLevel());
        updates.setEstimatedWords(req.getEstimatedWords());
        updates.setExtraAttributes(req.getExtraAttributes());
        updates.setNegativeConstraints(req.getNegativeConstraints());
        if (req.getTargetLength() != null) {
            updates.setLengthType(LengthType.valueOf(req.getTargetLength()));
        } else if (req.getLengthType() != null) {
            updates.setLengthType(req.getLengthType());
        }
        if (req.getCreationMode() != null) {
            updates.setCreationMode(req.getCreationMode());
        }
        if (req.getCharacterMode() != null) {
            updates.setCharacterMode(req.getCharacterMode());
        }
        if (req.getStatus() != null) {
            updates.setStatus(BookStatus.valueOf(req.getStatus()));
        }
        Book updated = bookService.update(id, updates);
        return ApiResponse.ok(BookResponse.from(updated));
    }

    @PutMapping("/reorder")
    public ApiResponse<Void> reorder(@RequestBody List<Long> bookIds) {
        Long userId = SecurityUtil.getCurrentUserId();
        bookService.reorder(userId, bookIds);
        log.info("Books reordered: userId={}, count={}", userId, bookIds.size());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        bookService.getOwnedBook(id, userId); // verify ownership
        bookService.delete(id);
        log.info("Book deleted: id={}, userId={}", id, userId);
        return ApiResponse.ok();
    }
}
