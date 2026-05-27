package com.youmo.core.service;

import com.youmo.common.entity.Foreshadowing;
import java.util.List;
import java.util.Map;

public interface ForeshadowingService {

    List<Foreshadowing> listByBook(Long bookId);

    Foreshadowing create(Foreshadowing foreshadowing);

    Foreshadowing update(Long id, Foreshadowing foreshadowing);

    void delete(Long id);

    /** AI scan a chapter for new/recycled foreshadowings. Returns {new: [...], recycled: [...]} */
    Map<String, List<Foreshadowing>> scanChapter(Long bookId, Long chapterStructureId, String chapterContent);
}
