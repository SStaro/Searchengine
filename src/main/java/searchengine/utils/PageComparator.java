package searchengine.utils;

import searchengine.model.Page;

import java.util.Comparator;

public class PageComparator implements Comparator<Page> {

    @Override
    public int compare(Page p1, Page p2) {
        if (p1.getPath().length() != p2.getPath().length()) {
            return Integer.compare(p1.getPath().length(), p2.getPath().length());
        } else {
            return p1.getPath().compareTo(p2.getPath());
        }
    }
}
