import database.MusicDatabase;
import sync.SyncModule;

import java.sql.SQLException;

/** Entry point for the backend running as a service. */
public class ServerMain {
    public static void main(String[] args) throws SQLException {
        new SyncModule(new MusicDatabase()).run();
    }
}
