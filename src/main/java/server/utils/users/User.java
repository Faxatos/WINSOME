package server.utils.users;

import client.RMI.RMIClient;
import com.fasterxml.jackson.annotation.JsonIgnore;
import server.utils.Pair;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class User{

    private static SecureRandom random = new SecureRandom();
    private final String username;
    private final byte[] password_hash;
    private final byte[] salt;

    private final static SecretKeyFactory factory;

    static {
        try {
            factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> tags;
    private final Deque<Pair<Integer, Timestamp>> posts; //uso arraylist perché mi interessa ordine, ed è possibile effettuare il reween di post già presenti nel proprio blog

    //hashsets per semplici op. di get
    //da gestire manualmente la sincronizzazione
    private final Set<String> followers; //uso set perché voglio un set stringhe (non duplicate)
    private final Set<String> following;
    @JsonIgnore
    private RMIClient followersCallback;
    private final Wallet wallet;
    @JsonIgnore
    private AtomicBoolean isLogged;

    public User(){ //costruttore per jackson serialization
        super();
        this.username = null;
        this.password_hash = null;
        this.salt = null;
        this.posts = null;
        this.followers = null;
        this.following = null;
        this.followersCallback = null;
        this.wallet = null;
        isLogged = new AtomicBoolean();
    }

    public User(String username, String password, List<String> tags){

        this.username = username;

        this.salt = new byte[16]; //salt di 16 byte
        random.nextBytes(salt); //genero byte casuali
        KeySpec  spec = new PBEKeySpec(password.toCharArray(), salt, 256, 32); //iterationCount parametro che fornisce robustezza alla psw
        try {
            this.password_hash = factory.generateSecret(spec).getEncoded();
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
        this.tags = tags; //parsing string in method register(..)

        this.posts = new LinkedList<>();
        this.followers = new HashSet<>();
        this.following = new HashSet<>();
        this.followersCallback = null;

        this.wallet = new Wallet();
        this.isLogged = new AtomicBoolean(); //initial value = false

    }

    public String getUsername() {
        return username;
    }
    public boolean checkPassword(String password_to_check) {
        String user_psw = new String(password_hash);
        KeySpec spec = new PBEKeySpec(password_to_check.toCharArray(), salt, 256, 32); //iterationCount parametro che fornisce robustezza alla psw
        byte[] password_to_check_byte = null;
        try {
            password_to_check_byte = factory.generateSecret(spec).getEncoded();
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }

        if(password_to_check_byte != null){
            String psw_to_check = new String(password_to_check_byte);
            return psw_to_check.equals(user_psw);
        }
        return false;
    }

    public List<String> getTags() {
        return tags;
    }

    public Wallet getWallet() {
        synchronized (wallet) {
            return wallet;
        }
    }

    public boolean is_logged() {
        return isLogged.get();
    }

    public void set_logged(boolean is_logged) {
        this.isLogged.set(is_logged);
    }

    public boolean have_common_tags(List<String> tags){
        for(String tag : tags){
            if(this.tags.contains(tag)){
                return true;
            }
        }
        return false;
    }

    public ArrayList<String> getFollowing(){
        synchronized (this.following) {
            return new ArrayList<String>(this.following);
        }
    }

    public ArrayList<String> getFollowers(){
        synchronized (this.followers) {
            return new ArrayList<String>(this.followers);
        }
    }

    public ArrayList<Pair<Integer, Timestamp>> getBlog(){
        synchronized (this.posts) {
            return new ArrayList<>(this.posts);
        }
    }

    public int addFollow(String username){
        synchronized (this.following) {
            if(username.equals(this.username))
                return 2; //Non puoi seguire te stesso

            boolean ris = following.add(username);
            if(ris) //ris == true
                return 0; //User seguito con successo
            //else
            return 1; //User già seguito
        }
    }

    public int removeFollow(String username){
        synchronized (this.following) {
            if(username.equals(this.username))
                return 2; //Non puoi smettere di seguire te stesso

            boolean ris = following.remove(username);
            if(ris) //ris == true
                return 0; //User unfollowed con successo
            //else
            return 1; //User già unfollowed
        }
    }

    public void addFollower(String username){
        synchronized (this.followers) {
            //eventuali controlli effettuati nella chiamata addFollow
            followers.add(username);
            try {
                if(followersCallback!=null){
                    //qui possibile race cond se non sincronizzo aggiunta followersCallback
                    followersCallback.newFollower(username);
                }
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public void removeFollower(String username){
        synchronized (this.followers) {
            //eventuali controlli effettuati nella chiamata addFollow
            followers.remove(username);
            try {
                if(followersCallback!=null){
                    //qui possibile race cond se non sincronizzo aggiunta followersCallback
                    followersCallback.newUnfollower(username);
                }
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void setCallback(RMIClient cb){
        synchronized (this.followers) { //sincronizzo con followers
            followersCallback = cb;
        }
    }

    public void removeCallback(){
        synchronized (this.followers) { //sincronizzo con followersW
            followersCallback = null;
        }
    }

    public void addPost(int post){
        synchronized (this.posts){
            posts.addFirst(new Pair<>(post, new Timestamp(System.currentTimeMillis()))); //inserimento in testa
        }
    }

    public void removePost(Integer idPost){
        synchronized (this.posts){

            for (Pair<Integer, Timestamp> single_p: posts) {
                if(single_p.getFirst() == idPost){
                    posts.remove(single_p);
                    break;
                }
            }
        }
    }

    public ArrayList<Integer> getDatePosts(Date feeds_date) {

        Iterator<Pair<Integer, Timestamp>> it;
        boolean exit = false;
        ArrayList<Integer> ret = new ArrayList<>();

        synchronized (this.posts){
            it = this.posts.iterator();

            LocalDate post_ld;
            LocalDate feeds_date_ld = convertToLocalDateViaInstant(feeds_date);;

            while(it.hasNext() && !exit){ //prima ciclo per cercare i post che non sono prima della data parametro
                Pair<Integer, Timestamp> tmp = it.next();
                Date post_date = new Date(tmp.getSecond().getTime());
                post_ld = convertToLocalDateViaInstant(post_date);
                //System.out.println(post_ld + "comparing with "+ convertToLocalDateViaInstant(feeds_date)+ " :" + post_ld.compareTo(convertToLocalDateViaInstant(feeds_date)));

                if(post_ld.compareTo(feeds_date_ld) >= 0){//scorro i posts in avanti fino a quando non trovo uno che è più vecchio o dello stesso giorno di feeds_date
                    if(post_ld.compareTo(feeds_date_ld) == 0){ //caso in cui il punto sia uguale alla data ricercata
                        ret.add(tmp.getFirst());
                    }
                    exit = true;
                }
            }
            if(exit){ //se abbiamo trovato il punto
                exit = false; //assegno di nuovo valore ad exit per uscire da prossima iterazione

                while(it.hasNext() && !exit){ //poi ciclo per cercare i post che sono nella data parametro (oltre al primo già trovato)
                    Pair<Integer, Timestamp> tmp = it.next();
                    Date post_date = new Date(tmp.getSecond().getTime());
                    post_ld = convertToLocalDateViaInstant(post_date);
                    if(post_ld.compareTo(feeds_date_ld) == 0) //abbiamo trovato altro post
                        ret.add(tmp.getFirst());
                    else //se non troviamo più valori validi, allora sappiamo che per costruzione anche i prossimi non saranno più validi
                        exit = true;
                }
            }
        }

        return ret;
    }

    public boolean follows(String auth_following) {
        synchronized (following){
            return following.contains(auth_following);
        }
    }

    public void insertTransactionIntoWallet(Transaction trans) {
        synchronized (wallet){
            wallet.addTransaction(trans);
        }
    }

    private LocalDate convertToLocalDateViaInstant(Date dateToConvert) {
        return dateToConvert.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    public String getFormattedTags() {
        String ret = "";
        for(String tmp_tag : tags){
            ret = ret.concat(tmp_tag+"|");
        }
        if(!tags.isEmpty()){ //non dovrebbe mai accadere, ma aggiungo controllo per evitare IndexOutOfBoundsException
            ret = ret.substring(0, ret.length()-1);
        }
        return ret;
    }
}
