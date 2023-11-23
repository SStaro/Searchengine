package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
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

import java.io.IOException;
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

    @Override
    public IndexingResult getIndexingResult() {

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


            setPagesInDB(siteConfig);
            siteDB.setStatus(Status.INDEXED);
        }
        IndexingResult indexingResult = new IndexingResult();
        indexingResult.setResult(true);

        return indexingResult;
    }

    private void setPagesInDB(Site siteConfig) {

        ForkJoinPool pool = new ForkJoinPool();
        PageProcessor processor = new PageProcessor(siteConfig);
        Set<Page> pages = pool.invoke(processor);

        searchengine.model.Site siteDB = null;
        for (searchengine.model.Site siteModel : siteRepository.findAll()) {
            if (siteModel.getUrl().equals(siteConfig.getUrl())) {
                siteDB = siteModel;
                break;
            }
        }

        log.info("Got all urls");


        for (Page page : pages) {

            page.setSite(siteDB);

            pageRepository.save(page);
        }
    }

    public static searchengine.model.Site configSiteToModelSite(Site siteConfig) {
        searchengine.model.Site siteDB = new searchengine.model.Site();
        siteDB.setUrl(siteConfig.getUrl());
        siteDB.setName(siteConfig.getName());
        return siteDB;
    }
}
