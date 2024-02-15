package server.utils.posts;

import com.fasterxml.jackson.annotation.JsonIgnore;
import server.utils.Pair;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Post{

    private static DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    private final String author;
    private final String title;
    private final String content;
    private final Timestamp creation_timestmp;
    private final Integer idPost;

    private final HashMap<String, Boolean> voters = new HashMap<>();
    private final Deque<Comment> comments = new LinkedList<>();

    //dati ridontanti, ma necessari per alleggerire operazione di restituzione numero upvotes/downvotes
    private final AtomicInteger upvotes = new AtomicInteger(0);
    private final AtomicInteger downvotes = new AtomicInteger(0);

    private Integer iterations = 0;

    private HashSet<String> new_voters = new HashSet<>();

    private int old_upvotes = 0;
    private int old_downvotes = 0;

    @JsonIgnore
    private Timestamp last_update;

    public Post(){
        super();

        author = null;
        title = null;
        content = null;
        creation_timestmp = null;
        idPost = null;
    }
    public Post(int id, String author, String title, String content){

        this.author = author;
        this.title = title;
        this.content = content;
        this.creation_timestmp = new Timestamp(System.currentTimeMillis());

        this.idPost = id;
        this.last_update = new Timestamp(System.currentTimeMillis());
    }

    public int getID(){
        return this.idPost;
    }

    public Timestamp getCreationTime(){
        return creation_timestmp;
    }

    public String getAuthor() {
        return author;
    }
    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public ArrayList<Comment> getComments(){
        synchronized (comments){
            return new ArrayList<Comment>(comments);
        }
    }

    public int getUpVotes() {
        return upvotes.get();
    }
    public int getDownVotes() {
        return downvotes.get();
    }

    public boolean addVote(String voter, boolean vote){

        synchronized (voters){
            Boolean ret = voters.putIfAbsent(voter, vote);
            if(ret == null){
                new_voters.add(voter);
                if(vote)
                    upvotes.getAndIncrement();
                else
                    downvotes.getAndIncrement();
                return true; //voto aggiunto correttamente
            }
            return false; //voto già effettuato
        }
    }

    public void addComment(Comment comm){
        synchronized (comments){
            comments.addFirst(comm);
        }
    }

    public synchronized Pair<Float, HashSet<String>> calculateReward() {

        this.iterations++;

        //hashmap<username, n_votes_per_user>
        HashMap<String, Integer> commenters_to_reward = new HashMap<>();

        float wincoins_ret = 0;

        for (Comment comm : this.comments) { //valuto commenti
            if(comm.getTimestampCreation().after(last_update)){ //se è un commento inserito dopo l'ultimo calcolo ricompensa
                String comm_author = comm.getAuthor();
                if(!commenters_to_reward.containsKey(comm_author)){
                    commenters_to_reward.put(comm_author, 1); //allora lo inserisco nella mappa
                }else{
                    commenters_to_reward.put(comm_author, commenters_to_reward.get(comm_author) + 1); //(o incremento di 1 se è un utente che ha già commentato)
                }
            }
            else //gli altri sono commenti già valutati
                break;
        }

        last_update = new Timestamp(System.currentTimeMillis());

        //parte commenti
        for (String interactors: commenters_to_reward.keySet()) {
            wincoins_ret += 2 / (1 + Math.pow(Math.E, -(commenters_to_reward.get(interactors) - 1))); //aggiungo a wincoins_ret ogni valore della parte destra dell'equazione
            //andando a punire chi inseriva tanti commenti per fare soldi
        }

        wincoins_ret = (float) (Math.log(wincoins_ret + 1) / iterations);

        //parte voti
        int current_upvotes = upvotes.get() - old_upvotes;
        int current_downvotes = downvotes.get() - old_downvotes;

        if(current_upvotes - current_downvotes > 0){
            wincoins_ret += Math.log(current_upvotes - current_downvotes + 1) / iterations; //aggiungo a wincoins_ret la parte sinistra dell'equazione
        }

        old_upvotes = upvotes.get();
        old_downvotes = downvotes.get();

        HashSet<String> ret_rewarded = new HashSet<>(commenters_to_reward.keySet()); //aggiungo quindi gli utenti che hanno commentato
        ret_rewarded.addAll(new_voters); //e quelli che hanno votato (che mi salvavo mano a mano quando)(set non permette duplicati)
        new_voters.clear();

        return new Pair<Float, HashSet<String>>(wincoins_ret, ret_rewarded); //restituisco guadagno wincoin per post, e chi deve essere ricompensato
    }

    public void setLast_update(Timestamp last_update_from_sv){
        last_update = last_update_from_sv;
    }
}
