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

}
