package searchengine.dto.searching;

import lombok.Data;
import searchengine.utils.SearchingPageInfo;

@Data
public class SearchingData {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private double relevance;
}
