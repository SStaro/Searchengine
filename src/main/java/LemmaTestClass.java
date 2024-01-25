import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.List;

public class LemmaTestClass {
    public static void main(String[] args) {
        LuceneMorphology luceneMorphology;

        {
            try {
                luceneMorphology = new RussianLuceneMorphology();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        List<String> wordBaseForms = luceneMorphology.getNormalForms("лесов");
        wordBaseForms.forEach(System.out::println);
    }
}
