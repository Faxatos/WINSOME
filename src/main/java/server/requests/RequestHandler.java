package server.requests;

import server.utils.posts.Post;
import server.SocialNetwork;
import server.utils.users.User;
import server.utils.ConnectionAttachment;
import server.utils.Pair;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;

public class RequestHandler implements Runnable{

    private final SelectionKey sk;
    private final Selector selector;
    private static SocialNetwork social_network;

    public static int BUFFER_SIZE;
    private static String multicast_adrr;
    private static int multicast_port;

    //variabile per infinite scrolling

    private static int show_elements = 25; //default value
    private static int max_days_without_posts = 3; //default value

    public RequestHandler(SelectionKey sk, Selector selector){
        this.sk = sk;
        this.selector = selector;
    }

    @Override
    public void run() {

        try {
            if(sk.isReadable()){
                handle_read();
            } else if (sk.isWritable()){
                handle_write();
            }
        } catch (Exception e) {
            e.printStackTrace();
            close(sk);
        }

        selector.wakeup();//  Force Selector to return immediately
    }

    private void handle_request(String req) {

        ConnectionAttachment ca = (ConnectionAttachment) sk.attachment();

        //business logic
        StringTokenizer tokenizedReq = new StringTokenizer(req);

        if(ca.getPointer() != -1 && ca.getUser() != null){
            //abbiamo uno scrolling aperto
            String next_cmd = tokenizedReq.nextToken().trim();
            switch (next_cmd) { //quindi richiamiamo metodi legati allo scrolling, passando il tipo di servizio che è attivo (ca.getType_of_service())
                case "n":
                    forwardScrolling(ca, ca.getType_of_service());
                    break;
                case "p":
                    backwardScrolling(ca);
                    break;
                case "help":
                    ca.setBufferData(ByteBuffer.wrap(("Comandi possibili:\np (previous) -> ti permette di visualizzare i precedenti " + show_elements + " elementi della pagina (se presenti)\n" +
                            "n (next) -> ti permette di visualizzare i successivi " + show_elements + " elementi della pagina (se presenti)\n" +
                            "exit -> ti permette di uscire dalla pagina").getBytes()));
                    break;
                case "exit":
                    ca.setPointer(-1);
                    ca.setType_of_service(null);
                    ca.setCap_reached(false);
                    ca.clearScrolling();
                    //ca.setLongResponse(null);
                    ca.setBufferData(ByteBuffer.wrap("Uscito dalla pagina".getBytes()));
                    break;
                default:
                    ca.setBufferData(ByteBuffer.wrap("Comandi possibili: p - n - exit - help".getBytes()));
            }
        } //richiesta normale (cioè non scrolling), con utente loggato (caso di utente non loggato in else -> handle_login)
        else if(ca.getPointer() == -1 && ca.getUser() != null){
            String next_cmd = tokenizedReq.nextToken().trim();
            String result;
            String[] req_split;
            int req_res;
            switch (next_cmd){ //a seconda del prossimo comando
                case "list":
                    req_split = req.split(" ", 2);
                    if(req_split.length == 2 && req_split[1].equals("users")){ //controllo correttezza messaggio
                        result = initializeScrolling(ca, "list users"); //metodo con scrolling
                        ca.setLongResponse(result);
                    }
                    else if(req_split.length == 2 && req_split[1].equals("following")){ //controllo correttezza messaggio
                        result = initializeScrolling(ca, "list following"); //metodo con scrolling
                        ca.setLongResponse(result);
                    }
                    else{ //altrimenti mando usage
                        ca.setBufferData(ByteBuffer.wrap("[usage: list users/followers/following]".getBytes()));
                    }
                    break;
                case "follow":
                    req_split = req.split(" ", 2);
                    if(req_split.length == 2){ //controllo correttezza messaggio
                        req_res = social_network.followUser(ca.getUser(), req_split[1]); //e richiedo metodo a social network
                        result = req_split[1];
                        switch (req_res){
                            case -1:
                                result = result.concat(" non trovato");
                                break;
                            case 0:
                                result = result.concat(" seguito con successo");
                                break;
                            case 1:
                                result = result.concat(" già seguito");
                                break;
                            case 2:
                                result = "Non puoi seguire te stesso";
                                break;
                        }
                        ca.setBufferData(ByteBuffer.wrap(result.getBytes()));
                    }
                    else{
                        ca.setBufferData(ByteBuffer.wrap("[usage: follow {username}]".getBytes()));
                    }
                    break;
                case "unfollow":
                    req_split = req.split(" ", 2);
                    if(req_split.length == 2){ //controllo correttezza messaggio
                        req_res = social_network.unfollowUser(ca.getUser(), req_split[1]); //e richiedo metodo a social network
                        result = "";
                        switch (req_res){
                            case -1:
                                result = req_split[1];
                                result = result.concat(" non trovato");
                                break;
                            case 0:
                                result = result.concat("hai smesso di seguire");
                                result = result.concat(req_split[1]);
                                result = result.concat("con successo");
                                break;
                            case 1:
                                result = result.concat("hai già smesso di seguire");
                                result = result.concat(req_split[1]);
                                break;
                            case 2:
                                result = "Non puoi smettere seguire te stesso";
                                break;
                        }
                        ca.setBufferData(ByteBuffer.wrap(result.getBytes()));
                    }
                    else{
                        ca.setBufferData(ByteBuffer.wrap("[usage: follow {username}]".getBytes()));
                    }
                    break;
                case "blog":
                    result = initializeScrolling(ca, "blog"); //metodo con scrolling
                    ca.setLongResponse(result);
                    break;
                case "post":
                    req_split = req.split(" ", 2);
                    if(req_split.length == 2){ //controllo correttezza messaggio
                        String[] post_split = req_split[1].split("\\|", 2); //effettuo parsing
                        req_res = social_network.createPost(ca.getUser(), post_split[0], post_split[1]);  //e richiedo metodo a social network
                        result = "";
                        switch (req_res){
                            case 0:
                                result = "Post creato";
                                break;
                            case 1:
                                result = "Lunghezza titolo non ammessa";
                                break;
                            case 2:
                                result = "Lunghezza contenuto non ammessa";
                                break;
                        }
                        ca.setBufferData(ByteBuffer.wrap(result.getBytes()));
                    }
                    else{
                        ca.setBufferData(ByteBuffer.wrap("[post titolo|contenuto]".getBytes()));
                    }
                    break;
                case "show":
                    req_split = req.split(" ", 3);
                    if(req_split.length == 2 && req_split[1].equals("feed")){
                        result = initializeScrolling(ca, "show feed"); //metodo con scrolling
                        ca.setLongResponse(result);
                    }
                    else if(req_split.length == 3 && req_split[1].equals("post")){
                        int post_id = Integer.parseInt(req_split[2]);
                        result = social_network.showPost(post_id); //e richiedo metodo a social network
                        if(result != null){
                            ca.setTmp_id_post(post_id);
                            String result_conc = initializeScrolling(ca, "show post"); //metodo con scrolling (per commenti del post)
                            result = result.concat(result_conc);
                            ca.setLongResponse(result);
                        }
                        else{
                            ca.setBufferData(ByteBuffer.wrap("Il post cercato non esiste".getBytes()));
                        }
                    }
                    else{
                        ca.setBufferData(ByteBuffer.wrap("[usage: show feed/post]".getBytes()));
                    }
                    break;
                case "rewin":
                    req_split = req.split(" ", 2);
                    if(req_split.length == 2){ //controllo correttezza messaggio
                        try{
                            req_res = social_network.rewinPost(ca.getUser(), Integer.parseInt(req_split[1])); //e richiedo metodo a social network
                        }catch (NumberFormatException ignored){
                            req_res = 3;
                        }
                        result = "";
                        switch (req_res){
                            case -1:
                                result = "Post non trovato";
                                break;
                            case 0:
                                result = "Rewin eseguito correttamente";
                                break;
                            case 1:
                                result = "Non puoi effettuare il rewin di un post che non è presente nel tuo feed";
                                break;
                            case 2:
                                result = "Non puoi effettuare il rewin di un tuo stesso post";
                                break;
                            case 3:
                                result = "ID post non numerico";
                                break;
                        }
                        ca.setBufferData(ByteBuffer.wrap(result.getBytes()));
                    }
                    else
                        ca.setBufferData(ByteBuffer.wrap("[usage: rewin <idPost>]".getBytes()));
                    break;
                case "rate":
                    req_split = req.split(" ", 3); //controllo correttezza messaggio
                    if(req_split.length == 3){
                        req_res = 4;

                        if(Integer.parseInt(req_split[2]) == 1) { //e richiedo metodo a social network in base al voto
                            req_res = social_network.ratePost(ca.getUser(), Integer.parseInt(req_split[1]), true);
                        }
                        else if(Integer.parseInt(req_split[2]) == -1)
                            req_res = social_network.ratePost(ca.getUser(), Integer.parseInt(req_split[1]), false);
                        result = "";
                        switch (req_res){
                            case -1:
                                result = "Post non trovato";
                                break;
                            case 0:
                                result = "Post votato correttamente";
                                break;
                            case 1:
                                result = "Hai già votato il post";
                                break;
                            case 2:
                                result = "Non puoi votare un post che non è presente nel tuo feed";
                                break;
                            case 3:
                                result = "Non puoi votare un tuo stesso post";
                                break;
                            case 4: //ulteriore controllo sul voto (già controllato da client)
                                result = "Voto non valido";
                                break;
                        }
                        ca.setBufferData(ByteBuffer.wrap(result.getBytes()));
                    }else{
                        ca.setBufferData(ByteBuffer.wrap("[usage: rate <idPost> <vote>]".getBytes()));
                    }
                    break;
                case "comment":
                    req_split = req.split(" ", 3);
                    if(req_split.length == 3){ //controllo correttezza messaggio
                        req_res = social_network.addComment(ca.getUser(), Integer.parseInt(req_split[1]), req_split[2]);  //e richiedo metodo a social network
                        result = "";
                        switch (req_res){
                            case -1:
                                result = "Post non trovato";
                                break;
                            case 0:
                                result = "Commento aggiunto";
                                break;
                            case 1:
                                result = "Non puoi commentare un post che non è presente nel tuo feed";
                                break;
                            case 2:
                                result = "Non puoi commentare il tuo stesso post";
                                break;
                        }
                        ca.setBufferData(ByteBuffer.wrap(result.trim().getBytes()));
                    }
                    else
                        ca.setBufferData(ByteBuffer.wrap("[usage: comment <idPost> <comment>]".getBytes()));
                    break;
                case "delete":
                    req_split = req.split(" ", 2);
                    if(req_split.length == 2){ //controllo correttezza messaggio
                        try{
                            req_res = social_network.deletePost(ca.getUser(), Integer.parseInt(req_split[1]));  //e richiedo metodo a social network
                        }catch (NumberFormatException ignored){
                            req_res = 2;
                        }
                        result = "";
                        switch (req_res){
                            case -1:
                                result = "Post non trovato";
                                break;
                            case 0:
                                result = "Post eliminato";
                                break;
                            case 1:
                                result = "Non puoi eliminare posts altrui";
                                break;
                            case 2:
                                result = "ID post non numerico";
                                break;
                        }
                        ca.setBufferData(ByteBuffer.wrap(result.trim().getBytes()));
                    }
                    else
                        ca.setBufferData(ByteBuffer.wrap("[usage: delete <idPost>]".getBytes()));
                    break;
                case "wallet":
                    req_split = req.split(" ", 2);
                    if(req_split.length == 1 && req_split[0].equals("wallet")){  //controllo correttezza messaggio
                        float wincoins = social_network.getWalletValue(ca.getUser());  //e richiedo metodo a social network
                        result = String.valueOf(wincoins); //vado prima ad agginugere valore wallet in wincoins
                        result = result.concat("|"); //agiungo separatore
                        String result_conc = initializeScrolling(ca, "wallet"); //e apro metodo con scrolling (per transizioni del wallet)
                        result = result.concat(result_conc);
                        ca.setLongResponse(result);
                    }
                    else if(req_split.length == 2 && req_split[1].equals("btc")){ //controllo correttezza messaggio
                        float btc = social_network.getWalletInBitcoin(ca.getUser()); //e richiedo metodo a social network
                        result = String.valueOf(btc);
                        ca.setBufferData(ByteBuffer.wrap(result.getBytes()));
                    }
                    else
                        ca.setBufferData(ByteBuffer.wrap("[usage: wallet/wallet btc]".getBytes()));
                    break;
                case "logout":
                    req_split = req.split(" ", 1);
                    if (req_split[0].equals("logout")) { //controllo correttezza messaggio
                        social_network.logout(ca.getUser()); //e richiedo metodo a social network
                        ca.setUser(null);
                        ca.setBufferData(ByteBuffer.wrap("Logout effettuato".getBytes()));
                    }
                    else
                        ca.setBufferData(ByteBuffer.wrap("[usage: logout]".getBytes()));
                    break;
                case "help":
                    handleHelp(req, ca);
                    break;
                default:
                    ca.setBufferData(ByteBuffer.wrap("Digitare il comando 'help' per ricevere i possibili comandi".getBytes()));
            }
        }
        else { //login routine
            handle_login(ca, tokenizedReq);
        }
        sk.interestOps(SelectionKey.OP_WRITE);
    }


