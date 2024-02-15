package client.RMI;

import java.util.ArrayList;
import java.util.HashSet;

public class RMIImplementation implements RMIClient{
    private final HashSet<String> followers = new HashSet<>();

    public RMIImplementation() {
    }

    @Override
    public synchronized void setPrevFollowers(ArrayList<String> prevFollowers) {
        this.followers.clear();
        this.followers.addAll(prevFollowers);
    }

    @Override
    public synchronized void newFollower(String username) {
        this.followers.add(username);
    }

    @Override
    public synchronized void newUnfollower(String username) {
        this.followers.remove(username);
    }

    public synchronized ArrayList<String> getFormattedFollowers(){
        return new ArrayList<>(this.followers);
    }
}
