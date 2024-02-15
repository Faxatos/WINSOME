package client.RMI;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface RMIClient extends Remote {
    void setPrevFollowers(ArrayList<String> prevFollowers) throws RemoteException;
    void newFollower(String username) throws RemoteException;
    void newUnfollower(String username) throws RemoteException;
}
