import audiofeatures.IAfeatures;
import com.google.gson.*;
import models.Song;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Consulta Gemini directamente, sin Spotify, ReccoBeats ni base de datos. */
public class Main {
    private static final String[] FEATURES = {"acousticness", "danceability", "energy",
            "instrumentalness", "key", "liveness", "loudness", "mode", "speechiness", "tempo", "valence"};

    public static void main(String[] args) throws Exception {
        run(args, new IAfeatures());
    }

    static void run(String[] args, IAfeatures ia) throws Exception {
        if (args.length > 2 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("Uso: ./run.sh [canciones.json] [resultados.json]");
            return;
        }
        Path output = args.length == 2 ? Path.of(args[1]) : null;
        if (output != null && Files.exists(output)) throw new IllegalArgumentException("El archivo de salida ya existe: " + output);
        Path source = Path.of(args.length == 0 ? "canciones.example.json" : args[0]);
        JsonArray input = JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8)).getAsJsonArray();
        List<Song> songs = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement element : input) {
            JsonObject row = element.getAsJsonObject();
            String id = required(row, "id");
            if (!ids.add(id)) throw new IllegalArgumentException("ID duplicado: " + id);
            if (row.has("ai") && row.get("ai").getAsBoolean())
                throw new IllegalArgumentException("La referencia ya contiene estimaciones de IA: " + id);
            for (String feature : FEATURES) {
                JsonElement value = row.get(feature);
                if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                        || !Double.isFinite(value.getAsDouble()))
                    throw new IllegalArgumentException("Falta una feature de referencia válida: " + feature + " en " + id);
            }
            songs.add(new Song(id, required(row, row.has("nombre") ? "nombre" : "title"),
                    required(row, row.has("artistas") ? "artistas" : "artists"),
                    required(row, "album"), null, null, null, null));
        }
        if (songs.isEmpty()) throw new IllegalArgumentException("La lista de canciones está vacía.");
        ia.addFeatures(songs);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonArray results = new JsonArray();
        for (int i = 0; i < songs.size(); i++) {
            Song song = songs.get(i);
            JsonObject row = gson.toJsonTree(song).getAsJsonObject();
            printComparison(song, input.get(i).getAsJsonObject(), row);
            row.add("title", row.remove("nombre"));
            row.add("artists", row.remove("artistas"));
            row.add("popularity", row.remove("popularidad"));
            results.add(row);
        }
        if (output != null) {
            Files.writeString(output, gson.toJson(results) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            System.out.println("Resultado: " + output.toAbsolutePath());
        }
    }

    private static void printComparison(Song song, JsonObject reference, JsonObject estimate) {
        System.out.println("\n" + song.getNombre() + " — " + song.getArtistas());
        System.out.println("Diferencia = estimada - referencia; error absoluto = |diferencia|.");
        System.out.printf("%-18s %14s %14s %14s %14s%n",
                "Feature", "Referencia", "Estimada IA", "Diferencia", "Error absoluto");
        for (String feature : FEATURES) {
            double real = reference.get(feature).getAsDouble();
            double predicted = estimate.get(feature).getAsDouble();
            System.out.printf(Locale.ROOT, "%-18s %14.8f %14.8f %+14.8f %14.8f%n",
                    feature, real, predicted, predicted - real, Math.abs(predicted - real));
        }
        System.out.println("tempo: BPM; loudness: dB; key: código 0–11; mode: 0 menor / 1 mayor.");
        System.out.println("Las diferencias de key y mode comparan códigos, no distancias musicales.");
    }

    private static String required(JsonObject row, String field) {
        JsonElement value = row.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) throw new IllegalArgumentException("Falta un texto válido en: " + field);
        return value.getAsString();
    }
}
