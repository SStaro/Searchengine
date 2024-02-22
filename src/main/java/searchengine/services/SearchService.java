package searchengine.services;

import searchengine.config.Site;
import searchengine.dto.searching.results.SearchingResult;

public interface SearchService {
    SearchingResult search(String query, Site site, int offset, int limit);
}
