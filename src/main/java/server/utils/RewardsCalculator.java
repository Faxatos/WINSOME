package server.utils;

import server.utils.posts.Post;
import server.SocialNetwork;
import server.utils.users.Transaction;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

public class RewardsCalculator implements Runnable{

    private final InetAddress multicast_adrr;
    private final int multicast_port;
    private final float creator_pg;
    private final long waiting_time_next_itr;

    private final SocialNetwork socialNetwork;

    private final ConcurrentHashMap<Integer, Post> posts;

    public RewardsCalculator(String multicast_adrr, int multicast_port, float creator_pg, long waiting_time_next_itr, SocialNetwork socialNetwork, ConcurrentHashMap<Integer, Post> posts){
        try {
            this.multicast_adrr = InetAddress.getByName(multicast_adrr);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        this.multicast_port = multicast_port;
        this.creator_pg = creator_pg;
        this.waiting_time_next_itr = waiting_time_next_itr;

        this.socialNetwork = socialNetwork;
        this.posts = posts;
    }

    @Override
    public void run() {
        DatagramSocket sender = null;
        try {
            sender = new DatagramSocket();

            while (true) {
                try {
                    Thread.sleep(waiting_time_next_itr*1000);
                } catch (InterruptedException e) {
                    System.out.println("Distributore di ricompense interrotto");
                    sender.close();
                    break;
                }

                HashMap<String, Float> interactors_rewards = new HashMap<>();

                for (Post post : posts.values()) {
                    Pair<Float, HashSet<String>> reward_return = post.calculateReward(); //da qui ottengo incremento wincoins + gente che deve essere ricompensata

                    Float wincoin_reward = reward_return.getFirst();
                    if (wincoin_reward > 0) { //if wincoin_reward == 0 -> non ci sono state nuove interazioni
                        HashSet<String> post_interactors = reward_return.getSecond();

                        String author = post.getAuthor();
                        //prima agginugo Transaction ad autore (con la sua fetta da config file)
                        socialNetwork.addTransaction(author, new Transaction(new Timestamp(System.currentTimeMillis()), wincoin_reward*creator_pg));

                        float interactor_pg = (1 - creator_pg);
                        int interactors_num = post_interactors.size();
                        //poi per ogni persona che ha interagito
                        for (String interactor : post_interactors) { //ridondante? forse. me ne sono accorto a 50 min dalla consegna, quindi non proverò :)
                            if (!interactors_rewards.containsKey(interactor)) {
                                interactors_rewards.put(interactor, (wincoin_reward*interactor_pg)/interactors_num);
                            } else {
                                interactors_rewards.put(interactor, interactors_rewards.get(interactor) + (wincoin_reward*interactor_pg)/interactors_num);
                            }
                        }
                    }
                }
                //poi per ogni persona che ha interagito
                for (String interactor : interactors_rewards.keySet()) {
                    float increment = interactors_rewards.get(interactor);
                    // agginugo Transaction ad interactor (con la sua fetta da config file)
                    socialNetwork.addTransaction(interactor, new Transaction(new Timestamp(System.currentTimeMillis()), increment));
                }
                // mando messaggio in multicast di fine update
                if (multicast_adrr != null) {
                    try {
                        byte[] message = "Server: Portafogli aggiornati".getBytes();
                        DatagramPacket packet = new DatagramPacket(message, message.length, multicast_adrr, multicast_port);
                        System.out.println("Mando notifica di aggiornamento portafogli");
                        sender.send(packet);
                    } catch (SocketException e) {
                        System.out.println("Errore nella creazione del socket");
                    } catch (IOException e) {
                        System.out.println("Errore nell'invio del pacchetto di notifica");
                    }
                }
            }
        } catch (SocketException e) {
            System.out.println("Creazione socket non riuscita, riavviare il server per riprovare");
        }
    }

    public static int getWincoinValue(){
        try {
            // Create a neat value object to hold the URL
            URL url = new URL("https://www.random.org/integers/?num=1&min=10&max=100&col=1&base=10&format=plain&rnd=new");

            // This line makes the request
            InputStream responseStream = null;

            // Open a connection(?) on the URL(?) and cast the response(??)
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Now it's "open", we can set the request method, headers etc.
            connection.setRequestProperty("accept", "application/json");

            responseStream = connection.getInputStream();

            // Finally we have the response
            byte[] result_bytes = new byte[5];
            responseStream.read(result_bytes);

            String res = new String(result_bytes, StandardCharsets.UTF_8);
            return Integer.parseInt(res.trim()); //wincoin value
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Float getWalletBitcoinsValue(int wincoin_value, float wincoins){
        try {
            // Create a neat value object to hold the URL
            URL url = new URL("https://www.random.org/integers/?num=1&min=19000&max=20000&col=1&base=10&format=plain&rnd=new");

            // This line makes the request
            InputStream responseStream = null;

            // Open a connection(?) on the URL(?) and cast the response(??)
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Now it's "open", we can set the request method, headers etc.
            connection.setRequestProperty("accept", "application/json");

            responseStream = connection.getInputStream();

            // Finally we have the response
            byte[] result_bytes = new byte[5];
            responseStream.read(result_bytes);

            String res = new String(result_bytes, StandardCharsets.UTF_8);
            int bitcoin_value = Integer.parseInt(res.trim());
            return wincoins*(((float)wincoin_value) / bitcoin_value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
