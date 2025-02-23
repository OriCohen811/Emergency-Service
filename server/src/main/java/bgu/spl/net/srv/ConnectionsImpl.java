package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionsImpl<T> implements Connections<T>{
    private AtomicInteger message_id;
    private ConcurrentHashMap<Integer,ConnectionHandler<T>> ConnectionHandlerList;
    
    private final DataBase dataBase = DataBase.getInstance();

    ConnectionsImpl(ConcurrentHashMap<Integer,ConnectionHandler<T>> ConnectionHandlerList){
        message_id = new AtomicInteger();
        this.ConnectionHandlerList=ConnectionHandlerList;
    }
    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = ConnectionHandlerList.get(connectionId);
        if(handler!=null && !handler.isClosed()){
            handler.send(msg);
        }
        else{
            dataBase.disconnect(connectionId);
        }
        return false;
    }


    @Override
    public void send(String channel, T msg) {}

    @Override
    public void disconnect(int connectionId) {
        ConnectionHandlerList.remove(connectionId);
    }

    @Override
    public synchronized String getMsgID() {
        String ret= String.valueOf(message_id.incrementAndGet());
        return ret;
    }

}