    private void handle_read() throws IOException {
        SocketChannel channel = (SocketChannel) sk.channel();
        ConnectionAttachment att = (ConnectionAttachment) sk.attachment();

        ByteBuffer[] bfs = att.getBuffer();

        long n = 0;
        try {
            n = channel.read(bfs); //leggo
        } catch (IOException e) { //chiusura connessione improvvisa da parte di client
            close(sk);
        }

        if (n == -1) { //end of stream
            close(sk);
            return;
        }

        if (!bfs[0].hasRemaining()){ //se non ho finito di caricare dim messaggio provo con prossima read
            bfs[0].flip();
            int l = bfs[0].getInt(); //sennò prendo dim
            if (bfs[1].position() >= l) { //e controllo se ho letto tutto
                bfs[1].flip();
                String req_buf = new String(bfs[1].array()).trim();
                String req = req_buf.substring(0, l); //vado così a prendere solo il contenuto
                System.out.printf("Server: ricevuto %s da %s\n", req, channel.getRemoteAddress());
                bfs[0].clear(); //pulisco quindi buffer
                bfs[1].clear();
                handle_request(req); //ed inizializzo così request
            }
            else{ //non ho finito di leggere
                //sk.interestOps(SelectionKey.OP_READ); //abilito lettura
            }
        }
    }

