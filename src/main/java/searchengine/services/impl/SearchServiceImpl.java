package searchengine.services.impl;

import org.jsoup.Jsoup;
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
import searchengine.utils.lemmas.CollectionOfLemmasImpl;
import searchengine.utils.SearchingPageInfo;

import java.io.IOException;
import java.util.*;

@Service
public class SearchServiceImpl implements SearchService {

    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;
    private final CollectionOfLemmasImpl collectionOfLemmas;
    private final float lemmaFrequencyLimit = 1f;
    private final int amountOfSymbolsBeforeQueryLemma = 100;
    private final int amountOfSymbolsAfterQueryLemma = 100;
    private boolean canFindAllQueryWords = true;

    public SearchServiceImpl(LemmaRepository lemmaRepository, PageRepository pageRepository,
                             IndexRepository indexRepository, SiteRepository siteRepository) throws IOException {
        this.lemmaRepository = lemmaRepository;
        this.pageRepository = pageRepository;
        this.indexRepository = indexRepository;
        this.siteRepository = siteRepository;
        collectionOfLemmas = new CollectionOfLemmasImpl();
    }

    @Override
    public SearchingResult search(String query, String selectedSiteToSearchIn, int offset, int limit) {
        canFindAllQueryWords = true;
        if (query.isEmpty()) {
            return new SearchingResultFail("Задан пустой поисковый запрос");
        }
        HashMap<String, Integer> lemmas = collectionOfLemmas.collectLemmas(query);
        TreeSet<Lemma> lemmaListSearchingFor = new TreeSet<>(Comparator.comparing(Lemma::getFrequency));
        List<Lemma> allLemmasInDB = new ArrayList<>();
        Site siteToSearchIn = new Site();

        boolean allQueryWordsWereFoundForSelectedSite = true;
        if (selectedSiteToSearchIn == null) {
            for (Site site : siteRepository.findAll()) {
                List<Lemma> siteLemmasInDB = lemmaRepository.findAllBySite(site);
                if (lemmasInDBContainsQueryLemmas(lemmas, siteLemmasInDB, lemmaListSearchingFor)) {
                    allLemmasInDB.addAll(siteLemmasInDB);
                }
            }
        } else {
            siteToSearchIn = siteRepository.findByUrl(selectedSiteToSearchIn).get();
            allLemmasInDB = lemmaRepository.findAllBySite(siteToSearchIn);
            allQueryWordsWereFoundForSelectedSite = lemmasInDBContainsQueryLemmas(lemmas, allLemmasInDB, lemmaListSearchingFor);
        }

        if (lemmaListSearchingFor.isEmpty() || !allQueryWordsWereFoundForSelectedSite) {
            return new SearchingResultFail("По данному поисковому запросу ничего не найдено");
        }
        Lemma rarestLemma = lemmaListSearchingFor.first();
        List<Lemma> lemmasWithRarestStringLemma = lemmaRepository.findAllByLemma(rarestLemma.getLemma());
        if (indexRepository.count() == 0) {
            return new SearchingResultFail("Индексации не происходило");
        }
        List<Page> pages = new ArrayList<>();
        addNeededPages(selectedSiteToSearchIn, lemmasWithRarestStringLemma, siteToSearchIn, pages);
        float maxPageRelevance = 0;
        HashMap<Page, Float> pageToRelevanceMap = new HashMap<>();
        setRelevanceToPages(pages, lemmaListSearchingFor, maxPageRelevance, pageToRelevanceMap);
        if (pages.isEmpty()) {
            SearchingResultSuccess searchingResultSuccess = new SearchingResultSuccess();
            searchingResultSuccess.setCount(0);
            searchingResultSuccess.setData(new ArrayList<>());
            return searchingResultSuccess;
        }
        return getSearchingResultSuccess(pages, pageToRelevanceMap, query, limit, offset);
    }


    private void addNeededPages(String selectedSiteToSearchIn, List<Lemma> lemmasWithRarestStringLemma, Site siteToSearchIn, List<Page> pages) {
        for (Lemma lemmaWithRarestString : lemmasWithRarestStringLemma) {
            List<Index> neededIndexes = indexRepository.findAllByLemma(lemmaWithRarestString);
            for (Index index : neededIndexes) {
                boolean siteToSearchInEqualsTheSiteWeGotWithIndex = index.getPage().getSite().equals(siteToSearchIn);
                if (!(selectedSiteToSearchIn == null) && !siteToSearchInEqualsTheSiteWeGotWithIndex) {
                    continue;
                }
                pages.add(index.getPage());
            }
        }
    }

