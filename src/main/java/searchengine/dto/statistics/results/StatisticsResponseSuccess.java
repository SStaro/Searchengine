package searchengine.dto.statistics.results;

import lombok.Getter;
import lombok.Setter;
import searchengine.dto.statistics.StatisticsData;

@Getter
@Setter
public class StatisticsResponseSuccess extends StatisticsResponse {
    private StatisticsData statistics;

    public StatisticsResponseSuccess() {
        setResult(true);
    }
}
