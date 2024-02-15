package server;

import client.RMI.RMIClient;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.SerializationFeature;
import server.RMI.RMIServer;
import server.requests.RequestHandler;
import server.utils.Pair;
import server.utils.RewardsCalculator;
import server.utils.posts.Comment;
import server.utils.posts.Post;
import server.utils.users.Transaction;
import server.utils.users.User;
import server.utils.users.Wallet;

import java.io.*;
import java.rmi.RemoteException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class SocialNetwork implements RMIServer {

    private final int min_post_title_len;
    private int max_post_title_len;
    private final int min_post_content_len;
    private int max_post_content_len;

    private final String path_usersdb;
    private final String path_postsdb;

    private final AtomicInteger id_counter = new AtomicInteger(0);
    private final ConcurrentHashMap<String, User> users;
    private final ConcurrentHashMap<Integer, Post> posts;

    private final Thread rew_calc_thread;

    public SocialNetwork(Properties props, int BUFF_SIZE){
        users = new ConcurrentHashMap<>();
        posts = new ConcurrentHashMap<>();

        this.path_usersdb = props.getProperty("PATH_USERSDB", "./usersDB.json");
        this.path_postsdb = props.getProperty("PATH_POSTSDB", "./postsDB.json");

        loadFromDB();

        this.min_post_title_len = Integer.parseInt(props.getProperty("MIN_POSTTITLELEN", "4"));
        this.max_post_title_len = Integer.parseInt(props.getProperty("MAX_POSTTITLELEN", "100"));
        this.min_post_content_len = Integer.parseInt(props.getProperty("MIN_POSTCONTENTLEN", "1"));
        this.max_post_content_len = Integer.parseInt(props.getProperty("MAX_POSTCONTENTLEN", "400"));

        if(max_post_title_len + max_post_content_len > BUFF_SIZE){
            this.max_post_title_len = (BUFF_SIZE - 16)/5;
            this.max_post_content_len = 4*(BUFF_SIZE - 16)/5;
        }

        int multicast_port = Integer.parseInt(props.getProperty("MCASTPORT", "40000"));
        RequestHandler.setMulticast_port(multicast_port);
        String multicast_adrr = props.getProperty("MULTICAST", "239.255.0.0");
        RequestHandler.setMulticast_adrr(multicast_adrr);
        float creator_pg = Float.parseFloat(props.getProperty("CREATORPG", "0.7"));
        if(creator_pg < 0 || creator_pg > 1){ //controllo valori limite di una percentuale
            creator_pg = 0.7f; //se non vengono rispettati assegno un valore di default
        }
        int waiting_time_next_itr = Integer.parseInt(props.getProperty("REFRESHTIME", "3600"));

        RewardsCalculator rew_calc = new RewardsCalculator(multicast_adrr, multicast_port, creator_pg, waiting_time_next_itr, this, this.posts);
        this.rew_calc_thread = new Thread(rew_calc);
        rew_calc_thread.start();

    }

    //controlla da set di users se è presente username
    //in caso lo sia, restituisci messaggio positivo (se this.user=user e this.psw = psw) e rendi utente loggato
    //altrimenti messaggio di errore
    public Pair<User, Integer> login(String username, String password){

        if(users.containsKey(username)){ //se esiste utente
            User tmp_user = users.get(username);
            if (tmp_user.checkPassword(password.trim())) { //e se password combacia
                if (!tmp_user.is_logged()) {
                    tmp_user.set_logged(true);
                    return new Pair<>(tmp_user, 0); //login succesfull
                }
                return new Pair<>((User)null, 2); //login già effettuato altrove
            }
            return new Pair<>((User)null, -1); //psw errata
        }
        return new Pair<>((User)null, -1); //user non trovato
    }

    public void logout(User app){
        //app sicuramente loggato, altrimenti metodo non richiamabile in HandeRequest
        app.set_logged(false);
    }

    //restituisce lista di utenti che hanno tags in comune con richiedente
    public ArrayList<String> listUsers(List<String> tags, String app, int show_elements){

        ArrayList<String> ret = new ArrayList<>();

        for (User tmp : users.values()) { //ciclo per tutti gli utenti nella lista
            if(tmp.have_common_tags(tags) && !tmp.getUsername().equals(app)){ //controllo anche che non venga inserito applicant stesso
                ret.add(tmp.getUsername()+"|"+tmp.getFormattedTags()); //e formatto risultato per scrolling
            }
            if(ret.size()>show_elements*3) //ci fermiamo quando troviamo abbastanza elementi
                break;
        }
        return ret;
    }

    public ArrayList<String> listFollowing(User app, int show_elements){ //restituisce lista degli utenti di cui è follower
        ArrayList<String> unformatted_following = app.getFollowing();

        ArrayList<String> ret = new ArrayList<>();

        for(String foll : unformatted_following){ //ciclo per tutti gli utenti nella lista
            User cb_tmp_follower = users.get(foll);
            String formatted_tags = cb_tmp_follower.getFormattedTags();
            ret.add(foll+"|"+formatted_tags); //e formatto risultato per scrolling
            if(ret.size() > show_elements*3){ //ci fermiamo quando troviamo abbastanza elementi
                break;
            }
        }

        return ret;
    }

    //inserisce username nei followers del richiedente
    //return: messaggio di successo o eventuale messaggio di errore (già segui/non esiste)
    public int followUser(User app, String username){

        User new_followed = users.get(username);
        if(new_followed != null){
            int ris = app.addFollow(username);
            if(ris == 0) //se op di follow va a buon fine
                new_followed.addFollower(app.getUsername()); //aggiorno anche followers di new_followed
            return ris;
            // ris = 0 //User seguito con successo
            // ris = 1 //User già seguito
            // ris = 2 //Non puoi seguire te stesso
        }
        else
            return -1; //User non trovato
    }

    //elimina username nel followers del richiedente
    //return: messaggio di successo o eventuale messaggio di errore (non segui)
    public int unfollowUser(User app, String username){

        User new_unfollowed = users.get(username);
        if(new_unfollowed != null){
            int ris = app.removeFollow(username);
            if(ris == 0) //se op di follow va a buon fine
                new_unfollowed.removeFollower(app.getUsername()); //aggiorno anche followers di new_followed
            return ris;
            // ris = 0 //User unfollowed con successo
            // ris = 1 //User già unfollowed
            // ris = 2 //Non puoi smettere di seguire te stesso
        }
        else
            return -1; //User non trovato
    }

    //return post pubblicati da richiedente
    public ArrayList<String> viewBlog(User app, int show_elements){

        ArrayList<Pair<Integer, Timestamp>> blog = app.getBlog();
        ArrayList<String> ret = new ArrayList<>();

        for (Pair<Integer, Timestamp> p_ids : blog) { //ciclo per tutti i post del blog
            Post p = posts.get(p_ids.getFirst());
            if(p != null){
                ret.add(p.getID()+"|"+p.getAuthor()+"|"+p.getTitle()); //e formatto risultato per scrolling
            }
            else{//gestisco caso in cui autore ha eliminato il post, ma il rewin è rimasto nel blog di app
                app.removePost(p_ids.getFirst());
            }
            if(ret.size() > show_elements*3) //ci fermiamo quando troviamo abbastanza elementi
                break;
        }
        return ret;
    }

    //inserisce nuovo post
    public int createPost (User app, String titolo, String contenuto){

        if(titolo.length() > min_post_title_len && titolo.length() < max_post_title_len){
            if(contenuto.length() > min_post_content_len && contenuto.length() < max_post_content_len){
                //se va tutto bene
                int new_id = id_counter.incrementAndGet(); //incremento id atomico e lo recupero
                Post new_post = new Post(new_id, app.getUsername(), titolo, contenuto);

                posts.put(new_id, new_post); //quindi aggiunto post
                app.addPost(new_id); //anche nel blog dell'utente

                return 0; //post inserito correttamente
            }
            else{
                return 2; //contenuto troppo lungo/corto
            }
        }
        else{
            return 1; //titolo troppo lungo/corto
        }
    }

    //genera feed, che rimarrà temporaneamente salvato fino a nuova richiesta di feed (equivalente a refresh)
    public void showFeed (User app, Date feeds_date, ArrayList<Post> latest_feed, int days_without_posts, int max_days_without_posts, int show_elements){
        ArrayList<String> followers = app.getFollowing();
        ArrayList<Integer> id_posts = new ArrayList<>();

        int prev_len = latest_feed.size();

        for (String app_follower : followers) { //possibile miglioramento: permetto periodi di Date, in modo da ridurre notevolemnte le chiamate di getFollowers() e getDatePosts(). Quanto lunghi periodi? sarebbe necessario fare studio
            id_posts.addAll(users.get(app_follower).getDatePosts(feeds_date));
        }

        if(id_posts.size() == 0) //se mi trovo un giorno senza posts, vado a contarlo
            days_without_posts++;
        else{
            days_without_posts = 0;
        }

        for (int id : id_posts) {
            Post p = posts.get(id);
            latest_feed.add(p);
        }

        latest_feed.subList(prev_len, latest_feed.size()).sort(new Comparator<Post>() { //effettuo il sort in base a timestamp solo dei nuovi post inseriti
            @Override
            public int compare(Post post1, Post post2) {

                return post2.getCreationTime().compareTo(post1.getCreationTime());
            }
        });

        //se non trovo posts per 3 giorni consecutivi, oppure la size del feed è > showelements * 3 mi fermo
        if(latest_feed.size() < show_elements * 3 && days_without_posts < max_days_without_posts){ //condizioni per mandare avanti ricorsione
            Calendar shiftDay = Calendar.getInstance();
            shiftDay.setTime(feeds_date);
            shiftDay.add(Calendar.DATE, -1);
            showFeed(app, shiftDay.getTime(), latest_feed, days_without_posts, max_days_without_posts, show_elements);
        }
    }

    //restituisce post richiesto formattato per scrolling
    public String showPost(int idPost){
        Post tmp_post = posts.get(idPost);
        if(tmp_post != null)
            return tmp_post.getTitle()+"|"+tmp_post.getContent()+"|"+tmp_post.getUpVotes()+"|"+tmp_post.getDownVotes()+"|";
        return null;
    }

    //restituisce post richiesto
    public ArrayList<String> showPostComments(int idPost, int show_elements){
        Post tmp_post = posts.get(idPost);
        ArrayList<Comment> comms = tmp_post.getComments();
        ArrayList<String> ret = new ArrayList<>();

        for (Comment tmp_comm : comms) { //ciclo per tutti i commenti del post
            ret.add(tmp_comm.getAuthor()+"|"+tmp_comm.getComment()); //e formatto risultato per scrolling
            if(ret.size() > show_elements*3) //ci fermiamo quando troviamo abbastanza elementi
                break;
        }
        return ret;
    }

    //si cancella Post indicato solo se richiedente è autore
    public int deletePost(User app, int idPost){
        String app_username = app.getUsername();
        Post tmp_post = posts.get(idPost);
        if(tmp_post == null){
            return -1; //post non trovato
        }
        if(tmp_post.getAuthor().equals(app_username)) {
            posts.remove(idPost);
            app.removePost(idPost); //lo rimuovo anche dal blog dell'autore. la rimozione dagli altri blog avverrà quando
            //iterandoli ci accorgiamo che posts.get(idPost) = null
            return 0;//richiesta andata a buon fine
        }
        return 1; //richiesta ricevuta da non autore
    }

    //si ripubblica un post presente nel proprio feed nel blog del richiedente
    public int rewinPost(User app, int idPost){
        Post tmp_post = posts.get(idPost);
        if(tmp_post == null) {
            return -1; //post non trovato
        }
        if(tmp_post.getAuthor().equals(app.getUsername())){
            return 2; //app è autore del post
        }
        if(app.follows(tmp_post.getAuthor())){ //se post è nel feed
            app.addPost(idPost);
            return 0; //rewin eseguito correttamente
        }
        else
            return 1; //post non in feed
    }

    //si assegna voto ad un post solo se è presente nel proprio feed, e se il voto non è già stato espresso
    public int ratePost(User app, int idPost, boolean vote){
        Post tmp_post = posts.get(idPost);
        if(tmp_post == null) {
            return -1; //post non trovato
        }
        if(tmp_post.getAuthor().equals(app.getUsername())){
            return 3; //app è autore del post
        }
        if(app.follows(tmp_post.getAuthor())){ //se post è nel feed
            if(tmp_post.addVote(app.getUsername(), vote))
                return 0; //voto aggiunto correttamente
            return 1; //voto già effettuato
        }
        return 2; //post non in feed
    }

    //si inserisce un commento al post solo se quest'ultimo è presente nel proprio feed
    public int addComment(User app, int idPost, String commento){
        Post tmp_post = posts.get(idPost);
        if(tmp_post == null) {
            return -1; //post non trovato
        }
        else if(app.follows(tmp_post.getAuthor())){ //se post è nel feed
            tmp_post.addComment(new Comment(app.getUsername(), commento));
            return 0; //commento aggiunto correttamente
        }
        else if(tmp_post.getAuthor().equals(app.getUsername())){
            return 2; //app è autore del post
        }
        return 1; //post non in feed
    }

    //si restituisce valore wallet e storia transizioni
    public Pair<Float, ArrayList<Transaction>> getWallet(User app){
        Wallet app_wallet = app.getWallet();
        Float wincoin = app_wallet.getWincoin();
        ArrayList<Transaction> trans = app_wallet.getTransactions();

        return new Pair<>(wincoin, trans);
    }

    public void addTransaction(String username, Transaction trans){
        User app = users.get(username);
        app.insertTransactionIntoWallet(trans);
    }

    //restituisce valore wallet in btc
    public Float getWalletInBitcoin(User app){
        Wallet app_wallet = app.getWallet();
        float wincoins = app_wallet.getWincoin();
        Float bitcoins = RewardsCalculator.getWalletBitcoinsValue(RewardsCalculator.getWincoinValue(), wincoins);
        return bitcoins;
    }

    //restituisce valore wallet in btc
    public Float getWalletValue(User app){
        Wallet app_wallet = app.getWallet();
        return app_wallet.getWincoin();
    }

    //restituisce valore wallet in btc
    public ArrayList<String> getWalletTransactions(User app, int show_elements){
        Wallet app_wallet = app.getWallet();
        ArrayList<Transaction> transactions = app_wallet.getTransactions();
        ArrayList<String> ret = new ArrayList<>();

        for (Transaction tmp_trans : transactions) {
            ret.add(tmp_trans.getWincoin_increment()+"|"+tmp_trans.getTime());
            if(ret.size() > show_elements*3)
                break;
        }
        return ret;
    }


    //a differenza degli altri metodi (dove RequestHandler effettuava il parsing della stringa passata da client),
    //questi metodi vengono richiamati tramite tecnologia RMI, e quindi è necessario effettuare controlli sui parametri
    @Override
    public int signUp(String username, String password, List<String> tags) {
        if(username == null || username.length() < 4 || username.length() > 20 || username.contains("|"))
            return 1; //username scorrettamente formattato o vuota
        if(password == null || password.length() < 4 || password.length() > 20 || password.contains("|")) {
            return 2; //password scorrettamente formattata o vuota

        }if(tags.size() > 5 || tags.size() == 0) {
            return 4; //troppi o troppi pochi tags
        }
        for(String tag : tags){
            if(tag.contains("|"))
                return 3; //tag contiene |
        }

        User new_user = new User(username, password, tags);
        if(users.putIfAbsent(username, new_user) == null)
            return 0; //nuovo user accettato
        return -1; //user con quell'username già esistente

    }

    //per i seguenti 2 metodi viene richiesta password dato che è possibile la manomissione lato client per
    //la richiesta di callback da parte di utente diverso da quello identificato da username
    @Override
    public int registerCallback(RMIClient callback, String username, String password) {
        if(username == null)
            return 1; //errore username vuoto
        if(password == null)
            return 2; //errore password vuota
        if(callback == null)
            return 3; //errore callback vuoto

        User new_callbacker = users.get(username); //recupero utente che ha richiesto login
        if(new_callbacker != null && new_callbacker.checkPassword(password)){ //se callback è non null e la password corrisponde (vedi relazione per necessità password)
            ArrayList<String> cb_followers = new_callbacker.getFollowers(); //prendo followers
            new_callbacker.setCallback(callback); //setto callback

            ArrayList<String> cb_followers_formatted = new ArrayList<>(); //formatto i followers (aggiungendo tags)
            for(String cb_tmp_nick : cb_followers){
                User cb_tmp_follower = users.get(cb_tmp_nick);
                String formatted_tags = cb_tmp_follower.getFormattedTags();
                cb_followers_formatted.add(cb_tmp_nick+"|"+formatted_tags); //formatto i followers restituiti per renderli pronti a scrolling locale del client
            }

            try {
                callback.setPrevFollowers(cb_followers_formatted); //e effettuo la prima chiamata per sincronizzare followers server con quelli di RMIImplementation
            } catch (RemoteException e) {
                e.printStackTrace();
                return 5; //remote exception
            }
            System.out.println("callback impostato per l'utente " + username);
            return 0; //callback registrato con successo

        }
        else
            return -1; //dati manomessi

    }

    @Override
    public int unregisterCallback(RMIClient callback, String username, String password) {
        if(username == null)
            return 1; //errore username vuoto
        if(password == null)
            return 2; //errore password vuota
        if(callback == null)
            return 3; //errore callback vuoto

        User old_callbacker = users.get(username);
        if(old_callbacker != null && old_callbacker.checkPassword(password)){
            old_callbacker.removeCallback();
            System.out.println("callback rimosso per l'utente " + username);
            return 0; //callback rimosso con successo
        }
        else
            return -1; //dati manomessi
    }

    private void loadFromDB() {

        File users_db = new File(path_usersdb);
        File posts_db = new File(path_postsdb);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        if(users_db.exists() && posts_db.exists()){ //se esistono entrambi i file

            try {
                List<User> reloaded_users = objectMapper.readValue(users_db, new TypeReference<List<User>>(){});

                for (User us : reloaded_users) { //prima carico gli users
                    String us_username = us.getUsername();
                    users.put(us_username, us);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try {
                List<Post> reloaded_posts = objectMapper.readValue(posts_db, new TypeReference<List<Post>>(){});

                int highest_id = 0;

                for (Post ps : reloaded_posts) { //poi i posts
                    Integer getID = ps.getID();
                    if(highest_id < getID)
                        highest_id = getID;
                    ps.setLast_update(new Timestamp(System.currentTimeMillis()));
                    posts.put(getID, ps);
                }

                id_counter.set(highest_id); //e mi vado anche a salvare il post di id più alto (per ripartire da quello durante la creazione di nuovi posts)
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        else{
            System.out.println("Non è stato trovato uno stato precedente del sistema");
        }
    }

    public void uploadDB() {

        rew_calc_thread.interrupt();
        try {
            rew_calc_thread.join(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        File users_db = new File(path_usersdb);
        if (users_db.exists())
            users_db.delete();

        File posts_db = new File(path_postsdb);
        if (posts_db.exists())
            posts_db.delete();

        byte[] bytes;

        ObjectMapper object_mapper = new ObjectMapper();

        //per serializzare/deserializzare LocalDate; passato successivamente a timestamps
        //object_mapper.registerModule(new JavaTimeModule());

        //In some cases where, for example, you might not actually be able to modify the source code directly – we need to configure the way Jackson deals with non-public fields from the outside.
        //
        //That kind of global configuration can be done at the ObjectMapper level, by turning on the AutoDetect function to use either public fields or getter/setter methods for serialization, or maybe turn on serialization for all fields
        object_mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        object_mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        object_mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET,false);
        object_mapper.configure(SerializationFeature.INDENT_OUTPUT, true);

        try (FileWriter fr = new FileWriter(users_db)){ //prima scrivo users

            Set<Map.Entry<String, User>> entry_set = users.entrySet();
            Iterator<Map.Entry<String, User>> itr = entry_set.iterator();

            fr.write("[\n");
            while(itr.hasNext()){ //itero per gli elementi nella collezione
                User to_json = itr.next().getValue();
                to_json.set_logged(false);
                object_mapper.writeValue(fr, to_json);
                if(itr.hasNext())
                    fr.write(",\n");
            }
            fr.write("\n]");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (FileWriter fr = new FileWriter(posts_db)){ //poi scrivo posts

            Set<Map.Entry<Integer, Post>> entry_set = posts.entrySet();
            Iterator<Map.Entry<Integer, Post>> itr = entry_set.iterator();

            fr.write("[\n");
            while(itr.hasNext()){ //itero per gli elementi nella collezione
                object_mapper.writeValue(fr, itr.next().getValue());
                if(itr.hasNext())
                    fr.write(",\n");
            }
            fr.write("\n]");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
