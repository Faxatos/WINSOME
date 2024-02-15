package server.utils.posts;

import java.sql.Timestamp;

public class Comment {

    private final String author;
    private final String comment;
    private final Timestamp timestp;

    public Comment(){
        super();
        author = null;
        comment = null;
        timestp = null;
    }
    
    public Comment(String author, String comment){
        this.author = author;
        this.comment = comment;
        this.timestp = new Timestamp(System.currentTimeMillis());
    }

    public Timestamp getTimestampCreation() {
        return timestp;
    }

    public String getComment() {
        return comment;
    }

    public String getAuthor() {
        return author;
    }

}
