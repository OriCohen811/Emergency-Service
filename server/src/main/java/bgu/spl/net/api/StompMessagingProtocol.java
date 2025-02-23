package bgu.spl.net.api;

import bgu.spl.net.srv.Connections;

public interface StompMessagingProtocol<T>  {

    void start(int connectionId, Connections<T> connections);
    
    void process(T message);

    boolean shouldTerminate();
}
