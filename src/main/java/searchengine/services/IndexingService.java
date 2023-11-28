package searchengine.services;

import searchengine.dto.indexing.IndexingResult;

public interface IndexingService {

    IndexingResult getIndexingResult();
    IndexingResult stopIndexing();
}
