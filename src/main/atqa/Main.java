package atqa;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class Main {

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        var fs = FullSystem.initialize().start();
        TheRegister.registerDomains(fs.webFramework);
    }


}
