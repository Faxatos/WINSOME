package server;

import java.io.*;
import java.util.Properties;

import static java.lang.System.exit;

public class ServerMain {


    public static void main(String[] args){


        //implementata fase di lettura e parsing file di config
        Properties props = setConfigs("./config/configServer.properties");
        int BUFF_SIZE = Integer.parseInt(props.getProperty("BUFFLEN", "1024"));

        if(BUFF_SIZE < 1024){ //valore minimo del buffer impostato indipendentemente dal file di configurazione
            BUFF_SIZE = 1024;
        }
        if(BUFF_SIZE > 8192){ //valore massimo del buffer impostato indipendentemente dal file di configurazione
            BUFF_SIZE = 8192;
        }


        SocialNetwork socialNetwork = new SocialNetwork(props, BUFF_SIZE);
        // crea e avvia il server
        Server server = new Server(props, socialNetwork, BUFF_SIZE);
        server.start();

        //salvo stato del server dopo la sua chiusura
        socialNetwork.uploadDB();
    }

    private static Properties setConfigs(String path) {
        File configFile = new File(path.trim());

        FileReader reader = null;
        Properties props = null;
        try {
            reader = new FileReader(configFile);
            props = new Properties();
            props.load(reader);
        } catch (FileNotFoundException e) {
            System.out.println("File di configurazione non trovato. Crearlo nella cartella di esecuzione del jar.");
            exit(1); //unsuccessful termination
        } catch (IOException e) {
            throw new RuntimeException(e); //unsuccessful termination with Exception
        }

        return props;
    }
}
