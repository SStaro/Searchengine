package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.dto.indexing.results.IndexingResult;
import searchengine.dto.indexing.results.IndexingResultSuccess;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.utils.lemmas.CollectionOfLemmasImpl;
import searchengine.utils.PageProcessor;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.results.IndexingResultFail;
import searchengine.model.Page;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.IndexingService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SitesList sitesList;

    private static boolean indexingStopped = false;
    private static boolean indexingNow = false;

    @Override
    public IndexingResult getIndexingResult() {
        indexingStopped = false;
        if (indexingNow) {
            return new IndexingResultFail("Индексация уже запущена");
        }
        indexingNow = true;

        List<Site> siteList = sites.getSites();
        for (Site siteConfig : siteList) {
            updateSitesInSiteRepository(siteConfig);
        }

        setPagesInDB();

        return new IndexingResultSuccess();
    }

    private void updateSitesInSiteRepository(Site siteConfig) {
        searchengine.model.Site siteDB = configSiteToModelSite(siteConfig);
        for (searchengine.model.Site siteModel : siteRepository.findAll()) {
            if (siteModel.getUrl().equals(siteConfig.getUrl())) {
                siteRepository.delete(siteModel);
            }
        }
        siteDB.setStatusTime(LocalDateTime.now());

        try {
            Jsoup.connect(siteDB.getUrl()).execute();
        } catch (HttpStatusException httpStatusException) {
            siteDB.setStatus(Status.FAILED);
            setErrors(siteDB, httpStatusException.getStatusCode());
            siteRepository.save(siteDB);
            return;
        } catch (IOException exception) {
            siteDB.setStatus(Status.FAILED);
            siteDB.setLastError(exception.getMessage());
            siteRepository.save(siteDB);
            return;
        }

        siteDB.setStatus(Status.INDEXING);
        siteRepository.save(siteDB);
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
            return new IndexingResultFail("Данная страница находится за пределами " +
                    "сайтов, указанных в конфигурационном файле");
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
        siteDB.setStatusTime(LocalDateTime.now());
        IndexingResultFail httpStatusExceptionIfCantAccessSite = setErrorsIfCantAccessSite(siteDB);
        if (httpStatusExceptionIfCantAccessSite != null) {
            return httpStatusExceptionIfCantAccessSite;
        }
        if (!siteAlreadyInRepository) {
            siteDB.setStatus(Status.INDEXING);
        }
        siteRepository.save(siteDB);
        boolean isPageAlreadyInDB = false;
        Page page = new Page();
        int startOfPath = pageUrl.indexOf(configSite.getDomain()) + configSite.getDomain().length();
        String path = pageUrl.substring(startOfPath);
        return getIndexingResultAndSetInfoToRepositories(pageUrl, page, path, siteDB, isPageAlreadyInDB);
    }

    private IndexingResult getIndexingResultAndSetInfoToRepositories(String pageUrl, Page page, String path, searchengine.model.Site siteDB, boolean isPageAlreadyInDB) {
        page.setPath(path);
        page.setSite(siteDB);
        Page pageFromDB = new Page();
        for (Page pageDB : pageRepository.findAll()) {
            if (page.equals(pageDB)) {
                pageFromDB = pageDB;
                isPageAlreadyInDB = true;
                break;
            }
        }
        if (!addPageLemmasAndIndexes(page, pageUrl, isPageAlreadyInDB, pageFromDB, siteDB)) {
            return new IndexingResultFail("Невозможно получить доступ к странице");
        }
        siteDB.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteDB);
        return new IndexingResultSuccess();
    }

    private IndexingResultFail setErrorsIfCantAccessSite(searchengine.model.Site siteDB) {
        try {
            Jsoup.connect(siteDB.getUrl()).execute();
        } catch (HttpStatusException httpStatusException) {
            siteDB.setStatus(Status.FAILED);
            setErrors(siteDB, httpStatusException.getStatusCode());
            siteRepository.save(siteDB);
            return new IndexingResultFail("Невозможно получить доступ к сайту. Номер ошибки: " + httpStatusException.getStatusCode());
        } catch (IOException exception) {
            siteDB.setStatus(Status.FAILED);
            exception.printStackTrace();
            siteRepository.save(siteDB);
            return new IndexingResultFail("Невозможно получить доступ к сайту.");
        }
        return null;
    }

    @Override
    public IndexingResult stopIndexing() {

        if (!indexingNow) {
            return new IndexingResultFail("Индексация не запущена");
        }

        indexingNow = false;
        indexingStopped = true;

        return new IndexingResultSuccess();
    }

    private boolean addPageLemmasAndIndexes(Page page, String pageUrl, boolean isPageAlreadyInDB, Page pageFromDB, searchengine.model.Site siteDB) {
        try {
            page.setCode(Jsoup.connect(pageUrl).execute().statusCode());

            String getHtml = Jsoup.connect(pageUrl).get().html();
            page.setContent(getHtml);
            CollectionOfLemmasImpl collectionOfLemmasImpl = new CollectionOfLemmasImpl();
            HashMap<String, Integer> lemmas = collectionOfLemmasImpl.collectLemmas(getHtml);

            deleteOldPageAndRemoveOldLemmasInformation(isPageAlreadyInDB, pageFromDB);
            pageRepository.save(page);

            setLemmasAndIndexesToRepository(page, siteDB, lemmas);
        } catch (HttpStatusException httpStatusException) {
            page.setCode(httpStatusException.getStatusCode());
            pageRepository.save(page);
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void deleteOldPageAndRemoveOldLemmasInformation(boolean isPageAlreadyInDB, Page pageFromDB) {
        if (isPageAlreadyInDB) {

            List<Index> indexesFromThePageIndexRepository = indexRepository.findAllByPage(pageFromDB);
            List<Lemma> lemmasFromThePageIndexRepository = new ArrayList<>();
            for (Index index : indexesFromThePageIndexRepository) {
                lemmasFromThePageIndexRepository.add(index.getLemma());
            }

            for (Lemma lemmaOnThePage : lemmasFromThePageIndexRepository) {

                lemmaOnThePage.setFrequency(lemmaOnThePage.getFrequency() - 1);
                if (lemmaOnThePage.getFrequency() == 0) {
                    lemmaRepository.delete(lemmaOnThePage);
                }
            }

            pageRepository.delete(pageFromDB);
        }
    }

    private void setLemmasAndIndexesToRepository(Page page, searchengine.model.Site siteDB, HashMap<String, Integer> lemmas) {
        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            boolean isLemmaAlreadyInDB = false;
            Lemma newLemma = new Lemma();
            for (Lemma lemma : lemmaRepository.findAll()) {
                if (lemma.getLemma().equals(entry.getKey())) {
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
    }


    private void setPagesInDB() {

        int sitesIndexed = 0;

        for (Site siteConfig : sites.getSites()) {
            new Thread(() -> {
                ForkJoinPool pool = new ForkJoinPool();
                PageProcessor processor = new PageProcessor(siteConfig, siteConfig, pageRepository,
                        siteRepository, lemmaRepository, indexRepository);
                pool.invoke(processor);
                getSiteFromDBAndChangeSiteStatusIfNeeded(siteConfig);
            }).start();
        }
        for (searchengine.model.Site siteDB : siteRepository.findAll()) {
            if (siteDB.getStatus().equals(Status.INDEXED)) {
                sitesIndexed++;
            }
        }
        if (sitesIndexed == siteRepository.count()) {
            indexingNow = false;
        }
    }

    private void getSiteFromDBAndChangeSiteStatusIfNeeded(Site siteConfig) {
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
            if (!siteDB.getStatus().equals(Status.FAILED)) {
                siteDB.setStatus(Status.INDEXED);
                siteRepository.save(siteDB);
            }
        }
    }

    public static searchengine.model.Site configSiteToModelSite(Site siteConfig) {
        searchengine.model.Site siteDB = new searchengine.model.Site();
        siteDB.setUrl(siteConfig.getUrl());
        siteDB.setName(siteConfig.getName());
        return siteDB;
    }

    private void setErrors(searchengine.model.Site site, int errorCode) {
        switch (errorCode) {
            case 400 -> site.setLastError("Код ошибки: " + errorCode + " - Bad Request");
            case 401 -> site.setLastError("Код ошибки: " + errorCode + " - Unauthorized Error");
            case 403 -> site.setLastError("Код ошибки: " + errorCode + " - Forbidden");
            case 404 -> site.setLastError("Код ошибки: " + errorCode + " - Not Found");
            case 405 -> site.setLastError("Код ошибки: " + errorCode + " - Method Not Allowed");
            case 500 -> site.setLastError("Код ошибки: " + errorCode + " - Internal Server Error");
            default -> site.setLastError("Невозможно получить доступ к сайту, код ошибки " + errorCode);
        }
    }


    public static boolean isIndexingStopped() {
        return indexingStopped;
    }

    public static boolean isIndexingNow() {
        return indexingNow;
    }
}