    private void handle_write() throws IOException {
        SocketChannel channel = (SocketChannel) sk.channel();
        ConnectionAttachment att = (ConnectionAttachment) sk.attachment();
        ByteBuffer bb = att.getBufferData();
        ByteBuffer bb_len = att.getBufferLen();
        //String debug_buffer_pre = bb.toString();
        //String debug_string = null;

        String lresp = att.getLongResponse(); //prendo risposta punga

        if(att.getFirstWriting() && lresp == null){ //buffer già caricato, quindi mando dimensione messaggio corto
            bb_len.putInt(bb.limit());
            bb_len.flip();
            channel.write(bb_len);
            att.setFirstWriting(false);
        }

        if(/*is_written &&*/ lresp != null){ //caso di risposta lunga da dividere in buffers
            if(att.getFirstWriting()){ //mando dimensione messaggio lungo
                bb_len.putInt(lresp.length());
                bb_len.flip();
                channel.write(bb_len);
                att.setFirstWriting(false);
            }
            String tmp = att.splitLongResponse();
            bb = ByteBuffer.wrap(tmp.getBytes()); //inserisco porzione di risposta lunga dentro bb
            //is_written = false;
        }

        channel.write(bb);
        String debug_buffer_post = bb.toString();
        if(!bb.hasRemaining()) { //quando il buffer ha finito di scrivere
            if(att.getLongResponse() != null){ //se ho ancora parte di long resp da mandare
                //is_written = true; //allora abilito prossimo split
                sk.interestOps(SelectionKey.OP_WRITE); //continuo con scrittura
            }

            else{ //se ho mandato tutto
                bb.clear(); //pulisco buffer
                att.getBufferLen().clear(); //pulisco anche prima parte del buffer per favorire nuova lettura
                att.setFirstWriting(true);
                sk.interestOps(SelectionKey.OP_READ); //abilito lettura
            }
        }
    }

