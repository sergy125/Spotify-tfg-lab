import audiofeatures.IAfeatures;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Comprueba el flujo completo con respuesta simulada, sin red. */
public class MainTest {
    public static void main(String[] args) throws Exception {
        Path input = Path.of("canciones.example.json");
        String original = Files.readString(input);
        String referenceOriginal = Files.readString(Path.of("json10.json"));
        int expectedCount = JsonParser.parseString(original).getAsJsonArray().size();
        int[] calls = {0};
        IAfeatures fake = new IAfeatures() {
            @Override protected String request(JsonObject body) {
                calls[0]++;
                String prompt = body.getAsJsonArray("contents").get(0).getAsJsonObject()
                        .getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString();
                JsonArray metadata = JsonParser.parseString(prompt.substring(prompt.indexOf("Songs: ") + 7)).getAsJsonArray();
                String referenceMarker = "Reference songs (json10.json): ";
                int referenceStart = prompt.indexOf(referenceMarker) + referenceMarker.length();
                JsonArray references = JsonParser.parseString(prompt.substring(referenceStart, prompt.indexOf('\n', referenceStart))).getAsJsonArray();
                if (!references.equals(JsonParser.parseString(referenceOriginal)))
                    throw new AssertionError("No se incluyen las diez referencias y comentarios originales");
                if (metadata.size() != expectedCount) throw new AssertionError("No se envían todas las canciones juntas");
                for (JsonElement element : metadata) {
                    if (element.getAsJsonObject().size() != 4)
                        throw new AssertionError("Se filtran features de referencia al prompt");
                    for (JsonElement reference : references)
                        if (element.getAsJsonObject().get("id").equals(reference.getAsJsonObject().get("id")))
                            throw new AssertionError("Una referencia se incluye entre los objetivos");
                }
                JsonArray response = JsonParser.parseString(original).getAsJsonArray();
                for (JsonElement element : response) {
                    JsonObject row = element.getAsJsonObject();
                    row.addProperty("energy", row.get("energy").getAsDouble() - 0.1);
                    row.add("popularity", row.get("popularidad"));
                }
                return response.toString();
            }
        };
        PrintStream previous = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream captured = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            Main.run(new String[0], fake);
        } finally {
            System.setOut(previous);
        }
        String report = buffer.toString(StandardCharsets.UTF_8);
        if (calls[0] != 1 || report.split("Error absoluto", -1).length - 1 != expectedCount
                || report.split("-0.10000000", -1).length - 1 != expectedCount
                || !report.contains("UNA BIMBO POR MADRID") || !report.contains("valence"))
            throw new AssertionError("Informe incompleto o diferencias incorrectas: " + report);
        if (!original.equals(Files.readString(input))) throw new AssertionError("Se modificó la referencia");
        System.out.println("PASS: " + expectedCount + " objetivos sin sus features, diez ejemplos con comentarios, diferencias y JSON original intacto.");
    }

}
