package searchengine.services.impl;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.dto.searching.*;
import searchengine.dto.searching.results.SearchingResult;
import searchengine.dto.searching.results.SearchingResultFail;
import searchengine.dto.searching.results.SearchingResultSuccess;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.services.SearchService;
import searchengine.utils.SearchingPageInfo;

import java.io.IOException;
import java.util.*;

@Service
public class SearchServiceImpl implements SearchService {

    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final LemmaServiceImpl lemmaService;
    private final float lemmaFrequencyLimit = 1;

    public SearchServiceImpl(LemmaRepository lemmaRepository, PageRepository pageRepository,
                             IndexRepository indexRepository) throws IOException {
        this.lemmaRepository = lemmaRepository;
        this.pageRepository = pageRepository;
        this.indexRepository = indexRepository;
        lemmaService = new LemmaServiceImpl();
    }

    @Override
    public SearchingResult search(String query, Site site, int offset, int limit) {

        if (query.isEmpty()) {
            return new SearchingResultFail("Задан пустой поисковый запрос");
        }
        HashMap<String, Integer> lemmas = lemmaService.collectLemmas(query);
        TreeSet<Lemma> lemmaListSearchingFor = new TreeSet<>(Comparator.comparing(Lemma::getFrequency));

        List<Lemma> allLemmasInDB = lemmaRepository.findAll();

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            for (Lemma lemmaInDB : allLemmasInDB) {
                if (entry.getKey().equals(lemmaInDB.getLemma())) {
                    int lemmaFrequency = lemmaInDB.getFrequency();
                    double lemmaFrequencyToAllPages = 1.0 * lemmaFrequency / pageRepository.count();
                    boolean lemmaIsTooFrequent = lemmaFrequencyToAllPages > lemmaFrequencyLimit;
                    if (!lemmaIsTooFrequent) {
                        lemmaListSearchingFor.add(lemmaInDB);
                    }
                    break;
                }
            }
        }



        if (lemmaListSearchingFor.isEmpty()) {
            return new SearchingResultFail("По данному поисковому запросу ничего не найдено");
        }
        Lemma rarestLemma = lemmaListSearchingFor.first();

        if (indexRepository.count() == 0) {
            return new SearchingResultFail("Индексации не происходило");
        }

        List<Index> neededIndexes = indexRepository.findAllByLemma(rarestLemma);

        List<Page> pages = new ArrayList<>();

        for (Index index : neededIndexes) {
            pages.add(index.getPage());
        }

        float maxPageRelevance = 0;

        HashMap<Page, Float> pageToRelevanceMap = new HashMap<>();

        for (Page page : pages) {
            ArrayList<Index> indexesOnPage = indexRepository.findAllByPage(page);
            ArrayList<Lemma> lemmasOnPage = new ArrayList<>();
            for (Index index : indexesOnPage) {
                lemmasOnPage.add(index.getLemma());
            }

            float absPageRelevance = 0;
            for (Lemma lemmaFromQuery : lemmaListSearchingFor) {
                boolean thereIsLemmaInQueryOnPage = false;

                for (Lemma lemmaOnPage : lemmasOnPage) {

                    if (lemmaFromQuery.equals(lemmaOnPage)) {
                        thereIsLemmaInQueryOnPage = true;
                        absPageRelevance = absPageRelevance + indexRepository.findByLemmaAndPage(lemmaOnPage, page).get().getRank();
                        if (absPageRelevance > maxPageRelevance ) {
                            maxPageRelevance = absPageRelevance;
                        }
                        break;
                    }
                }
                if (!thereIsLemmaInQueryOnPage) {
                    pages.remove(page);
                }
            }
            float relativeRelevance = absPageRelevance / maxPageRelevance;
            pageToRelevanceMap.put(page, relativeRelevance);
        }


        if (pages.isEmpty()) {
            SearchingResultSuccess searchingResultSuccess = new SearchingResultSuccess();
            searchingResultSuccess.setResult(true);
            searchingResultSuccess.setCount(0);
            searchingResultSuccess.setData(new ArrayList<>());
            return searchingResultSuccess; //TODO: сниппеты, СДЕЛАТЬ ТО ЧТО ДЕЛАЕТ ОФСЕТ И ЛИМИТ И САЙТ
        }



        return getSearchingResultSuccess(pages, lemmaListSearchingFor, pageToRelevanceMap);
    }

    private SearchingResultSuccess getSearchingResultSuccess(List<Page> pages, TreeSet<Lemma> lemmaListSearchingFor, HashMap<Page, Float> pageToRelevanceMap) {
        ArrayList<SearchingData> searchingDataList = new ArrayList<>();

        for (Page page : pages) {
            SearchingPageInfo pageInfo = new SearchingPageInfo();
            pageInfo.setUri(page.getPath());
            try {
                pageInfo.setTitle(getPageTitle(getPageUrl(page)));
                pageInfo.setSnippet(getPageSnippet(getPageUrl(page), lemmaListSearchingFor));
                pageInfo.setRelevance(pageToRelevanceMap.get(page));

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            SearchingData searchingData = new SearchingData();

            searchingData.setSite(page.getSite().getUrl());
            searchingData.setSiteName(page.getSite().getName());
            searchingData.setUri(pageInfo.getUri());
            searchingData.setTitle(pageInfo.getTitle());
            searchingData.setSnippet(pageInfo.getSnippet());
            searchingData.setRelevance(pageInfo.getRelevance());

            searchingDataList.add(searchingData);
        }

        searchingDataList.sort(Comparator.comparing(SearchingData::getRelevance));
        Collections.reverse(searchingDataList);

        SearchingResultSuccess searchingResultSuccess = new SearchingResultSuccess();

        searchingResultSuccess.setCount(pages.size());
        searchingResultSuccess.setData(searchingDataList);
        return searchingResultSuccess;
    }

    private String getPageUrl(Page page) {
        searchengine.model.Site pageSite = page.getSite();
        String siteUrl = pageSite.getUrl();

        String pageUrl = siteUrl + page.getPath();
        return pageUrl;
    }

    private String getPageTitle(String url) throws IOException {
        return Jsoup.connect(url).get().title();
    }

    private String getPageSnippet(String url, TreeSet<Lemma> lemmas) {
        return null;
    }
}