    //gestisce richiesta di login
    private static void handle_login(ConnectionAttachment ca, StringTokenizer tokenizedLine){
        String next_tkn = tokenizedLine.nextToken().trim();
        if(next_tkn.equals("login")){
            try{
                String user = tokenizedLine.nextToken();
                String psw = tokenizedLine.nextToken();
                if(!tokenizedLine.hasMoreTokens()){
                    Pair<User, Integer> ret_value = social_network.login(user, psw);  //valuto risposta server alla richiesta
                    if(ret_value.getSecond() == 0){ //se va a buon fine
                        ca.setUser(ret_value.getFirst());
                        String set_response = "Login effettuato|multicast_adrr="+multicast_adrr+"|multicast_port="+multicast_port+"|show_elements="+show_elements; //aggiungo le informazioni necessarie per il dopo login
                        ca.setBufferData(ByteBuffer.wrap(set_response.getBytes()));
                    }
                    else if(ret_value.getSecond() == -1){
                        ca.setBufferData(ByteBuffer.wrap(("Combinazione di username e password non corretta").getBytes()));
                    }
                    else{ //ret_value.getSecond() == 2
                        ca.setBufferData(ByteBuffer.wrap(("Utente " + user + " ha già effettuato login su altra macchina").getBytes()));
                    }
                }
                else{ //caso in cui vengono inseriti più parametri del dovuto
                    ca.setBufferData(ByteBuffer.wrap("[usage: login <username> <password>]".getBytes()));
                }
            }catch (NoSuchElementException e){ //nel caso vengano inseriti meno parametri del dovuto
                ca.setBufferData(ByteBuffer.wrap("[usage: login <username> <password>]".getBytes()));
            }
        }
        else if(next_tkn.equals("help")){
            if(tokenizedLine.hasMoreTokens()){
                String last_tnk = tokenizedLine.nextToken().trim();
                if(last_tnk.equals("login"))
                    ca.setBufferData(ByteBuffer.wrap("Permette il login all'interno del social network.\nSintassi: login <username> <password>".getBytes()));
                if(last_tnk.equals("register"))
                    ca.setBufferData(ByteBuffer.wrap(("Permette la registrazione all'interno del social network. Non è però ammesso il carattere | tra i parametri del comando.\n" +
                            "Sintassi: register <username> <password> <tag1, tag2, tag3, tag4, tag5> (minimo un tag, massimo 5).").getBytes()));
            }
            else{ //help only case
                ca.setBufferData(ByteBuffer.wrap("Elenco dei comandi possibili. Per sapere più informazioni su ogni comando, digitare help <nome comando>:\nlogin / register".getBytes()));
            }
        }
        else //richiesta altra operazione mentre non si è ancora loggati
            ca.setBufferData(ByteBuffer.wrap("Digitare 'help' per ricevere l'elenco dei comandi possibili".getBytes()));
    }

