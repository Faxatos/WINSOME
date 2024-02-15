package server.utils.users;

import java.sql.Timestamp;

public class Transaction {

    private Timestamp time;
    private double wincoin_increment;

    public Transaction(){
        super();
    }
    public Transaction(Timestamp time, double wincoin_increment) {
        this.time = time;
        this.wincoin_increment = wincoin_increment;
    }

    public Timestamp getTime() {
        return time;
    }

    public double getWincoin_increment() {
        return wincoin_increment;
    }
}