    private void setRelevanceToPages(List<Page> pages, TreeSet<Lemma> lemmaListSearchingFor, float maxPageRelevance, HashMap<Page, Float> pageToRelevanceMap) {
        for (Page page : pages) {
            ArrayList<Index> indexesOnPage = indexRepository.findAllByPage(page);
            ArrayList<Lemma> lemmasOnPage = new ArrayList<>();
            for (Index index : indexesOnPage) {
                lemmasOnPage.add(index.getLemma());
            }

            float relativeRelevance = getRelativeRelevance(lemmaListSearchingFor, lemmasOnPage, maxPageRelevance, page);
            pageToRelevanceMap.put(page, relativeRelevance);
        }
    }

    private float getRelativeRelevance(TreeSet<Lemma> lemmaListSearchingFor, ArrayList<Lemma> lemmasOnPage, float maxPageRelevance, Page page) {
        float absPageRelevance = 0;
        for (Lemma lemmaFromQuery : lemmaListSearchingFor) {
            for (Lemma lemmaOnPage : lemmasOnPage) {
                if (lemmaFromQuery.equals(lemmaOnPage)) {
                    absPageRelevance = absPageRelevance + indexRepository.findByLemmaAndPage(lemmaOnPage, page).get().getRank();
                    if (absPageRelevance > maxPageRelevance) {
                        maxPageRelevance = absPageRelevance;
                    }
                    break;
                }
            }
        }
        float relativeRelevance = absPageRelevance / maxPageRelevance;
        return relativeRelevance;
    }

    private SearchingResultSuccess getSearchingResultSuccess(List<Page> pages, HashMap<Page, Float> pageToRelevanceMap, String query, int limit, int offset) {
        ArrayList<SearchingData> searchingDataList = new ArrayList<>();

        setSearchingData(pages, pageToRelevanceMap, query, searchingDataList);

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

    private void setSearchingData(List<Page> pages, HashMap<Page, Float> pageToRelevanceMap, String query, ArrayList<SearchingData> searchingDataList) {
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
        result = setSnippet(url, query, queryWords, snippetBuilder, result, endOfLemma);
        if (!endOfTheSnippetIsTheEndOfTheText) {
            result = result + "...";
        }

        return result;
    }

    private String setSnippet(String url, String query, String[] queryWords, StringBuilder snippetBuilder, String result, int endOfLemma) {
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
            } else if (!snippetTextLowerCase.contains(queryWords[0])) {
                break;
            } else {
                result = getPageSnippet(url, query, endOfLemma);
                break;
            }
        }
        return result;
    }

    private boolean lemmasInDBContainsQueryLemmas(HashMap<String, Integer> lemmas, List<Lemma> allLemmasInDB, TreeSet<Lemma> lemmaListSearchingFor) {
        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            boolean siteContainsQueryLemma = false;
            siteContainsQueryLemma = isSiteContainsQueryLemma(allLemmasInDB, lemmaListSearchingFor, entry, siteContainsQueryLemma);
            if (!siteContainsQueryLemma) {
                return false;
            }
        }
        return true;
    }

    private boolean isSiteContainsQueryLemma(List<Lemma> allLemmasInDB, TreeSet<Lemma> lemmaListSearchingFor, Map.Entry<String, Integer> entry, boolean siteContainsQueryLemma) {
        for (Lemma lemmaInDB : allLemmasInDB) {
            if (entry.getKey().equals(lemmaInDB.getLemma())) {
                int lemmaFrequency = lemmaInDB.getFrequency();
                double lemmaFrequencyToAllPages = 1.0 * lemmaFrequency / pageRepository.count();
                boolean lemmaIsTooFrequent = lemmaFrequencyToAllPages > lemmaFrequencyLimit;
                siteContainsQueryLemma = true;
                if (!lemmaIsTooFrequent) {
                    lemmaListSearchingFor.add(lemmaInDB);
                }
                break;
            }
        }
        return siteContainsQueryLemma;
    }
}
