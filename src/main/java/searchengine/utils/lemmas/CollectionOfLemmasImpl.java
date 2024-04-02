package searchengine.utils.lemmas;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.jsoup.safety.*;

import java.io.IOException;
import java.util.*;

@Component
public class CollectionOfLemmasImpl implements CollectionOfLemmas {

    private final LuceneMorphology russianLuceneMorphology = new RussianLuceneMorphology();
    private final LuceneMorphology englishLuceneMorphology = new EnglishLuceneMorphology();
    private static final String[] particlesNamesRus = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ"};
    private static final String[] particlesNamesEng = new String[]{"INT", "PREP", "CONJ"};



    public CollectionOfLemmasImpl() throws IOException {

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
                normalWord = getNormalWord(word, russianLuceneMorphology);
            }

            if (word.matches(engRegex)) {
                normalWord = getNormalWord(word, englishLuceneMorphology);
            }
            if (lemmas.containsKey(normalWord)) {
                lemmas.put(normalWord, lemmas.get(normalWord) +1);
            } else {
                lemmas.put(normalWord, 1);
            }
        }
        return lemmas;
    }

    private String getNormalWord(String word, LuceneMorphology luceneMorphology) {
        List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
        List<String> normalForms = luceneMorphology.getNormalForms(word);
        if (anyWordBaseBelongToParticle(wordBaseForms) || normalForms.isEmpty()) {
            return "";
        } else {
            return normalForms.get(0);
        }
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
