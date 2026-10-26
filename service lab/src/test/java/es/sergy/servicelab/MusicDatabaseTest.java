package es.sergy.servicelab;

import es.sergy.servicelab.database.MusicDatabase;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class MusicDatabaseTest {
    @TempDir Path directory;

    @Test
    void createsTableAndInsertsOrUpdatesWithoutDuplicatingOtherAccounts() {
        var url = "jdbc:sqlite:" + directory.resolve("users.db");
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url));
        var database = new MusicDatabase(jdbc);
        database.createUsersTable();
        database.saveUser("one", "Original");
        database.saveUser("two", null);
        database.createUsersTable();
        database.saveUser("one", "Updated");
        // Una conexión nueva sigue viendo los datos persistidos en el archivo.
        var reopened = new JdbcTemplate(new DriverManagerDataSource(url));
        assertThat(reopened.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(2);
        assertThat(reopened.queryForObject("SELECT display_name FROM users WHERE spotify_id = 'one'", String.class))
                .isEqualTo("Updated");
        assertThat(reopened.queryForObject("SELECT updated_at FROM users WHERE spotify_id = 'one'", String.class))
                .isNotBlank();
    }
}
