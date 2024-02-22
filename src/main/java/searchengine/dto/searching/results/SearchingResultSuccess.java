package searchengine.dto.searching.results;

import lombok.Getter;
import lombok.Setter;
import searchengine.dto.searching.SearchingData;

import java.util.List;

@Getter
@Setter
public class SearchingResultSuccess extends SearchingResult{
    private int count;
    private List<SearchingData> data;

    public SearchingResultSuccess() {
        setResult(true);
    }
}
