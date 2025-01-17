package server.entity;
import globalEntity.Message;
import globalEntity.User;
import server.control.Controller;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private final Controller controller;
    private ConcurrentHashMap<User, Buffer<Message>> messageOnHold = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<User, ClientHandler> loggedInUsers = new ConcurrentHashMap<>();
    private PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private ServerSocket serverSocket;

    public Server(Controller controller, int port) {
        this.controller = controller;
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
        new Connection().start();
    }

    public void addListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public synchronized void addLoggedInUser(User user, ClientHandler clientHandler) {
        new Thread(() -> {
            Message message = new Message.Builder()
                .type(Message.USER_LOGGED_IN)
                .sender(user) // <--- har loggat in
                .build();

            for(ClientHandler ch : loggedInUsers.values()) {
                ch.send(message);
            }

            loggedInUsers.put(user, clientHandler);
        }).start();
    }

    public void createFriendList(User user, ArrayList<User> users) {
        ArrayList<User> friends = new ArrayList<>(users);
        String filename = String.format("files/%s_friends.dat", user.getUsername());

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename))) {
            oos.writeInt(friends.size());
            for (User friend : friends) {
                oos.writeObject(friend);
            }
            oos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void messagesToSend(Message message) {
        User receiver = message.getReceiver();
        Buffer<Message> buffer;
        if (messageOnHold.containsKey(receiver)) {
            buffer = messageOnHold.get(receiver);
            buffer.put(message);
        } else {
            buffer = new Buffer<>();
            buffer.put(message);
        }
        messageOnHold.put(receiver, buffer);
    }

    public Message readFriendList(User user) {
        String filename = String.format("files/%s_friends.dat",  user.getUsername());
        ArrayList<User> friends = new ArrayList<>();
        Message message = null;

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename))) {
            int size = ois.readInt();
            for(int i = 0; i < size; i++) {
                friends.add((User) ois.readObject());
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        message = new Message.Builder()
            .type(Message.CONTACTS)
            .contacts(friends)
            .receiver(user)
            .build();

        return message;
    }

    public void sendMessage(Message reply, Traffic traffic) {
        User receiver = reply.getReceiver();
        if(loggedInUsers.containsKey(receiver)) {
            ClientHandler clientHandler = loggedInUsers.get(receiver);
            clientHandler.send(reply);
            pcs.firePropertyChange("sent", null, traffic);
        } else {
            pcs.firePropertyChange("queued", null, traffic);
            messagesToSend(reply);
        }
    }

    private void sendUserLoggedOut(User loggedOutUser) {
        for(ClientHandler ch : loggedInUsers.values()) {
            Message message = new Message.Builder()
                .type(Message.USER_LOGGED_OUT)
                .sender(loggedOutUser)
                .build();
            ch.send(message);
        }
    }

    public boolean userExists(User user) {
            return loggedInUsers.containsKey(user);
        }

    public void notifyReceived(Traffic traffic) {
        pcs.firePropertyChange("receivedByUser", null, traffic);
    }

    public void disconnect(Message message){
        User user = message.getSender();
        if(loggedInUsers.containsKey(user)){
            Traffic traffic = new Traffic.Builder()
                .text(message.getSender().getUsername() + " just logged out.")
                .eventTime(LocalDateTime.now())
                .build();
            pcs.firePropertyChange("logout", null, traffic);
            loggedInUsers.get(user).closeThread();
            loggedInUsers.remove(user);
            sendUserLoggedOut(user);
        }
        else {
            System.out.println("User not found");
        }
    }

    private class Connection extends Thread {

        public void run() {
            Socket socket;
            System.out.println("Loading...");
            System.out.println("Server operational.");
            while (true) {
                try {
                    socket = serverSocket.accept();
                    ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());

                    Message msg = (Message) ois.readObject();

                    if (msg.getType() == Message.LOGIN) {
                        Traffic traffic = new Traffic.Builder()
                            .text("Login request from: " + msg.getSender().getUsername())
                            .eventTime(LocalDateTime.now())
                            .build();

                        pcs.firePropertyChange("login", null, traffic);//nytt

                        new LoginHandler(oos, ois, msg.getSender()).start();
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class LoginHandler extends Thread {
        private final ObjectOutputStream oos;
        private final ObjectInputStream ois;
        private User user;

        public LoginHandler(ObjectOutputStream oos, ObjectInputStream ois, User user) {
            this.oos = oos;
            this.ois = ois;
            this.user = user;
        }

        @Override
        public void run() {
            ClientHandler clientHandler = null;
            try {
                while (clientHandler == null) {
                    if (user == null) {
                        Message message = (Message) ois.readObject();
                        user = message.getSender();
                    }
                    Message reply;

                    if(!controller.userExists(user)) { // kan logga in
                        reply =  new Message.Builder()
                            .type(Message.LOGIN_SUCCESS)
                            .contacts(new ArrayList<>(loggedInUsers.keySet()))
                            .build();
                        clientHandler = new ClientHandler(controller, oos, ois);

                        Traffic traffic = new Traffic.Builder()
                            .text(user.getUsername() + " just logged in.")
                            .eventTime(LocalDateTime.now())
                            .build();
                        pcs.firePropertyChange("loginOK", null, traffic);//nytt
                        addLoggedInUser(user, clientHandler);
                        clientHandler.start();
                        clientHandler.getServerSender().send(reply);
                        File file = new File("files/" + user.getUsername() + "_friends.dat");
                        if (file.exists()) {
                            clientHandler.getServerSender().send(readFriendList(user));
                        } else {
                            System.out.println("No friend list in directory.");
                        }
                        if (messageOnHold.containsKey(user)) {
                            Buffer buffer = messageOnHold.get(user);
                            while(!buffer.isEmpty()) {
                                try {
                                    clientHandler.getServerSender().send((Message) buffer.get());
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    } else { // kan inte logga in
                        reply =  new Message.Builder().type(Message.LOGIN_FAILED).build();
                        Traffic traffic = new Traffic.Builder()
                            .text(user.getUsername() + " failed to login.")
                            .eventTime(LocalDateTime.now())
                            .build();
                        pcs.firePropertyChange("loginFail", null, traffic);//nytt
                        oos.writeObject(reply);
                        oos.flush();
                        user = null;
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
            interrupt(); // Stoppa tråden
        }
    }
}




