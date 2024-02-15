package server.utils.posts;

import server.utils.users.User;

import java.sql.Timestamp;

public class Vote{

    private boolean vote; //true stands for upvote, false stands for downvote
    private Timestamp timestp; //Timestamp votazione post

    public Vote(User user, boolean vote){
        this.vote = vote;
        this.timestp = new Timestamp(System.currentTimeMillis());
    }
}
