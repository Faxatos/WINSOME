package client.notification;

import java.io.IOException;
import java.net.*;

public class ClientNotificationPrinter implements Runnable{
    MulticastSocket ms;
    private static int buff_len = 256; //default value

    public ClientNotificationPrinter(InetAddress multicastAddress, int multicastPort) throws IOException {
        ms = new MulticastSocket(multicastPort);
        InetSocketAddress group = new InetSocketAddress(multicastAddress, multicastPort);
        NetworkInterface netIf = NetworkInterface.getByName("wlan1");
        ms.joinGroup(group, netIf);
    }

    @Override
    public void run() {
        byte[] buff;
        while (!Thread.currentThread().isInterrupted()) {
            buff = new byte[buff_len];
            DatagramPacket packet = new DatagramPacket(buff, buff_len);
            try {
                ms.receive(packet); //aspetto prossimo pacchetto
                if(Thread.currentThread().isInterrupted())
                    break;
                String message = new String(packet.getData());
                System.out.println(message.trim());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        ms.close();
    }

    public static void setBufferSize(int size){
        buff_len = size;
    }
}