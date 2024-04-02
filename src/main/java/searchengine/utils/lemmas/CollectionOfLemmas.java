package searchengine.utils.lemmas;

import java.util.HashMap;

public interface CollectionOfLemmas {

    HashMap<String, Integer> collectLemmas(String text);
    String textWithoutHtmlTags(String htmlText);
}
