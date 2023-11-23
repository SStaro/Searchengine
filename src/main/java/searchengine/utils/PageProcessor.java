package searchengine.utils;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.Site;
import searchengine.model.Page;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.RecursiveTask;

@Slf4j
public class PageProcessor extends RecursiveTask<Set<Page>> {

    private final Site site;

    private static Set<String> linkSet = new HashSet<>();

    //Testing
    public static int count;


    public PageProcessor(Site site) {
        this.site = site;
        //Testing
        count++;
    }

    @Override
    protected Set<Page> compute() {
        Set<Page> pages = new HashSet<>();
        List<PageProcessor> tasks = new ArrayList<>();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }


        linkSet.add(site.getUrl());
        Page page = new Page();
        page.setPath(site.getUrl());
        try {
            page.setCode(Jsoup.connect(site.getUrl()).execute().statusCode());
            page.setContent(Jsoup.connect(site.getUrl()).get().html());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        pages.add(page);

        findAllChildSites(site.getUrl());
        if (site.getChildSites().isEmpty()) {
            return pages;
        }

        for (Site childSite : site.getChildSites()) {

            //Testing
            System.out.println(childSite.getUrl());

            PageProcessor task = new PageProcessor(childSite);
            task.fork();
            tasks.add(task);

        }

        //Testing
        System.out.println(PageProcessor.count);

        for (PageProcessor task : tasks) {
            //Testing
            System.out.println(task);

            pages.addAll(task.join());
        }
        return pages;
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

            if (!subUrl.contains("metanit.com") || !subUrl.endsWith("/")) {
                continue;
            }

            boolean added = linkSet.add(subUrl);

            if (added) {
                site.addChildPage(subUrl);
            }
        }
    }
}
