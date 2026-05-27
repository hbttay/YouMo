package com.youmo.core.service;

import java.util.List;

public interface DeepSeekEmbeddingService {

    List<Float> embed(String text);

    List<List<Float>> embedBatch(List<String> texts);

    String vectorToString(List<Float> vector);

    List<Float> stringToVector(String str);
}
