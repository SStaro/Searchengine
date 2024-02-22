package searchengine.utils;

import lombok.Data;

@Data
public class SearchingPageInfo {
    private String uri;
    private String title;
    private String snippet;
    private float relevance;
}
