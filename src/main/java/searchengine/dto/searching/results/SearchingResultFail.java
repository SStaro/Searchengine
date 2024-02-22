package searchengine.dto.searching.results;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchingResultFail extends SearchingResult{
    private final String error;

    public SearchingResultFail(String error) {
        setResult(false);
        this.error = error;
    }
}
