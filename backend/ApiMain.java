import api.ApiModule;
import database.MusicDatabase;

public class ApiMain {
    public static void main(String[] args) throws Exception {
        new ApiModule(new MusicDatabase()).run();
    }
}
