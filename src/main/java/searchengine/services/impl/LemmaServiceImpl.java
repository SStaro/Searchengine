package searchengine.services.impl;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.services.LemmaService;
import org.jsoup.safety.*;

import java.io.IOException;
import java.util.*;

@Service
public class LemmaServiceImpl implements LemmaService {

    private final LuceneMorphology russianLuceneMorphology = new RussianLuceneMorphology();
    private final LuceneMorphology englishLuceneMorphology = new EnglishLuceneMorphology();
    private static final String[] particlesNamesRus = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};
    private static final String[] particlesNamesEng = new String[]{"INT", "PREP", "CONJ"};



    public LemmaServiceImpl() throws IOException {

    }

    @Override
    public HashMap<String, Integer> collectLemmas(String htmlText) {
        String text = textWithoutHtmlTags(htmlText);
        String[] words = arrayContainsRussianOrEnglishWords(text);
        HashMap<String, Integer> lemmas = new HashMap<>();

        String rusRegex = "[^a-z]+";
        String engRegex = "[a-z]+";

        for (String word : words) {
            String normalWord = "";

            if (word.isBlank()) {
                continue;
            }

            if (word.matches(rusRegex)) {

                List<String> wordBaseForms = russianLuceneMorphology.getMorphInfo(word);
                if (anyWordBaseBelongToParticle(wordBaseForms)) {
                    continue;
                }


                List<String> normalForms = russianLuceneMorphology.getNormalForms(word);
                if (normalForms.isEmpty()) {
                    continue;
                }
                normalWord = normalForms.get(0);
            } if (word.matches(engRegex)) {
                List<String> wordBaseForms = englishLuceneMorphology.getMorphInfo(word);
                if (anyWordBaseBelongToParticle(wordBaseForms)) {
                    continue;
                }


                List<String> normalForms = englishLuceneMorphology.getNormalForms(word);
                if (normalForms.isEmpty()) {
                    continue;
                }
                normalWord = normalForms.get(0);
            }



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

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(this::hasRusParticleProperty) || wordBaseForms.stream().anyMatch(this::hasEngParticleProperty);
    }

    private boolean hasRusParticleProperty(String wordBase) {
        for (String property : particlesNamesRus) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEngParticleProperty(String wordBase) {
        for (String property : particlesNamesEng) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private String[] arrayContainsRussianOrEnglishWords(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("([^а-яa-z\\s])", " ")
                .trim()
                .split("\\s+");
    }
}
