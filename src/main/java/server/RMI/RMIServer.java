package server.RMI;

import client.RMI.RMIClient;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface RMIServer extends Remote {
    int signUp(String username, String password, List<String> tags) throws RemoteException;
    int registerCallback(RMIClient callback, String username, String password) throws RemoteException;
    int unregisterCallback(RMIClient callback, String username, String password) throws RemoteException;
}
