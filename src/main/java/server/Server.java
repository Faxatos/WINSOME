package server;

import server.RMI.RMIServer;
import server.requests.RequestHandler;
import server.requests.ServerInputScanner;
import server.utils.ConnectionAttachment;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Server {

    private final int BUFFER_SIZE;
    private final String server_adrr;
    private final int tcp_port;

    private final String stub_name;

    private Selector selector;
    private final SocialNetwork socialNetwork;

    private final Registry registry;

    private final ExecutorService tp;

    public Server(Properties props, SocialNetwork socialNetwork, int BUFFER_SIZE){

        int tp_size = Integer.parseInt(props.getProperty("WORKINGTHREADS", "32"));
        tp = Executors.newFixedThreadPool(tp_size);

        this.BUFFER_SIZE = BUFFER_SIZE;

        this.tcp_port = Integer.parseInt(props.getProperty("TCPPORT", "6000"));
        this.server_adrr = props.getProperty("SERVER", "127.0.0.1");

        this.socialNetwork = socialNetwork;
        RequestHandler.setSocial_network(this.socialNetwork);

        RequestHandler.setShowElements(Integer.parseInt(props.getProperty("SHOWELEMENTS", "25")));
        RequestHandler.set_BUFFER_SIZE(BUFFER_SIZE);

        int days_without_posts = Integer.parseInt(props.getProperty("DAYSWITHOUTPOSTS", "3"));
        RequestHandler.setMax_days_without_posts(days_without_posts);

        this.stub_name = props.getProperty("STUBNAME", "winsomeStub");
        this.registry = activateRMI(Integer.parseInt(props.getProperty("REGPORT", "7000")), props.getProperty("REGHOST", "7000"), socialNetwork, this.stub_name);

    }

    public void start() {
        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()){

            ServerSocket ss = serverChannel.socket();
            InetSocketAddress address = new InetSocketAddress(server_adrr, this.tcp_port);
            ss.bind(address);

            selector = Selector.open();
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.printf("Server: in attesa di connessioni sulla porta %d\n", this.tcp_port);

            AtomicBoolean running = new AtomicBoolean(true);
            Thread sysInReader = new Thread(new ServerInputScanner(running, selector));
            sysInReader.start();

            while(running.get()){
                if (selector.selectNow() == 0) { //op. selectNow non bloccante per verificare condizione while anche quando nessun channel è pronto
                    continue;
                }
                Iterator<SelectionKey> iter = selector.selectedKeys().iterator(); //iteratore dell'insieme delle chiavi corrispondenti a canali pronti

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();
                    if (key.isAcceptable()) { //avvio routine di connessione client
                        acceptSocket(key);
                    }
                    else if (key.isReadable() || key.isWritable()) {
                        RequestHandler req = new RequestHandler(key, selector);
                        key.interestOps(0); //da capire
                        tp.execute(req); //mando i dati da processare al thread pool
                    }
                }
            }

            try {
                registry.unbind(stub_name);
                UnicastRemoteObject.unexportObject(socialNetwork, true);
            } catch (RemoteException | NotBoundException e) {
                e.printStackTrace();
            }

            tp.shutdown();
            try {
                if(!tp.awaitTermination(60, TimeUnit.SECONDS)){
                    System.out.println("Thread pool not terminated");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }

    private void acceptSocket(SelectionKey key){
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel c_channel;

        try {
            c_channel = server.accept();
            c_channel.configureBlocking(false);
            c_channel.register(selector, SelectionKey.OP_READ, new ConnectionAttachment(BUFFER_SIZE));
            System.out.println("Nuova connessione dal client: " + c_channel.getRemoteAddress());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //metodo per esportare RMI implementation server
    private Registry activateRMI(int port, String host, SocialNetwork sn, String stubName) {
        try {
            RMIServer stub = (RMIServer) UnicastRemoteObject.exportObject(sn, 0); //scelta porta anonima
            LocateRegistry.createRegistry(port);
            Registry registry = LocateRegistry.getRegistry(host, port);
            registry.rebind(stubName, stub);
            System.out.println("RMI activated");
            return registry;
        }
        catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}
