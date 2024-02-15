package server.utils.users;

import java.util.ArrayList;

public class Wallet {

    private float wincoin;
    private final ArrayList<Transaction> transactions;

    //public Wallet
    public Wallet(){
        this.wincoin = 0;
        this.transactions = new ArrayList<>();
    }

    public float getWincoin() {
        return wincoin;
    }

    public ArrayList<Transaction> getTransactions() {
        synchronized (transactions){
            return new ArrayList<>(transactions);
        }
    }

    public synchronized void addTransaction(Transaction trans){
        transactions.add(trans);
        wincoin += trans.getWincoin_increment();
    }
}
