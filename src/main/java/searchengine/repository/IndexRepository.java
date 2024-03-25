package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {

    List<Index> findAllByLemma(Lemma lemma);
    ArrayList<Index> findAllByPage(Page page);

    Optional<Index> findByLemmaAndPage(Lemma lemma, Page page);

}
