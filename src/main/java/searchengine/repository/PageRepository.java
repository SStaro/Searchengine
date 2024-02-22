package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.ArrayList;
import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {

    ArrayList<Page> findAllBySite(Site site);

}
