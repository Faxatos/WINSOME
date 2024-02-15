package server.utils;

import server.utils.users.User;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class ConnectionAttachment {

    private ByteBuffer[] bfs;
    private User user;
    private String longResponse;
    private boolean first_writing;

    //variabili per infinite scrolling
    private int pointer;
    private boolean cap_reached = false;
    private String type_of_service;
    private ArrayList<String> scrolling;

    private int tmp_id_post;


    public ConnectionAttachment(int BUFFER_SIZE) {
        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
        ByteBuffer message = ByteBuffer.allocate(BUFFER_SIZE);
        this.bfs = new ByteBuffer[]{length, message};
        this.user = null;
        this.longResponse = null;
        this.first_writing = true;

        this.pointer = -1;
        this.type_of_service = null;
        this.cap_reached = false;
        this.scrolling = new ArrayList<>();
    }

    public boolean getFirstWriting() {
        return first_writing;
    }

    public void setFirstWriting(boolean first_writing) {
        this.first_writing = first_writing;
    }

    public ByteBuffer[] getBuffer() {
        return bfs;
    }

    public void setBufferData(ByteBuffer bb) {
        int len = bb.limit();
        System.arraycopy(bb.array(), 0, bfs[1].array(), 0, len);
        bfs[1].limit(len);
    }

    public ByteBuffer getBufferData() {
        return this.bfs[1];
    }

    public ByteBuffer getBufferLen() {
        return this.bfs[0];
    }

    public int getPointer() {
        return pointer;
    }

    public void setPointer(int pointer) {
        this.pointer = pointer;
    }

    public boolean isCap_reached() {
        return cap_reached;
    }

    public void setCap_reached(boolean cap_reached) {
        this.cap_reached = cap_reached;
    }

    public ArrayList<String> getScrolling() {
        return new ArrayList<>(scrolling);
    }

    public int getScrollingSize(){
        return scrolling.size();
    }

    public void addToScrolling(ArrayList<String> new_values) {
        scrolling.addAll(new_values);
    }

    public void clearScrolling() {
        scrolling.clear();
    }

    public void setScrolling(ArrayList<String> scrolling) {
        this.scrolling = scrolling;
    }

    public String getType_of_service() {
        return type_of_service;
    }

    public void setType_of_service(String type_of_service) {
        this.type_of_service = type_of_service;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getLongResponse(){
        return this.longResponse;
    }

    public void setLongResponse(String response){
        this.longResponse = response;
    }

    public int getTmp_id_post() {
        return tmp_id_post;
    }

    public void setTmp_id_post(int tmp_id_post) {
        this.tmp_id_post = tmp_id_post;
    }

    public String splitLongResponse(){ //metodo richiamato quando la risposta ottenuta dal server potrebbe essere > BUFF_LEN

        if(longResponse.length() > bfs[1].capacity()){ //se longResponse è più grande di buffer
            String ret = longResponse.substring(0, bfs[1].capacity());
            longResponse = longResponse.substring(bfs[1].capacity()); //allora vado a prendere quanto posso di longresponse
            return ret; //e lo passo al metodo di write del server
        }
        //se longResponse non è più grande di buffer
        String ret = longResponse;
        int ret_len = longResponse.length();
        longResponse = null;
        return ret; //restituisco ciò che devo inviare
    }


}
