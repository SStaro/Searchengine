package searchengine.config;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class Site {
    private String url;
    private String name;
    private String domain;

    private List<Site> sites;

    public Site() {

    }

    public Site(String url) {
        this.url = url;
    }

    public void addChildPages(String url) {
        sites.add(new Site(url));
    }
}
