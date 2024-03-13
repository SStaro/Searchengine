package searchengine.dto.searching.results;

import lombok.Getter;
import lombok.Setter;
import searchengine.dto.searching.SearchingData;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SearchingResultFail extends SearchingResult{
    private final String error;
    private List<SearchingData> data;

    public SearchingResultFail(String error) {
        setResult(false);
        this.error = error;
        data = new ArrayList<>();
    }
}
