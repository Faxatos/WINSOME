package client;

import client.RMI.RMIClient;
import client.RMI.RMIImplementation;
import client.notification.ClientNotificationPrinter;
import client.notification.ResponseFormatter;
import server.RMI.RMIServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static java.lang.System.exit;

public class Client {

    private final BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
    private int BUFFER_SIZE;
    private int MUTLICAST_BUFF_SIZE;
    private final int tcp_port;
    private final String server_adrr;
    private boolean logged;

    private String username;
    private String password;

    private RMIImplementation followers;
    private int show_elements = 25; //default value
    private boolean is_followers_exported;

    private RMIServer stubServer;
    private RMIClient callback;
    private Thread mc_thread;
    public Client(Properties props){
        this.BUFFER_SIZE = Integer.parseInt(props.getProperty("BUFF_LEN", "1024"));

        if(this.BUFFER_SIZE < 1024){ //valore minimo del buffer impostato indipendentemente dal file di configurazione
            this.BUFFER_SIZE = 1024;
        }
        if(this.BUFFER_SIZE > 8192){ //valore massimo del buffer impostato indipendentemente dal file di configurazione
            this.BUFFER_SIZE = 8192;
        }

        this.MUTLICAST_BUFF_SIZE = Integer.parseInt(props.getProperty("MULTICAST_BUFF_LEN", "256"));

        if(MUTLICAST_BUFF_SIZE < 64){ //valore minimo del buffer impostato indipendentemente dal file di configurazione
            MUTLICAST_BUFF_SIZE = 64;
        }
        if(MUTLICAST_BUFF_SIZE > 512){ //valore massimo del buffer impostato indipendentemente dal file di configurazione
            MUTLICAST_BUFF_SIZE = 512;
        }

        this.is_followers_exported = false;

        this.tcp_port = Integer.parseInt(props.getProperty("TCPPORT", "6000"));
        this.server_adrr = props.getProperty("SERVER", "127.0.0.1");
        try {
            int registry_port = Integer.parseInt(props.getProperty("REGPORT", "7000"));
            Registry registry = LocateRegistry.getRegistry(server_adrr, registry_port);
            stubServer = (RMIServer) registry.lookup(props.getProperty("ROSNAME", "winsomeStub"));
        } catch (RemoteException | NotBoundException e) {
            throw new RuntimeException(e);
        }

        this.logged = false;
    }

