package com.youmo.api.dto.response;

import com.youmo.common.entity.Book;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BookResponse {
    private Long id;
    private String title;
    private String coreIdea;
    private String theme;
    private String oneSentence;
    private String creationMode;
    private String lengthType;
    private String status;
    private String extraAttributes;
    private String negativeConstraints;
    private Long ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BookResponse from(Book book) {
        BookResponse r = new BookResponse();
        r.setId(book.getId());
        r.setTitle(book.getTitle());
        r.setCoreIdea(book.getCoreIdea());
        r.setTheme(book.getTheme());
        r.setOneSentence(book.getOneSentence());
        r.setCreationMode(book.getCreationMode().name());
        r.setLengthType(book.getLengthType().name());
        r.setStatus(book.getStatus().name());
        r.setExtraAttributes(book.getExtraAttributes());
        r.setNegativeConstraints(book.getNegativeConstraints());
        r.setOwnerId(book.getOwner().getId());
        r.setCreatedAt(book.getCreatedAt());
        r.setUpdatedAt(book.getUpdatedAt());
        return r;
    }
}
