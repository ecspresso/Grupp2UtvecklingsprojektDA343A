package server.entity;

import entity.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.time.LocalDateTime;

public class ServerReceiver extends Thread {

    private ObjectInputStream ois;
    private Socket socket;
    private Message message;

    public ServerReceiver(Socket socket) {

        this.socket = socket;
        start();
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {

    }

    public synchronized void run() {
        try {
            ois = new ObjectInputStream(socket.getInputStream());
            while(true) {
                Message message = (Message) ois.readObject();
                setMessage(message);
                message.setReceived(LocalDateTime.now());

            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
}

}
