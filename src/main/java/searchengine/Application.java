package searchengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    //TODO: Index by column 'path' in the table "Page" --- done. Created index but didn't change to 'TEXT'. Ended up with 'VARCHAR(255)'.
    //TODO: ManyToOne and ManyToMany annotations

    //TODO: StopIndexing marks Status as indexed or failed --- done!
    //TODO: Pages in db sorted by siteID first then path.length;
    //TODO: if stopIndexing -> immediately stop program --- done!
    //TODO: setTime in Site everytime page has indexed;

}
