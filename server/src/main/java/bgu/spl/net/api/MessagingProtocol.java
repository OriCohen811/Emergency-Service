package bgu.spl.net.api;

public interface MessagingProtocol<T> {
 

    T process(T msg);

    boolean shouldTerminate();
 
}