    //gestisce richiesta di help
    private void handleHelp(String req, ConnectionAttachment ca) {
        String[] req_split = req.split(" ", 2);
        if (req_split[0].equals("help") && req_split.length == 1) {
            ca.setBufferData(ByteBuffer.wrap(("Elenco dei comandi possibili. Per sapere più informazioni su ogni comando, digitare help <nome comando>:\n" +
                    "list users / list following / list followers / follow / unfollow / blog / post / show feed / show post / rewin / rate / comment / delete / wallet / wallet btc").getBytes()));
        }
        else if (req_split[1].equals("list users")) {
            ca.setBufferData(ByteBuffer.wrap(("Comando che inizializza una pagina contenente gli utenti della piattaforma che hanno tag in comune ai tuoi.\n" +
                    "Sintassi: list users; Navigazione nella pagina: n (next) | p (previous) | exit").getBytes()));
        }
        else if (req_split[1].equals("list following")) {
            ca.setBufferData(ByteBuffer.wrap(("Comando che inizializza una pagina contenente gli utenti della piattaforma che stai seguendo.\n" +
                    "Sintassi: list following; Navigazione nella pagina: n (next) | p (previous) | exit").getBytes()));
        }
        else if (req_split[1].equals("list followers")) {
            ca.setBufferData(ByteBuffer.wrap(("Comando che inizializza una pagina contenente gli utenti della piattaforma che ti stanno seguendo.\n" +
                    "Sintassi: list followers; Navigazione nella pagina: n (next) | p (previous) | exit").getBytes()));
        }
        else if (req_split[1].equals("follow")) {
            ca.setBufferData(ByteBuffer.wrap(("Comando che ti permette di seguire l'utente identificato da <nome utente>.\n" +
                    "Sintassi: follow <nome utente>").getBytes()));
        }
        else if (req_split[1].equals("unfollow")) {
            ca.setBufferData(ByteBuffer.wrap(("Comando che ti permette di smettere di seguire l'utente identificato da <nome utente>\n" +
                    "Sintassi: unfollow <nome utente>").getBytes()));
        }
        else if (req_split[1].equals("blog")) {
            ca.setBufferData(ByteBuffer.wrap(("Comando che inizializza una pagina contenente i post da te pubblicati (compresi i rewin).\n" +
                    "Sintassi: blog; Navigazione nella pagina: n (next) | p (previous) | exit").getBytes()));
        }
        else if (req_split[1].equals("post")) {
            ca.setBufferData(ByteBuffer.wrap(("Permette di pubblicare un post di titolo \"titolo\" e contenuto \"contenuto\". Attenzione! Utilizzare il carattere speciale | può portare ad anomalie nella creazione del post.\n" +
                    "Sintassi: post <\"titolo\"> <\"contenuto\">").getBytes()));
        }
        else if (req_split[1].equals("show feed")) {
            ca.setBufferData(ByteBuffer.wrap(("Comando che inizializza una pagina contenente i post pubblicati dagli utenti che segui. Il comando cerca fino a n giorni a ritroso a partire dall'ultimo post visualizzato nel feed\n" +
                    "(quindi la fine del feed non indica necessariamente che un utente seguito abbia pubblicato solamente i posts visualizzati nel feed).\n" +
                    "Sintassi: show feed; Navigazione nella pagina: n (next) | p (previous) | exit").getBytes()));
        }
        else if (req_split[1].equals("show post")) {
            ca.setBufferData(ByteBuffer.wrap(("Comando che prima mostra il post identificato da <id post>, e poi inizializza una pagina contenente i commenti del post.\n" +
                    "Sintassi: show post <id post>; Navigazione nella pagina: n (next) | p (previous) | exit").getBytes()));
        }
        else if (req_split[1].equals("rewin")) {
            ca.setBufferData(ByteBuffer.wrap(("Comando che consente la ripubblicazione (rewin) di un post identificato da <id post>.\n" +
                    "Sintassi: rewin <id post>").getBytes()));
        }
        else if (req_split[1].equals("rate")) {
            ca.setBufferData(ByteBuffer.wrap(("Comando che permette di valutare un post identificato da <id post>. Una volta valutato, non è possibile più cambiare la propria valuazione.\n" +
                    "Sintassi: rate <id post> <voto>").getBytes()));
        }
        else if (req_split[1].equals("comment")) {
            ca.setBufferData(ByteBuffer.wrap(("Comando che permette di commentare un post identificato da <id post>.\n" +
                    "Sintassi: comment <id post> <commento>").getBytes()));
        }
        else if (req_split[1].equals("delete")) {
            ca.setBufferData(ByteBuffer.wrap(("Comando che permette di eliminare un post identificato da <id post>.\n" +
                    "Sintassi: delete <id post>").getBytes()));
        }
        else if (req_split[1].equals("wallet")) {
            ca.setBufferData(ByteBuffer.wrap(("Comando che prima stampa il totale dei wincoins in nostro possesso, e successivamente inizializza una pagina contenentela lista delle transizioni.\n" +
                    "Sintassi: wallet; Navigazione nella pagina: n (next) | p (previous) | exit").getBytes()));
        }
        else if (req_split[1].equals("wallet btc")) {
            ca.setBufferData(ByteBuffer.wrap(("Comando che ci mosta quanto valgono i nostri wincoins convertiti in bitcoins.\n" +
                    "Sintassi: wallet btc").getBytes()));
        }
        else if (req_split[1].equals("logout")) {
            ca.setBufferData(ByteBuffer.wrap(("Comando che permette la disconnessione dal social network.\n" +
                    "Sintassi: logout").getBytes()));
        }
        else
            ca.setBufferData(ByteBuffer.wrap(("Elenco dei comandi possibili. Per sapere più informazioni su ogni comando, digitare help <nome comando>:\n" +
                    "").getBytes()));
    }

