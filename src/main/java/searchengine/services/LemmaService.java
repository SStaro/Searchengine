package searchengine.services;

import searchengine.dto.indexing.IndexingResult;

import java.util.HashMap;

public interface LemmaService {

    HashMap<String, Integer> collectLemmas(String text);
    String textWithoutHtmlTags(String htmlText);
}
