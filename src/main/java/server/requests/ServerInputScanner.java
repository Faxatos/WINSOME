package server.requests;

import java.io.InputStreamReader;
import java.nio.channels.Selector;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerInputScanner implements Runnable{

    private final AtomicBoolean is_running;
    private final Selector selector;

    public ServerInputScanner(AtomicBoolean is_running, Selector selector) {
        this.is_running = is_running;
        this.selector = selector;
    }

    @Override
    public void run() {
        String input;
        Scanner in = new Scanner(new InputStreamReader(System.in));

        while (is_running.get()) {
            input = in.nextLine(); //legge di continuo
            if (input.equals("exit")) { //e se leggo exit fermo tutto
                System.out.println("Chiusura del server");
                is_running.set(false);
                selector.wakeup();
            }
        }
    }
}
