package net.mysterria.backend;

import net.mysterria.backend.config.TestContainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestContainersConfig.class)
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void cleanDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("TRUNCATE TABLE purchases CASCADE");
            statement.execute("TRUNCATE TABLE transactions CASCADE");
            statement.execute("TRUNCATE TABLE votes CASCADE");
            statement.execute("TRUNCATE TABLE news CASCADE");
            statement.execute("TRUNCATE TABLE service_localizations CASCADE");
            statement.execute("TRUNCATE TABLE services CASCADE");
            statement.execute("TRUNCATE TABLE service_categories CASCADE");
            statement.execute("TRUNCATE TABLE refresh_tokens CASCADE");
            statement.execute("TRUNCATE TABLE users CASCADE");
            statement.execute("TRUNCATE TABLE punishments CASCADE");
            statement.execute("TRUNCATE TABLE audit_logs CASCADE");
        }
    }
}
