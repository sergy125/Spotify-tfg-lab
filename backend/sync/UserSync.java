package sync;

import database.MusicDatabase;
import processor.SpotifyProcessor;
import spotify.SpotifyAuth;
import spotify.SpotifyClient;
import spotify.SpotifyLogin;

import java.io.IOException;
import java.sql.SQLException;

final class UserSync {
    private final SpotifyLogin login;
    private final SpotifyProcessor processor;

    UserSync(MusicDatabase database) {
        login = new SpotifyLogin(new SpotifyAuth(), database);
        processor = new SpotifyProcessor(database);
    }

    void syncHistory(String userId) throws IOException, SQLException, InterruptedException {
        processor.addHistory(clientFor(userId).getHistory(), userId);
    }

    void syncTops(String userId) throws IOException, SQLException, InterruptedException {
        SpotifyClient client = clientFor(userId);
        System.out.println("Actualizando el top de canciones a corto plazo de " + userId + "...");
        processor.addTopShort(client.getTopShort(), userId);
        System.out.println("Actualizando el top de canciones a medio plazo de " + userId + "...");
        processor.addTopMedium(client.getTopMedium(), userId);
        System.out.println("Actualizando el top de canciones a largo plazo de " + userId + "...");
        processor.addTopLong(client.getTopLong(), userId);
        System.out.println("Actualizando el top de artistas a corto plazo de " + userId + "...");
        processor.addArtistTopShort(client.getArtistTopShort(), userId);
        System.out.println("Actualizando el top de artistas a medio plazo de " + userId + "...");
        processor.addArtistTopMedium(client.getArtistTopMedium(), userId);
        System.out.println("Actualizando el top de artistas a largo plazo de " + userId + "...");
        processor.addArtistTopLong(client.getArtistTopLong(), userId);
    }

    private SpotifyClient clientFor(String userId) throws IOException, SQLException, InterruptedException {
        return new SpotifyClient(login.getValidUser(userId).getAccessToken());
    }
}
