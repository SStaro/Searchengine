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
    private final String domain;

    private static Set<String> linkSet = new HashSet<>();

    //Testing
    public static int count;


    public PageProcessor(Site site, String domain) {
        this.site = site;
        this.domain = domain;
        //Testing
        count++;
    }

    @Override
    protected Set<Page> compute() {
        Set<Page> pages = new TreeSet<>(new PageComparator());
        List<PageProcessor> tasks = new ArrayList<>();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }


        linkSet.add(site.getUrl());
        Page page = new Page();

        int startOfPath = site.getUrl().indexOf(domain) + domain.length();

        String path = site.getUrl().substring(startOfPath);

        page.setPath(path);
        try {
            page.setCode(Jsoup.connect(site.getUrl()).execute().statusCode());
            page.setContent(Jsoup.connect(site.getUrl()).get().html());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        pages.add(page);

        findAllChildSites(site.getUrl());


        if (site.getChildSites().isEmpty() || linkSet.size() >= 200) {
            return pages;
        }

        for (Site childSite : site.getChildSites()) {

            //Testing
            System.out.println(childSite.getUrl());

            PageProcessor task = new PageProcessor(childSite, domain);
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

            if (subUrl.contains(domain)
                    && !subUrl.contains("#")
                    && (subUrl.endsWith("html") || subUrl.endsWith("/"))) {


                boolean added = linkSet.add(subUrl);

                if (added) {
                    site.addChildPage(subUrl);
                }
            }
        }
    }
}
