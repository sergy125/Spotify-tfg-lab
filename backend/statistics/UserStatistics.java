package statistics;

import database.MusicDatabase;
import database.MusicDatabase.TopPeriod;

import java.sql.SQLException;
import java.util.OptionalDouble;
import java.util.Optional;

public class UserStatistics {
    private final String userId;
    private final MusicDatabase database;
    private ListeningHistoryStatistics history;
    private TopTracksStatistics topTracks;
    private TopArtistsStatistics topArtists;

    public UserStatistics(String userId, MusicDatabase database) throws SQLException {
        this.userId = userId;
        this.database = database;
        refreshAll();
    }

    public final void refreshAll() throws SQLException {
        var newHistory = new ListeningHistoryStatistics(database.getListeningHistory(userId));
        var newTopTracks = new TopTracksStatistics(
                database.getTopTracks(userId, TopPeriod.SHORT_TERM),
                database.getTopTracks(userId, TopPeriod.MEDIUM_TERM),
                database.getTopTracks(userId, TopPeriod.LONG_TERM));
        var newTopArtists = new TopArtistsStatistics(
                database.getTopArtists(userId, TopPeriod.SHORT_TERM),
                database.getTopArtists(userId, TopPeriod.MEDIUM_TERM),
                database.getTopArtists(userId, TopPeriod.LONG_TERM));

        history = newHistory;
        topTracks = newTopTracks;
        topArtists = newTopArtists;
    }

    public OptionalDouble historySadness() {
        return history.averageSadness();
    }

    public Optional<String> closestTaylorSwiftEra() {
        return history.closestTaylorSwiftEra();
    }

    public OptionalDouble topTracksSadness(TopPeriod period) {
        return topTracks.averageSadness(period);
    }

    public ListeningHistoryStatistics getHistory() {
        return history;
    }

    public TopTracksStatistics getTopTracks() {
        return topTracks;
    }

    public TopArtistsStatistics getTopArtists() {
        return topArtists;
    }
}
