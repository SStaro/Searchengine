package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.*;
import searchengine.dto.statistics.results.StatisticsResponse;
import searchengine.dto.statistics.results.StatisticsResponseSuccess;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.StatisticsService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SiteRepository siteRepository;

    @Override
    public StatisticsResponse getStatistics() {

        TotalStatistics total = new TotalStatistics();
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        StatisticsData data = new StatisticsData();

        total.setSites((int)siteRepository.count());

        total.setIndexing(IndexingServiceImpl.isIndexingNow());

        for(searchengine.model.Site siteDB : siteRepository.findAll()) {

            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(siteDB.getName());
            item.setUrl(siteDB.getUrl());
            int pages = pageRepository.findAllBySite(siteDB).size();
            int lemmas = lemmaRepository.findAllBySite(siteDB).size();
            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(siteDB.getStatus().toString());
            item.setError(siteDB.getLastError());

            LocalDateTime statusDate = siteDB.getStatusTime();
            long statusTime = statusDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            item.setStatusTime(statusTime);
            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            detailed.add(item);
        }

        StatisticsResponseSuccess response = new StatisticsResponseSuccess();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}
