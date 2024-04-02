package searchengine.utils;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.Site;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Status;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.impl.IndexingServiceImpl;
import searchengine.utils.lemmas.CollectionOfLemmasImpl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveAction;

public class PageProcessor extends RecursiveAction {

    private final Site rootSite;
    private final Site site;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private static Set<String> linkSet = new HashSet<>();


    public PageProcessor(Site rootSite, Site site, PageRepository pageRepository,
                         SiteRepository siteRepository, LemmaRepository lemmaRepository,
                         IndexRepository indexRepository) {
        this.rootSite = rootSite;
        this.site = site;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    @Override
    protected void compute() {

        searchengine.model.Site siteDB = null;
            for (searchengine.model.Site siteModel : siteRepository.findAll()) {
                if (siteModel.getStatus().equals(Status.FAILED)) {
                    continue;
                }
                if (siteModel.getUrl().equals(rootSite.getUrl())) {
                    siteDB = siteModel;
                    break;
                }
            }
        List<PageProcessor> tasks = new ArrayList<>();

        if (IndexingServiceImpl.isIndexingStopped()) {
            return;
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        
        linkSet.add(site.getUrl());
        Page page = new Page();
        String getHtml = setPageInfoAndGetHtmlText(page, siteDB);
        if (getHtml == null) {
            return;
        }
        
        setLemmasAndIndexes(getHtml, siteDB, page);
        siteDB.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteDB);
        forkJoinPageProcessor(tasks);
    }

    private void forkJoinPageProcessor(List<PageProcessor> tasks) {
        findAllChildSites(site.getUrl());

        if (site.getChildSites().isEmpty()) {
            return;
        }

        for (Site childSite : site.getChildSites()) {
            PageProcessor task = new PageProcessor(rootSite, childSite, pageRepository,
                    siteRepository, lemmaRepository, indexRepository);
            task.fork();
            tasks.add(task);
        }

        for (PageProcessor task : tasks) {
            task.join();
        }
    }

    private void setLemmasAndIndexes(String getHtml, searchengine.model.Site siteDB, Page page) {
        CollectionOfLemmasImpl collectionOfLemmasImpl = null;
        try {
            collectionOfLemmasImpl = new CollectionOfLemmasImpl();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        HashMap<String, Integer> lemmas = collectionOfLemmasImpl.collectLemmas(getHtml);

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            if (entry.getKey().isEmpty()) {
                continue;
            }
            Lemma newLemma = new Lemma();
            newLemma = setLemmaAndSaveItToTheRepository(siteDB, entry, newLemma);
            Index index = new Index();
            index.setPage(page);
            index.setLemma(newLemma);
            index.setRank(entry.getValue());
            indexRepository.save(index);
        }
    }

    private Lemma setLemmaAndSaveItToTheRepository(searchengine.model.Site siteDB, Map.Entry<String, Integer> entry, Lemma newLemma) {
        boolean lemmaInDB = false;
        for (Lemma lemma : lemmaRepository.findAll()) {
            if (lemma.getLemma().equalsIgnoreCase(entry.getKey()) && lemma.getSite().getUrl().equals(siteDB.getUrl())) {
                lemma.setFrequency(lemma.getFrequency() + 1);
                lemmaRepository.save(lemma);
                lemmaInDB = true;
                newLemma = lemma;
                break;
            }
        }
        if (!lemmaInDB) {
            newLemma.setSite(siteDB);
            newLemma.setLemma(entry.getKey());
            newLemma.setFrequency(1);
            lemmaRepository.save(newLemma);
        }
        return newLemma;
    }

    private String setPageInfoAndGetHtmlText(Page page, searchengine.model.Site siteDB) {
        int startOfPath = site.getUrl().indexOf(rootSite.getDomain()) + rootSite.getDomain().length();

        String path = site.getUrl().substring(startOfPath);

        page.setPath(path);
        page.setSite(siteDB);
        String getHtml;
        try {
            Jsoup.connect(site.getUrl()).execute();
            page.setCode(Jsoup.connect(site.getUrl()).execute().statusCode());
            getHtml = Jsoup.connect(site.getUrl()).get().html();
        } catch (HttpStatusException httpStatusException) {
            if (siteDB == null) {
                return null;
            }
            page.setCode(httpStatusException.getStatusCode());
            page.setContent(" ");
            pageRepository.save(page);
            return null;
        } catch (IOException exception) {
            exception.printStackTrace();
            return null;
        }

        page.setContent(getHtml);

        for (Page pageDB : pageRepository.findAll()) {
            if (pageDB.equals(page)) {
                return null;
            }
        }

        pageRepository.save(page);
        return getHtml;
    }

    private void findAllChildSites(String url) {
        Document doc;
        try {
            doc = Jsoup.connect(url).get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Elements links = doc.select("a");

        for (Element link : links) {
            String subUrl = link.attr("abs:href");
            addChildSiteBySubUrl(subUrl);
        }
    }

    private void addChildSiteBySubUrl(String subUrl) {
        if (subUrl.contains(rootSite.getDomain())
                && !subUrl.contains("#")) {
            boolean added = linkSet.add(subUrl);

            if (added) {
                site.addChildPage(subUrl);
            }
        }
    }
}