    public void start(){

        boolean exit = false;
        //controllo se loggato: se non, ho login/reg handle routines
        //se loggato, lascio flow di messaggi con formattatore per scrolling (ResponseFormatter)

        try (SocketChannel client = SocketChannel.open(new InetSocketAddress(server_adrr, tcp_port)); )
        {

            ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
            ByteBuffer reply = ByteBuffer.allocate(BUFFER_SIZE);
            ByteBuffer[] bfs = new ByteBuffer[]{length, reply};

            while (!exit) {

                //rimuovo spazi bianchi prima e dopo stringa
                String msg = consoleReader.readLine().trim();


                String EXIT_CMD = "exit";
                if (msg.equals(EXIT_CMD) && !logged){ //
                    exit = true;
                    continue;
                }
                else if(msg.equals(EXIT_CMD) && logged){ //se provo ad uscire mentre sono ancora loggato
                    msg = "help";
                }

                String[] parts = msg.split(" ", 4); //divido messaggio per switch case
                String response = null;

                //ad ogni iterazione mando una richiesta (msg), che sarà smistata con switch case al caso interessato, e che quindi riceverà
                //risposta adeguata in base alla richiesta. alla fine di ogni iterazione, c'è un print della stringa response

                boolean exit_iteration = false;
                if(logged){
                    switch (parts[0]){
                        case"logout":
                            response = handleLogout(client, length, msg, bfs);
                            break;
                        case"list":
                            if((parts[1].equals("users") || parts[1].equals("following")) && parts.length == 2){ //controllo correttezza input
                                response = handleScrolling(msg, "list", client, length, bfs, String.format("  %-25s |  %-25s \n", "Utente", "Tags"));
                            }
                            else if(parts[1].equals("followers") && parts.length == 2){ //controllo correttezza input
                                response = handleListFollowers(show_elements);
                            }
                            else{
                                switch (parts[1]) { //altrimenti chiedo a server aiuto per il comando in questione
                                    case "users":
                                        handleWrite(client, length, "help list users");
                                        break;
                                    case "following":
                                        handleWrite(client, length, "help list following");
                                        break;
                                    case "followers":
                                        handleWrite(client, length, "help list followers");
                                        break;
                                    default:
                                        handleWrite(client, length, "help");
                                        break;
                                }
                                response = handleRead(client, bfs);
                            }
                            break;
                        case"show":
                            if(parts[1].equals("post") && parts.length == 3){ //controllo correttezza input

                                response = handleScrolling(msg, "post", client, length, bfs, "");
                            }
                            else if(parts[1].equals("feed") && parts.length == 2){ //controllo correttezza input
                                response = handleScrolling(msg, "feed", client, length, bfs, String.format("  %-10s | %-25s | %-25s", "ID", "Autore", "Titolo"));
                            }
                            else{ //altrimenti chiedo a server aiuto per il comando in questione
                                if(parts[1].equals("post"))
                                    handleWrite(client, length, "help show post");
                                else if(parts[1].equals("feed"))
                                    handleWrite(client, length, "help show feed");
                                else
                                    handleWrite(client, length, "help");
                                response = handleRead(client, bfs);
                            }
                            break;
                        case"blog":
                            if(parts.length == 1){ //controllo correttezza input
                                response = handleScrolling(msg, "blog", client, length, bfs, String.format("  %-10s | %-25s | %-25s", "ID", "Autore", "Titolo"));
                            }else{ //altrimenti chiedo a server aiuto per il comando in questione
                                handleWrite(client, length, "help blog");
                                response = handleRead(client, bfs);
                            }
                            break;
                        case"post":
                            String[] post_parts = msg.split("\"", 5); //post ".." ".."
                            if(post_parts.length != 4 && !post_parts[1].contains("|") && !post_parts[3].contains("|")){ //controllo correttezza input
                                //vado quindi a prima parsare la stringa (considerando i caratteri "), e poi a formattarla in modo da essere parsabile da server tramite
                                //carattere speciale "|"
                                String post_msg = "post ";
                                post_msg = post_msg.concat(post_parts[1]);//aggiungo titolo
                                post_msg = post_msg.concat("|"); //aggiungo separatore speciale
                                post_msg = post_msg.concat(post_parts[3]); //aggiungo contenuto post
                                handleWrite(client, length, post_msg); //e mando al server
                                response = handleRead(client, bfs);
                            }
                            else{ //altrimenti chiedo a server aiuto per il comando in questione
                                handleWrite(client, length, "help post");
                                response = handleRead(client, bfs);
                            }
                            break;
                        case"comment":
                            String[] comm_parts = msg.split(" ", 3); //comment id comm
                            if(comm_parts.length == 3 && isNatural(comm_parts[1])){ //controllo correttezza input
                                handleWrite(client, length, msg); //e mando al server
                                response = handleRead(client, bfs);
                            }
                            else{ //altrimenti chiedo a server aiuto per il comando in questione
                                handleWrite(client, length, "help comment");
                                response = handleRead(client, bfs);
                            }
                            break;
                        case"delete":
                        case"rewin":
                            String[] del_rewin_parts = msg.split(" ", 2); //delete/rewin id_post
                            if(del_rewin_parts.length == 2 && isNatural(del_rewin_parts[1])){ //controllo correttezza input
                                handleWrite(client, length, msg); //e mando al server
                                response = handleRead(client, bfs);
                            }
                            else{ //altrimenti chiedo a server aiuto per il comando in questione
                                if(del_rewin_parts[0].equals("delete"))
                                    handleWrite(client, length, "help delete");
                                else //fol_unfol_parts[0].equals("unfollow")
                                    handleWrite(client, length, "help rewin");
                                response = handleRead(client, bfs);
                            }
                            break;
                        case"follow":
                        case"unfollow":
                            String[] fol_unfol_parts = msg.split(" ", 2); //follow/unfollow username
                            if(fol_unfol_parts.length == 2){ //controllo correttezza input
                                handleWrite(client, length, msg); //e mando al server
                                response = handleRead(client, bfs);
                            }
                            else{ //altrimenti chiedo a server aiuto per il comando in questione
                                if(fol_unfol_parts[0].equals("follow"))
                                    handleWrite(client, length, "help follow");
                                else //fol_unfol_parts[0].equals("unfollow")
                                    handleWrite(client, length, "help unfollow");
                                response = handleRead(client, bfs);
                            }
                            break;
                        case"rate":
                            String[] rate_parts = msg.split(" ", 3); //comment id comm
                            if(rate_parts.length == 3 && isNatural(rate_parts[1]) && isVote(rate_parts[2])){ //controllo correttezza input
                                handleWrite(client, length, msg); //e mando al server
                                response = handleRead(client, bfs);
                            }
                            else{ //altrimenti chiedo a server aiuto per il comando in questione
                                handleWrite(client, length, "help rate");
                                response = handleRead(client, bfs);
                            }
                            break;
                        case"wallet":
                            String[] wallet_parts = msg.split(" ", 2);
                            if(wallet_parts.length == 1){ //controllo correttezza input
                                response = handleScrolling(msg, "wallet", client, length, bfs, "");
                            }
                            else if(wallet_parts.length == 2 && wallet_parts[1].equals("btc")){ //controllo correttezza input
                                handleWrite(client, length, msg); //mando al server richiesta wallet btc
                                response = handleRead(client, bfs);
                            }
                            else{ //altrimenti chiedo a server aiuto per il comando in questione
                                handleWrite(client, length, "help wallet");
                                response = handleRead(client, bfs);
                            }
                            break;
                        case "help": //chiedo a server aiuto per comandi
                            handleWrite(client, length, msg);
                            response = handleRead(client, bfs);
                            break;
                        default: //mando lista comandi possibili se viene digitato un comando sconosciuto
                            msg = "help";
                            handleWrite(client, length, msg);
                            response = handleRead(client, bfs);
                    }
                }else{
                    if(parts[0].equals("login") && parts.length == 3){ //login case
                        handleWrite(client, length, msg); //richiedo login
                        response = handleRead(client, bfs); //se va a buon fine, ottengo Login effettuato + informazioni per collegarmi a multicast group + max_show_elements per scrolling

                        String[] login_parts = response.split("\\|", 4);
                        if(login_parts[0].equals("Login effettuato")){
                            logged = true;
                            response = loginRoutine(msg, response, client, length, bfs, login_parts[1].split("=", 2)[1], Integer.parseInt(login_parts[2].split("=", 2)[1]), Integer.parseInt(login_parts[3].split("=", 2)[1]));
                        }
                    }
                    else if(parts[0].equals("register")){ //register case
                        response = registerRoutine(msg);
                    }else{ //mando messaggio al server per ottenere informazioni su cosa fare (answer da server: scrivi 'help' per comandi)
                        handleWrite(client, length, msg);
                        response = handleRead(client, bfs);
                    }
                }


                System.out.println(response); //print finale

            }
        } catch (IOException e) { //gestione eccezione
            if(mc_thread != null)
                mc_thread.interrupt();

            System.out.println("il server è chiuso!");
            exit(1);
        }

        if(mc_thread != null) //chiusura thread in lettura
            mc_thread.interrupt();
        try {
            if(is_followers_exported){
                stubServer.unregisterCallback(callback, this.username, this.password);
                UnicastRemoteObject.unexportObject(followers, true); //rimuovo l'oggetto remoto
            }
        } catch (RemoteException ex) {
            throw new RuntimeException(ex);
        }
    }

