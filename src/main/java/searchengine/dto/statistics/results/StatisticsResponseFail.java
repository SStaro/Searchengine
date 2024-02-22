package searchengine.dto.statistics.results;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StatisticsResponseFail extends  StatisticsResponse {
    private final String error;
    public StatisticsResponseFail(String error) {
        setResult(false);
        this.error = error;
    }
}
