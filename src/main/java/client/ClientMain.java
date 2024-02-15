package client;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import static java.lang.System.exit;

public class ClientMain {

    public static void main(String[] args){


        //fase di lettura e parsing file di config
        Properties props = setConfigs("./config/configClient.properties");


        // crea e avvia il client
        Client client = new Client(props);
        client.start();
    }

    private static Properties setConfigs(String path) {
        File configFile = new File(path);

        FileReader reader = null;
        Properties props = null;
        try {
            reader = new FileReader(configFile);
            props = new Properties();
            props.load(reader);
        } catch (FileNotFoundException e) {
            System.out.println("File di configurazione non trovato. Scaricare il file da ... e inserirlo nella cartella contenente il jar");
            exit(1); //unsuccessful termination
        } catch (IOException e) {
            throw new RuntimeException(e); //unsuccessful termination with Exception
        }

        return props;
    }


}