    private String initializeScrolling(ConnectionAttachment ca, String type_of_serv){
        ca.setPointer(0); //parto da puntatore a 0; intero che rappresenta il limite sinistro della finestra di scorrimento
        ca.setType_of_service(type_of_serv);
        switch (type_of_serv){ //richiamo metodo in social network con show_elements (prima call)
            //e carico arrayList<String> scrolling, che contiene gli elementi da stampare (del tipo type_of_serv)
            case "list users":
                ca.addToScrolling(social_network.listUsers(ca.getUser().getTags(), ca.getUser().getUsername(), show_elements));
                break;
            case "list following":
                ca.addToScrolling(social_network.listFollowing(ca.getUser(), show_elements));
                break;
            case "blog":
                ca.addToScrolling(social_network.viewBlog(ca.getUser(), show_elements));
                break;
            case "show feed":
                ArrayList<Post> latest_feed = new ArrayList<>();
                Calendar today = Calendar.getInstance();
                social_network.showFeed(ca.getUser(), today.getTime(),latest_feed, 0, max_days_without_posts, show_elements); //showfeed mi carica latest_feed
                ArrayList<String> latest_feed_formatted = new ArrayList<>(); //quindi vado dopo a formattarlo secondo formato adatto per scrolling
                for (Post post : latest_feed) {
                    latest_feed_formatted.add(post.getID()+"|"+post.getAuthor()+"|"+post.getTitle());
                }
                ca.addToScrolling(latest_feed_formatted);
                break;
            case "show post":
                ca.addToScrolling(social_network.showPostComments(ca.getTmp_id_post(), show_elements));
                break;
            case "wallet":
                ca.addToScrolling(social_network.getWalletTransactions(ca.getUser(), show_elements));
                break;
        }

        int itr_size = ca.getPointer()+show_elements; //intero che rappresenta il limite destro della finestra di scorrimento
        if(ca.getScrollingSize() <= itr_size){ //vedo se posso portare senza problemi max_itr a pointer + show_elements
            itr_size = ca.getScrollingSize();
            ca.setCap_reached(true);
        }

        String result = "";
        ArrayList<String> tmp_scrolling = new ArrayList<>(ca.getScrolling());
        for (int i = ca.getPointer(); i<itr_size; i++){ //e carico il risultato quanto posso
            result = result.concat(tmp_scrolling.get(i)+"\n");
        }

        if(!tmp_scrolling.isEmpty())
            result = result.substring(0, result.length()-1);
        return result; //mando poi al client risultato
    }

