package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SitesList sitesList;

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
    public IndexingResult indexPage(String pageUrl) {
        boolean siteIsFromConfig = false;
        Site configSite = new Site();
        for (Site site : sitesList.getSites()) {
            if (pageUrl.contains(site.getDomain())) {
                siteIsFromConfig = true;
                configSite = site;
                break;
            }
        }
        if (!siteIsFromConfig) {
            //Данная страница за пределами сайтов
            return null;
        }

        searchengine.model.Site siteDB = configSiteToModelSite(configSite);
        boolean siteAlreadyInRepository = false;

        for (searchengine.model.Site siteModel : siteRepository.findAll()) {
            if (siteModel.getUrl().equals(siteDB.getUrl())) {
                siteDB = siteModel;
                siteAlreadyInRepository = true;
                break;
            }
        }
        if (!siteAlreadyInRepository) {
            siteDB.setStatus(Status.INDEXING);
        }

        siteDB.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteDB);

        boolean isPageAlreadyInDB = false;

        Page page = new Page();

        int startOfPath = pageUrl.indexOf(configSite.getDomain()) + configSite.getDomain().length();

        String path = pageUrl.substring(startOfPath);

        page.setPath(path);
        page.setSite(siteDB);

        for (Page pageDB : pageRepository.findAll()) {
            if (page.equals(pageDB)) {
                pageRepository.delete(pageDB);
                isPageAlreadyInDB = true;
                break;
            }
        }

        try {
            page.setCode(Jsoup.connect(pageUrl).execute().statusCode());
            String getHtml = Jsoup.connect(pageUrl).get().html();
            page.setContent(getHtml);

            pageRepository.save(page);

            //Lemmas code
            LemmaServiceImpl lemmaServiceImpl = new LemmaServiceImpl(lemmaRepository, pageRepository);
            HashMap<String, Integer> lemmas = lemmaServiceImpl.collectLemmas(getHtml);

            for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
                boolean isLemmaAlreadyInDB = false;
                Lemma newLemma = new Lemma();
                for (Lemma lemma : lemmaRepository.findAll()) {
                    if (lemma.getLemma().equals(entry.getKey())) {


                        //TODO: Нужно удалить старые леммы
                        if (isPageAlreadyInDB) {
                            lemma.setFrequency(lemma.getFrequency() - 1);
                        }

                        lemma.setFrequency(lemma.getFrequency() + 1);
                        lemmaRepository.save(lemma);
                        isLemmaAlreadyInDB = true;
                        newLemma = lemma;
                        break;
                    }
                }
                if (!isLemmaAlreadyInDB) {
                    newLemma.setSite(siteDB);
                    newLemma.setLemma(entry.getKey());
                    newLemma.setFrequency(1);
                    lemmaRepository.save(newLemma);
                }
                Index index = new Index();
                index.setPage(page);
                index.setLemma(newLemma);
                index.setRank(entry.getValue());
                indexRepository.save(index);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

//        for (Page pageDB : pageRepository.findAll()) {
//            if (pageDB.equals(page)) {
//                return;
//            }
//        }

        siteDB.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteDB);

        return null;
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
                PageProcessor processor = new PageProcessor(siteConfig, siteConfig, pageRepository,
                        siteRepository, lemmaRepository, indexRepository);
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
