package searchengine.config;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.RecursiveTask;

@Slf4j
public class PageProcessor extends RecursiveTask<Set<String>> {

    private final String url;

    //Testing
    public static int count;


    public PageProcessor(String url) {
        this.url = url;
        //Testing
        count++;
    }

    @Override
    protected Set<String> compute() {
        Set<String> linkSet = new HashSet<>();
        List<PageProcessor> tasks = new ArrayList<>();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }


        linkSet.add(url);

        List<String> links = getLinksFromUrl(url);

        if (links.isEmpty()) {
            return linkSet;
        }

        for (String link : links) {

            //Testing
            if (!link.contains("metanit.com")) {
                continue;
            }

            //Testing
            System.out.println(link);

            PageProcessor subProcessor = new PageProcessor(link);
            subProcessor.fork();
            tasks.add(subProcessor);

        }
        //Testing
        System.out.println(PageProcessor.count);

        for (PageProcessor task : tasks) {
            //Testing
            System.out.println(task);

            linkSet.addAll(task.join());
        }
        return linkSet;
    }

    private List<String> getLinksFromUrl(String url) {

        Document doc;
        try {
            doc = Jsoup.connect(url).get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Elements links = doc.select("a");

        List<String> urls = new ArrayList<>();

        for (Element link : links) {
            String subUrl = link.attr("abs:href");

            urls.add(subUrl);
        }
        return urls;
    }
}
