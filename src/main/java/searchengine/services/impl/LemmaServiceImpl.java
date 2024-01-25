package searchengine.services.impl;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.dto.indexing.IndexingResult;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.services.LemmaService;
import org.jsoup.safety.*;

import java.io.IOException;
import java.util.*;

@Service
public class LemmaServiceImpl implements LemmaService {

    private final LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
    private static final String WORD_TYPE_REGEX = "\\W\\w&&[^а-яА-Я\\s]";
    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;

    public LemmaServiceImpl(LemmaRepository lemmaRepository, PageRepository pageRepository) throws IOException {
        this.lemmaRepository = lemmaRepository;
        this.pageRepository = pageRepository;
    }

    @Override
    public HashMap<String, Integer> collectLemmas(String htmlText) {
        String text = textWithoutHtmlTags(htmlText);
        String[] words = arrayContainsRussianWords(text);
        HashMap<String, Integer> lemmas = new HashMap<>();

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
            if (anyWordBaseBelongToParticle(wordBaseForms)) {
                continue;
            }


            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if (normalForms.isEmpty()) {
                continue;
            }

            String normalWord = normalForms.get(0);

            if (lemmas.containsKey(normalWord)) {
                lemmas.put(normalWord, lemmas.get(normalWord) +1);
            } else {
                lemmas.put(normalWord, 1);
            }
        }
        return lemmas;
    }

    @Override
    public String textWithoutHtmlTags(String htmlText) {
        return Jsoup.clean(htmlText, Safelist.none());
    }

    public Set<String> getLemmaSet(String text) {
        String[] textArray = arrayContainsRussianWords(text);
        Set<String> lemmaSet = new HashSet<>();
        for (String word : textArray) {
            if (!word.isEmpty() && isCorrectWordForm(word)) {
                List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
                if (anyWordBaseBelongToParticle(wordBaseForms)) {
                    continue;
                }
                lemmaSet.addAll(luceneMorphology.getNormalForms(word));
            }
        }
        return lemmaSet;
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(this::hasParticleProperty);
    }

    private boolean hasParticleProperty(String wordBase) {
        for (String property : particlesNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private String[] arrayContainsRussianWords(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("([^а-я\\s])", " ")
                .trim()
                .split("\\s+");
    }

    private boolean isCorrectWordForm(String word) {
        List<String> wordInfo = luceneMorphology.getMorphInfo(word);
        for (String morphInfo : wordInfo) {
            if (morphInfo.matches(WORD_TYPE_REGEX)) {
                return false;
            }
        }
        return true;
    }

}
