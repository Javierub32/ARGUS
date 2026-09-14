package es.javierub.argus;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that the main ARGUS application context can be initialized.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:argus-context-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ArgusBackendApplicationTests {

    /** Verifies that the application loads without configuration errors. */
    @Test
    void contextLoads() {
    }

}
