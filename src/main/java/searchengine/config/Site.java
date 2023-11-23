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

    private List<Site> childSites = new ArrayList<>();

    public Site() {

    }

    public Site(String url) {
        this.url = url;
    }

    public void addChildPage(String url) {
        childSites.add(new Site(url));
    }
}
