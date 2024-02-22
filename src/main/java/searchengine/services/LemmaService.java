package searchengine.services;

import java.util.HashMap;

public interface LemmaService {

    HashMap<String, Integer> collectLemmas(String text);
    String textWithoutHtmlTags(String htmlText);
}
