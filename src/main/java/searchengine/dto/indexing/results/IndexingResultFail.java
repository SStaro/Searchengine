package searchengine.dto.indexing.results;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IndexingResultFail extends IndexingResult{
    private final String error;
    public IndexingResultFail(String error) {
        setResult(false);
        this.error = error;
    }
}
