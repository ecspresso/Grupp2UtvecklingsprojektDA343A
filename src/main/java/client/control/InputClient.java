package client.control;

import globalEntity.Message;
import globalEntity.User;

import javax.swing.ImageIcon;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.SocketException;
import java.time.format.DateTimeFormatter;

public class InputClient extends Thread {
    private ObjectInputStream ois;
    private Message message;
    private Client client;
    public volatile boolean running = true;

    public InputClient(Client client,ObjectInputStream ois) {
        this.ois = ois;
        this.client = client;
    }

    @Override
    public void run() {
        while (running){
            try {
                message = (Message) ois.readObject();
                int type = message.getType();

                switch(type) {
                    case Message.CONTACTS -> {
                        this.client.updateListOfContacts(message.getContacts());
                    }

                    case Message.TEXT -> {
                        User sender = message.getSender();
                        String text = message.getMessage();
                        String time = message.getSent().format(DateTimeFormatter.ISO_LOCAL_TIME);
                        client.displayMessage(sender, text, time);
                        // mainWindow.newImageMessage(icon,sender);
                        // String sender = String.valueOf(message.getSender());
                        // String guiMessage = message.getMessage();
                        // ImageIcon icon = message.getImage();
                    }

                    case Message.IMAGE -> {
                        User sender = message.getSender();
                        ImageIcon image = message.getImage();
                        String time = message.getSent().format(DateTimeFormatter.ISO_LOCAL_TIME);
                        client.displayImage(sender, image, time);
                    }

                    case Message.TEXT_AND_IMAGE -> {
                        User sender = message.getSender();
                        String text = message.getMessage();
                        ImageIcon image = message.getImage();
                        String time = message.getSent().format(DateTimeFormatter.ISO_LOCAL_TIME);
                        client.displayImageAndText(sender, image,text, time);
                    }

                    default -> {
                        System.err.println("FEL?");
                    }
                }
            } catch (SocketException e) {
                client.logOut(null);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                System.exit(4);
            }
        }
    }

    public Message getMessage() {
        return message;
    }
}
