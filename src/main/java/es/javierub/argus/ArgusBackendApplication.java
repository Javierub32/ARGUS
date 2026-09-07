package es.javierub.argus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import es.javierub.argus.dto.IndexedFile;

/**
 * Entry point of the ARGUS backend application.
 *
 * <p>The {@link SpringBootApplication} annotation enables Spring Boot
 * auto-configuration, component scanning, and application configuration.</p>
 */
@SpringBootApplication
@RegisterReflectionForBinding(IndexedFile.class)
public class ArgusBackendApplication {

    /**
     * Starts the Spring Boot application context.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ArgusBackendApplication.class, args);
    }

}
