package com.youmo.api.controller;

import com.youmo.api.dto.request.CreateBookRequest;
import com.youmo.api.dto.response.BookResponse;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.entity.Book;
import com.youmo.common.entity.User;
import com.youmo.core.service.BookService;
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
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @PostMapping
    public ApiResponse<BookResponse> create(@RequestBody CreateBookRequest req) {
        Book book = new Book();
        book.setTitle(req.getTitle());
        book.setTheme(req.getTheme());
        book.setCoreIdea(req.getCoreIdea());
        book.setToneLabels(req.getToneLabels());
        book.setOneSentence(req.getOneSentence());
        book.setTargetReaderProfile(req.getTargetReaderProfile());
        book.setViolenceLevel(req.getViolenceLevel());
        book.setRomanceLevel(req.getRomanceLevel());
        book.setPoliticsLevel(req.getPoliticsLevel());
        book.setCivilityLevel(req.getCivilityLevel());
        book.setCreationMode(req.getCreationMode());
        book.setCharacterMode(req.getCharacterMode());
        book.setLengthType(req.getLengthType());
        book.setEstimatedWords(req.getEstimatedWords());
        // 设置 owner
        User owner = new User();
        owner.setId(req.getOwnerId());
        book.setOwner(owner);
        return ApiResponse.ok(BookResponse.from(bookService.create(book)));
    }

    @GetMapping
    public ApiResponse<List<BookResponse>> listByOwner(@RequestBody Long ownerId) {
        List<BookResponse> list = bookService.listByOwner(ownerId)
                .stream().map(BookResponse::from).toList();
        return ApiResponse.ok(list);
    }

    @GetMapping("/{id}")
    public ApiResponse<BookResponse> getById(@PathVariable Long id) {
        return bookService.getById(id)
                .map(b -> ApiResponse.ok(BookResponse.from(b)))
                .orElse(ApiResponse.fail(404, "书籍不存在"));
    }

    @PutMapping("/{id}")
    public ApiResponse<BookResponse> update(@PathVariable Long id, @RequestBody CreateBookRequest req) {
        Book updates = new Book();
        updates.setTitle(req.getTitle());
        updates.setTheme(req.getTheme());
        Book updated = bookService.update(id, updates);
        return ApiResponse.ok(BookResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        bookService.delete(id);
        return ApiResponse.ok();
    }
}