    private void forwardScrolling(ConnectionAttachment ca, String type_of_serv){
        //i-esima richiesta di scrolling, quindi incremento pointer (finestra sinistra) se non ho raggiunto il massimo (cioè il server non ci da più elementi)
        if(!ca.isCap_reached())
            ca.setPointer(ca.getPointer()+show_elements);
        int starting_point = ca.getPointer();

        //se non ho raggiunto il massimo, e la dim. di ConnectionAttachment.scrolling è < ca.getPointer()+show_elements
        if(!ca.isCap_reached() && ca.getScrollingSize()<ca.getPointer()+show_elements){
            //carico con ancora più elementi arrayList<String> scrolling, che contiene gli elementi da stampare (del tipo type_of_serv)
            ca.clearScrolling();
            switch (type_of_serv){
                case "list users":
                    ca.addToScrolling(social_network.listUsers(ca.getUser().getTags(), ca.getUser().getUsername(), ca.getPointer()+show_elements));
                    break;
                case "list following":
                    ca.addToScrolling(social_network.listFollowing(ca.getUser(), ca.getPointer()+show_elements));
                    break;
                case "blog":
                    ca.addToScrolling(social_network.viewBlog(ca.getUser(), ca.getPointer()+show_elements));
                    break;
                case "show feed":
                    ArrayList<Post> latest_feed = new ArrayList<>();
                    Calendar today = Calendar.getInstance();
                    //today.set(Calendar.HOUR_OF_DAY, 0);
                    social_network.showFeed(ca.getUser(), today.getTime(),latest_feed, 0, max_days_without_posts, ca.getPointer()+show_elements);
                    ArrayList<String> latest_feed_formatted = new ArrayList<>();
                    for (Post post : latest_feed) {
                        latest_feed_formatted.add(post.getID()+"|"+post.getAuthor()+"|"+post.getContent());
                    }
                    ca.addToScrolling(latest_feed_formatted);
                    break;
                case "show post": //add_information = post_id
                    ca.addToScrolling(social_network.showPostComments(ca.getTmp_id_post(), ca.getPointer()+show_elements));
                    break;
                case "wallet":
                    ca.addToScrolling(social_network.getWalletTransactions(ca.getUser(), show_elements));
                    break;
            }
        }

        int max_itr = ca.getPointer()+show_elements; //intero che rappresenta il limite destro della finestra di scorrimento
        if(ca.getScrollingSize()<=ca.getPointer()+show_elements){ //vedo se posso portare senza problemi max_itr a pointer + show_elements
            ca.setCap_reached(true);
            max_itr = ca.getScrollingSize();
            starting_point = ca.getPointer();
        }

        String result = "";
        ArrayList<String> tmp_scrolling = new ArrayList<>(ca.getScrolling());
        for (int i = starting_point; i<max_itr; i++){ //e carico il risultato quanto posso
            result = result.concat(tmp_scrolling.get(i)+"\n");
        }

        if(!tmp_scrolling.isEmpty())
            result = result.substring(0, result.length()-1);

        ca.setLongResponse(result);
    }