    //metodo per capire se l'input è un voto o meno
    private boolean isVote(String rate_part) {
        return Integer.parseInt(rate_part) == 1 || Integer.parseInt(rate_part) == -1;
    }

    //metodo che effettua il logout andando a fermare il thread che ascoltava in multicast, e rimuovendo l'oggetto remoto
    private String handleLogout(SocketChannel client, ByteBuffer length, String msg, ByteBuffer[] bfs) throws IOException {
        String[] parts = msg.split(" ", 2);
        String response = null;

        if(parts.length == 1){
            handleWrite(client, length, msg);
            response = handleRead(client, bfs);
            this.logged = false;

            mc_thread.interrupt();

            stubServer.unregisterCallback(callback, this.username, this.password);
            UnicastRemoteObject.unexportObject(followers, true);

            this.is_followers_exported = false;

            return response;
        }else{
            handleWrite(client, length, "help logout");
            return response = handleRead(client, bfs);
        }
    }

    //metodo per la lettura:
    public String handleRead(SocketChannel client, ByteBuffer[] bfs) throws IOException {
        String ret = "";

        boolean is_read = false;
        boolean is_onebuff_enough = true;

        //while (!bfs[0].hasRemaining()) //assumo che prima lettura riesca sempre a riempire bfs[0], visto la sua piccola dimensione
        long len = client.read(bfs); //leggo bfs[0], cioè dimensione totale del contenuto in arrivo

        bfs[0].flip();
        int l = bfs[0].getInt(); //e la assegno

        if(l>bfs[1].capacity()){ //gestisco il caso venga inviata risposta più grande del buffer stesso
            bfs[1].limit(BUFFER_SIZE);
        }
        else{
            bfs[1].limit(l);
        }

        if(len == 4){ //ho solo letto bfs[0], quindi leggo anche altro
            len = client.read(bfs); //leggo bfs[1]
        }

        int to_reach = 0; //quanto devo leggere
        while(!is_read){

            bfs[1].flip();

            ret = ret.concat(new String(bfs[1].array(), 0, (int) len)); //leggo da buffer

            to_reach += len;
            bfs[1].clear();
            if(to_reach == l) { //se sono arrivato alla fine
                is_read = true; //ho finito lettura
                //ret = ret.substring(0, l);
            }
            else{ //altrimento
                if(l - to_reach > bfs[1].capacity()){ //gestisco il caso venga inviata risposta più grande del buffer stesso
                    bfs[1].limit(BUFFER_SIZE);
                }
                else{ //sennò imposto limit = l - to_reach
                    bfs[1].limit(l - to_reach);
                }
                len = client.read(bfs[1]); //ed effettuo un'ulteriore lettura
            }

        }

        //alla fine pulisco anche prima parte del buffer
        bfs[0].clear();

        return ret;
    }

