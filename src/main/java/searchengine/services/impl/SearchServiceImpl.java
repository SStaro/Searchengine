package searchengine.services.impl;

import org.jsoup.Jsoup;
import org.springframework.http.converter.json.GsonBuilderUtils;
import org.springframework.stereotype.Service;
import searchengine.dto.searching.*;
import searchengine.dto.searching.results.SearchingResult;
import searchengine.dto.searching.results.SearchingResultFail;
import searchengine.dto.searching.results.SearchingResultSuccess;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.SearchService;
import searchengine.utils.SearchingPageInfo;

import java.io.IOException;
import java.util.*;

@Service
public class SearchServiceImpl implements SearchService {

    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;
    private final LemmaServiceImpl lemmaService;
    private final float lemmaFrequencyLimit = 0.9f;
    private final int amountOfSymbolsBeforeQueryLemma = 100;
    private final int amountOfSymbolsAfterQueryLemma = 100;
    private boolean canFindAllQueryWords = true;

    public SearchServiceImpl(LemmaRepository lemmaRepository, PageRepository pageRepository,
                             IndexRepository indexRepository, SiteRepository siteRepository) throws IOException {
        this.lemmaRepository = lemmaRepository;
        this.pageRepository = pageRepository;
        this.indexRepository = indexRepository;
        this.siteRepository = siteRepository;
        lemmaService = new LemmaServiceImpl();
    }

    @Override
    public SearchingResult search(String query, String selectedSiteToSearchIn, int offset, int limit) {
        canFindAllQueryWords = true;
        if (query.isEmpty()) {
            return new SearchingResultFail("Задан пустой поисковый запрос");
        }
        HashMap<String, Integer> lemmas = lemmaService.collectLemmas(query);
        TreeSet<Lemma> lemmaListSearchingFor = new TreeSet<>(Comparator.comparing(Lemma::getFrequency));

        List<Lemma> allLemmasInDB;
        Site siteToSearchIn = new Site();
        if (selectedSiteToSearchIn == null) {
            allLemmasInDB = lemmaRepository.findAll();
        } else {
            siteToSearchIn = siteRepository.findByUrl(selectedSiteToSearchIn).get();
            allLemmasInDB = lemmaRepository.findAllBySite(siteToSearchIn);
        }
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
            boolean siteToSearchInEqualsTheSiteWeGotWithIndex = index.getPage().getSite().equals(siteToSearchIn);
            if (!(selectedSiteToSearchIn == null) && !siteToSearchInEqualsTheSiteWeGotWithIndex) {
                continue;
            }
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
            searchingResultSuccess.setCount(0);
            searchingResultSuccess.setData(new ArrayList<>());
            return searchingResultSuccess;
        }

        return getSearchingResultSuccess(pages, pageToRelevanceMap, query, limit, offset);
    }

    private SearchingResultSuccess getSearchingResultSuccess(List<Page> pages, HashMap<Page, Float> pageToRelevanceMap, String query, int limit, int offset) {
        ArrayList<SearchingData> searchingDataList = new ArrayList<>();

        for (Page page : pages) {
            SearchingPageInfo pageInfo = new SearchingPageInfo();
            pageInfo.setUri(page.getPath());
            try {
                pageInfo.setTitle(getPageTitle(getPageUrl(page)));
                pageInfo.setSnippet(getPageSnippet(getPageUrl(page), query, 0));
                if (!canFindAllQueryWords) {
                    continue;
                }
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

        List<SearchingData> searchingDataArrayListWithOffsetAndLimit = searchingDataList;

        if (offset > 0) {
            searchingDataArrayListWithOffsetAndLimit = searchingDataList.subList(offset, searchingDataList.size());
        }
        if (searchingDataArrayListWithOffsetAndLimit.size() > limit) {
            searchingDataArrayListWithOffsetAndLimit = searchingDataArrayListWithOffsetAndLimit.subList(0, limit);
        }

        SearchingResultSuccess searchingResultSuccess = new SearchingResultSuccess();

        searchingResultSuccess.setCount(searchingDataList.size());
        searchingResultSuccess.setData(searchingDataArrayListWithOffsetAndLimit);
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

    private String getPageSnippet(String url, String query, int indexOfStartSearching) {

        String[] queryWords = query.split(" ");

        StringBuilder siteText = new StringBuilder();
        StringBuilder snippetBuilder = new StringBuilder();
        try {
            siteText.append(Jsoup.connect(url).get().head().text());
            siteText.append(Jsoup.connect(url).get().body().text());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        String siteTextLowerCase = siteText.toString().toLowerCase();

        int startOfLemma = siteTextLowerCase.indexOf(queryWords[0].toLowerCase(), indexOfStartSearching);
        int endOfLemma = startOfLemma + queryWords[0].length();

        boolean startOfTheSnippetIsTheStartOfTheText = false;
        boolean endOfTheSnippetIsTheEndOfTheText = false;

        int startOfSnippet = startOfLemma - amountOfSymbolsBeforeQueryLemma;
        if (startOfSnippet < 0) {
            startOfSnippet = 0;
        } if (startOfSnippet == 0) {
            startOfTheSnippetIsTheStartOfTheText = true;
        }
        int endOfSnippet = endOfLemma + amountOfSymbolsAfterQueryLemma;
        if (endOfSnippet < 0) {
            endOfSnippet = 0;
        } if (endOfSnippet == siteText.length() - 1) {
            endOfTheSnippetIsTheEndOfTheText = true;
            endOfSnippet = siteText.length() -1;
        }


        if (!startOfTheSnippetIsTheStartOfTheText) {
            snippetBuilder.append("...");
        }


        snippetBuilder.append(siteText.substring(startOfSnippet, endOfSnippet));
        String result = "";
        for (String queryWord : queryWords) {
            canFindAllQueryWords = false;
            String snippetTextLowerCase = snippetBuilder.toString().toLowerCase();
            if (snippetTextLowerCase.contains(queryWord.toLowerCase())) {
                int queryWordStartIndex = snippetTextLowerCase.indexOf(queryWord.toLowerCase());
                int lemmaEndIndex = queryWordStartIndex + queryWord.length();


                String queryWordIgnoreCase = snippetBuilder.substring(queryWordStartIndex, lemmaEndIndex);

                snippetBuilder.replace(queryWordStartIndex, lemmaEndIndex, "<b>" + queryWordIgnoreCase + "</b>");
                canFindAllQueryWords = true;

                result = snippetBuilder.toString();
            } else {

                result = getPageSnippet(url, query, endOfLemma);
                break;
            }
        }

        if (!endOfTheSnippetIsTheEndOfTheText) {
            result = result + "...";
        }


        return result;
    }
}
