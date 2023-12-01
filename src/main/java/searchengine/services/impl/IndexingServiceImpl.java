package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.utils.PageProcessor;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResult;
import searchengine.model.Page;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.IndexingService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    private static boolean indexingStopped = false;

    @Override
    public IndexingResult getIndexingResult() {

        indexingStopped = false;

        log.info("Method started");


        List<Site> siteList = sites.getSites();
        for (Site siteConfig : siteList) {
            searchengine.model.Site siteDB = configSiteToModelSite(siteConfig);
            for (searchengine.model.Site siteModel : siteRepository.findAll()) {
                if (siteModel.getUrl().equals(siteConfig.getUrl())) {
                    siteRepository.delete(siteModel);
                }
            }
            siteDB.setStatus(Status.INDEXING);
            siteDB.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteDB);


            log.info("Site saved to DB");

        }

        setPagesInDB();

        IndexingResult indexingResult = new IndexingResult();
        indexingResult.setResult(true);

//        PageProcessor.getLinkSet().clear();

        return indexingResult;
    }

    @Override
    public IndexingResult stopIndexing() {
        indexingStopped = true;

        IndexingResult indexingResult = new IndexingResult();
        indexingResult.setResult(true);
        return indexingResult;
    }

    private void setPagesInDB() {

        for (Site siteConfig : sites.getSites()) {
            new Thread(() -> {
                ForkJoinPool pool = new ForkJoinPool();
                PageProcessor processor = new PageProcessor(siteConfig, siteConfig, pageRepository, siteRepository);
                pool.invoke(processor);

                log.info("Thread " + siteConfig.getDomain() + " is here");

                searchengine.model.Site siteDB = null;
                for (searchengine.model.Site siteModel : siteRepository.findAll()) {
                    if (siteModel.getUrl().equals(siteConfig.getUrl())) {
                        siteDB = siteModel;
                        break;
                    }
                }

                if (indexingStopped) {
                    siteDB.setStatus(Status.FAILED);
                    siteDB.setLastError("Индексация остановлена пользователем");

                    siteRepository.save(siteDB);

                } else {
                    siteDB.setStatus(Status.INDEXED);

                    siteRepository.save(siteDB);
                }
            }).start();
        }
    }

    public static searchengine.model.Site configSiteToModelSite(Site siteConfig) {
        searchengine.model.Site siteDB = new searchengine.model.Site();
        siteDB.setUrl(siteConfig.getUrl());
        siteDB.setName(siteConfig.getName());
        return siteDB;
    }

    public static boolean isIndexingStopped() {
        return indexingStopped;
    }
}