    public void handleWrite(SocketChannel client, ByteBuffer length, String msg) throws IOException {
        // la prima parte del messaggio contiene la lunghezza del messaggio
        length.putInt(msg.length());
        length.flip();
        client.write(length);
        length.clear();

        // Creo il messaggio da inviare al server
        ByteBuffer writeBuffer = ByteBuffer.wrap(msg.getBytes());

        client.write(writeBuffer);
        writeBuffer.clear();
    }

    public String loginRoutine(String inputMsg, String response, SocketChannel client, ByteBuffer length, ByteBuffer[] bfs, String multicast_addr, int multicast_port, int show_elements){
        String[] parts = inputMsg.split(" ", 3);
        try {
            followers= new RMIImplementation();
            //esporto oggetto followers
            callback = (RMIClient) UnicastRemoteObject.exportObject(followers, 0);
            //e mi registro al callback se il login è andato a buon fine
            int ris = stubServer.registerCallback(callback, parts[1], parts[2]);

            this.is_followers_exported = true;

            response = "Login effettuato";
            if(ris != 0){ //caso in cui la registrazione non va a buon fine (in qualsiasi caso, i dati sono stati manomessi da terze parti)
                handleWrite(client, length, "logout");
                response = handleRead(client, bfs);
                response = "Dati manomessi.\nRiavvia il client per effettuare di nuovo il login con i dati precedenti.";
                logged = false;
            }
            else{
                this.username = parts[1];
                this.password = parts[2];
            }

            this.show_elements = show_elements;

            //avvio thread per multicast group
            ClientNotificationPrinter.setBufferSize(MUTLICAST_BUFF_SIZE);
            InetAddress multicast = InetAddress.getByName(multicast_addr);
            mc_thread = new Thread(new ClientNotificationPrinter(multicast, multicast_port));
            mc_thread.setDaemon(true); //da testare
            mc_thread.start();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    private String registerRoutine(String msg) throws RemoteException {
        String response = null;

        String[] parts = msg.split(" ", 9); //register username password tag1 tag2 tag3 tag4 tag5 //ignoro eventuali tags oltre al quinto

        if(parts.length != 9 && parts.length > 3){ //controllo input
            List<String> tags = new ArrayList<String>();
            for (int i = 3; i<parts.length; i++){
                //porto in minuscolo tutti i tags
                tags.add(parts[i].toLowerCase());
            }

            int res = stubServer.signUp(parts[1], parts[2], tags);

            switch (res){ //stampo risultato dell'op signUp
                case 0:
                    response = "Registrazione avvenuta con successo";
                    break;
                case -1:
                    response = "Utente già registrato con quell'username";
                    break;
                case 1:
                    response = "Formato username errato: uno username deve essere lungo almeno 4 caratteri, e lungo massimo 20. Non è accettato il carattere '|'";
                    break;
                case 2:
                    response = "Formato password errata: una password deve essere lungo almeno 4 caratteri, e lungo massimo 20. Non è accettato il carattere '|'";
                    break;
                case 3:
                    response = "Formato tags errato: almeno un tag contiene il carattere non è accettato '|'";
                    break;
                case 4:
                    response = "Numero tags errato: il numero di tags inseribili va da 1 a 5";
                    break;
            }
        }
        else{
            response = "Formato comando errato. Utilizzo corretto: register <username> <password> <tags> (massimo 5 tags)";
        }

        return response;
    }

    private String handleScrolling(String msg, String service, SocketChannel client, ByteBuffer length, ByteBuffer[] bfs, String header) throws IOException {
        String response = "";
        boolean exit_iteration = false;
        boolean first_iteration = true;
        boolean first_format_iteration = true;


        while (!exit_iteration) { //iterazione da cui non esco fino a quando non esco dallo scrolling

            if(msg.equals("exit")){ //sempre falso prima iterazione
                exit_iteration = true;
            }else{

                handleWrite(client, length, msg);
                response = handleRead(client, bfs);

                //preparo header a seconda di chi ha richiesto lo scrolling
                switch (service){
                    case"post":
                        if(first_iteration && first_format_iteration){
                            if(!response.equals("Il post cercato non esiste")){
                                String[] tkn_msg_post = response.split("\\|", 5);

                                header = "Titolo: ".concat(tkn_msg_post[0]+"\n");//creo header_titolo e carico titolo
                                header = header.concat("Contenuto: "+tkn_msg_post[1]+"\n");//carico contenuto
                                header = header.concat("Voti: "+tkn_msg_post[2]+" positivi, "+tkn_msg_post[3]+" negativi\n");//carico voti
                                header = header.concat("Commenti:");//inizio commenti
                                first_iteration = false;
                                System.out.println(header);
                                if(!tkn_msg_post[4].equals("")) //non contiene commenti
                                    ResponseFormatter.formatStringString(tkn_msg_post[4]);
                            }
                            else{
                                return response;
                            }
                        }
                        break;
                    case"wallet":
                        if(first_iteration){
                            String[] tkn_msg_wall = response.split("\\|", 2);

                            header = "Wincoins: ".concat(tkn_msg_wall[0]+"\n");//creo header_titolo e carico wincoins
                            header = header.concat("Transazioni:");//inizio transazioni
                            first_iteration = false;
                            System.out.println(header);
                            ResponseFormatter.formatStringString(tkn_msg_wall[1]);
                        }
                        break;
                }
                if(first_iteration)
                    System.out.println(header);
                if(service.equals("blog") || service.equals("feed") || service.equals("list")){
                    for (int i=0; i<70; i++){
                        System.out.print("_ ");
                    }
                    System.out.print("\n");
                }


                switch (service){ //e richiedo la formattazione giusta a seconda di chi ha richiesto lo scrolling
                    case"blog":
                    case"feed":
                        ResponseFormatter.formatIdAuthTitle(response);
                        break;
                    case"list":
                        ResponseFormatter.formatUserTags(response);
                        break;
                    case"post":
                    case"wallet":
                        if(first_format_iteration)
                            first_format_iteration = false;
                        else
                            ResponseFormatter.formatStringString(response);
                        break;
                }
                msg = consoleReader.readLine().trim(); //prossimo comando

                while(!(msg.equals("n") || msg.equals("p") || msg.equals("exit"))){ //fino a quando non fornisci un prossimo comando valido
                    handleWrite(client, length, msg);
                    System.out.println(handleRead(client, bfs));
                    msg = consoleReader.readLine().trim(); //leggo prossimi comandi
                }
            }
        }
        handleWrite(client, length, msg);
        response = handleRead(client, bfs);
        return response;
    }

    //scrolling implementato in locale con lista ottenuta dall'implementazione di RMIClient
    private String handleListFollowers(int show_elements) throws IOException {
        boolean exit_iteration = false;
        int pointer = 0; //parto da puntatore a 0; intero che rappresenta il limite sinistro della finestra di scorrimento
        int max_itr; //intero che rappresenta il limite destro della finestra di scorrimento
        boolean skip_next = false;
        String msg_itr = "first_itr";

        String response="";
        ArrayList<String> formatted_follwers = new ArrayList<>();

        while (!exit_iteration) {

            if(!skip_next){
                formatted_follwers = this.followers.getFormattedFollowers(); //vado ogni volta a caricare formatted_follwers in modo da avere pagina followers aggiornata live
                //aggiornamento live effettuato solo su client: se volessi effettuare aggiornamento live su server, dovrei costruire ogni volta formatted_users/following (troppo costoso)

                max_itr = pointer + show_elements; //vedo se posso portare senza problemi max_itr a pointer + show_elements
                if(max_itr > formatted_follwers.size()) //se max_itr è più grande della lista
                    max_itr = formatted_follwers.size();

                String to_format = "";

                for(int i = pointer; i<max_itr; i++){
                    to_format = to_format.concat(formatted_follwers.get(i)+"\n"); //vado ad aggiungere separatore tra gli elementi di formatted_follwers
                }
                if(!to_format.equals(""))
                    to_format = to_format.substring(0, to_format.length()-1); //elimino ultimo a capo

                System.out.printf("  %-25s |  %-25s %n", "Utente", "Tags"); //preparo header
                for (int i=0; i<70; i++){
                    System.out.print("_ ");
                }
                System.out.print("\n");

                ResponseFormatter.formatUserTags(to_format); //e passo al formatter adeguato per stampa del risultato
            }

            skip_next = false;
            msg_itr = consoleReader.readLine().trim(); //prossimo comando

            while(!(msg_itr.equals("n") || msg_itr.equals("p") || msg_itr.equals("first_itr") || msg_itr.equals("exit") || msg_itr.equals("help"))){ //fino a quando non fornisci un prossimo comando valido
                System.out.println("Comandi utilizzabili: n / p / exit / help");
                msg_itr = consoleReader.readLine().trim(); //leggo comandi
            }

            switch (msg_itr){
                case"first_itr":
                case"n":
                    if(pointer + show_elements < formatted_follwers.size()) //se posso spostare il pointer in avanti (perché presenti elementi)
                        pointer += show_elements; //lo faccio
                    //altrimenti mostro sempre stessa pagina
                    break;
                case"p":
                    if(pointer - show_elements >= 0) //se posso spostare il pointer indietro (perché presenti elementi a ritroso)
                        pointer -= show_elements; //lo faccio
                    //altrimenti mostro sempre stessa pagina
                    break;
                case"exit":
                    exit_iteration = true;
                    System.out.println("Uscito dalla lista");
                    break;
                case"help":
                    skip_next = true;
                    System.out.println("Comandi possibili:p (previous) -> ti permette di visualizzare i precedenti " + show_elements + " elementi della pagina (se presenti)\n" +
                            "n (next) -> ti permette di visualizzare i successivi " + show_elements + " elementi della pagina (se presenti)\n" +
                            "exit -> ti permette di uscire dalla pagina");
            }
        }
        return response;
    }

    public static boolean isNatural(String s) {
        if(s.isEmpty()) return false;
        for(int i = 0; i < s.length(); i++) {
            if(Character.digit(s.charAt(i),10) < 0)
                return false;
        }
        return true;
    }
}
