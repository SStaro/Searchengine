package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.indexing.IndexingResult;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.LemmaService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final LemmaService lemmaService;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService, LemmaService lemmaService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.lemmaService = lemmaService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResult> startIndexing() {
        return ResponseEntity.ok(indexingService.getIndexingResult());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResult> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<IndexingResult> indexPage(String url) {
        return ResponseEntity.ok(indexingService.indexPage(url));
    }
}
