package bgu.spl.net.srv;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class DataBase {
    
    private static class SingletonHolder{
        private static final DataBase instance = new DataBase();
    }

    private final ConcurrentHashMap<String,User> users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer,User> conId_user = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Queue<User>> channels= new ConcurrentHashMap<>();

    public void joinUser_conId(int connectionId, User user) {
        user.setConnectionID(connectionId);
        conId_user.put(connectionId,user);
    }

    public User getUserFromConId(int connectionId) {
        return conId_user.get(connectionId);
    }

    public Queue<User> getUsersQueue(String channel){
        return channels.get(channel);
    }

    public User getUser(String username){
        return users.get(username);
    }

    public static DataBase getInstance(){
        return SingletonHolder.instance;
    }

    public void addUser(String username,User user, int connectionId){
        synchronized(users){
            users.put(username,user);
            joinUser_conId(connectionId,user);
        }
    }

    public boolean containsUsername(String userName){
        return users.containsKey(userName);
    }

    public boolean containsChannel(String destination){
        return channels.containsKey(destination);
    }

    public void addChannel(String destination){
        Object obj = channels.putIfAbsent(destination,new ConcurrentLinkedDeque<>());
        if(obj==null){
            System.out.println("Channel: " + destination + " , add successfully!");
        }
    }

    public void subscribeUserToChannel(User user,String destination){
        Queue<User> subscribes = channels.get(destination);
        synchronized(subscribes){
            subscribes.add(user);
            System.out.println("User: " + user.userName + " , subscribe to channel " + destination + " successfully!");
        }
    }

    public void unsubscribeUser(User user , String destination){
        Queue<User> subscribes = channels.get(destination);
        synchronized(subscribes){
            if(subscribes.remove(user)){
                System.out.println("User: " + user.userName + " , unsubscribe from channel " + destination + " successfully!");
            }
            else{
                System.out.println("User: " + user.userName + " , try to unsubscribe from channel " + destination + " but he hasn't subscribed!");
            }
        }
    }

    public void disconnect(int connectionId) {
        User user = getUserFromConId(connectionId);
        if(user==null){
            return;
        }
        for(String topic_name:user.getTopicNames()){
            unsubscribeUser(user, topic_name);
        }
        user.disconnectUser();
        conId_user.remove(connectionId);
        System.out.println("User: " + user.userName + " , disconnect!");
    }

}