    private void backwardScrolling(ConnectionAttachment ca){
        ca.setPointer(ca.getPointer()-show_elements); //parto da puntatore = getPointer()-show_elements ; intero che rappresenta il limite sinistro della finestra di scorrimento
        if(ca.getPointer()<0)
            ca.setPointer(0);
        int starting_point = ca.getPointer();

        int max_itr = ca.getPointer()+show_elements; //intero che rappresenta il limite destro della finestra di scorrimento
        if(max_itr >= ca.getScrollingSize()){ //vedo se posso portare senza problemi max_itr a pointer + show_elements
            max_itr = ca.getScrollingSize();
        }
        else{
            ca.setCap_reached(false);
        }

        String result = "";
        ArrayList<String> tmp_scrolling = new ArrayList<>(ca.getScrolling());
        for (int i = starting_point; i<max_itr; i++){
            result = result.concat(tmp_scrolling.get(i)+"\n"); //e carico il risultato quanto posso
        }

        if(!tmp_scrolling.isEmpty())
            result = result.substring(0, result.length()-1);

        ca.setLongResponse(result);
    }

    private void close(SelectionKey key) {
        try {
            System.out.println("Chiusura connessione con " + key.channel());
            key.channel().close();
            key.cancel();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public static void set_BUFFER_SIZE(int BUFFER_SIZ){
        BUFFER_SIZE = BUFFER_SIZ;
    }

    public static void setSocial_network(SocialNetwork social_network) {
        RequestHandler.social_network = social_network;
    }

    public static void setMulticast_adrr(String multicast_adrr) {
        RequestHandler.multicast_adrr = multicast_adrr;
    }

    public static void setMulticast_port(int multicast_port) {
        RequestHandler.multicast_port = multicast_port;
    }

    public static void setShowElements(int show_elements) {
        RequestHandler.show_elements = show_elements;
    }

    public static void setMax_days_without_posts(int max_days_without_posts) {
        RequestHandler.max_days_without_posts = max_days_without_posts;
    }
}


