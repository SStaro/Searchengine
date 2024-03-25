package searchengine.services;

import searchengine.dto.searching.results.SearchingResult;

public interface SearchService {
    SearchingResult search(String query, String site, int offset, int limit);
}
