package bgu.spl.net.impl.stomp;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.DataBase;
import bgu.spl.net.srv.User;
import java.util.*;


public class StompProtocol implements StompMessagingProtocol<String> {
    private final Map<String,String> frameHeaders = new Hashtable<>();
    private int connectionId;
    private String currentMessage;
    private Connections<String> connections;

    private final DataBase dataBase = DataBase.getInstance();

    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId=connectionId;
        this.connections=connections;
    }

    @Override
    public void process(String message) {
        currentMessage = message;
        int endLine1 = message.indexOf("\n");
        String frameType = message.substring(0,endLine1);
        frameHeaders.put("frameType",frameType);
        putHeaders(message.substring(endLine1+1));

        boolean hasReceipt=false;
        if (frameHeaders.containsKey("receipt")){
            hasReceipt=true;
        }

        if(dataBase.getUserFromConId(connectionId)==null && !frameType.equals("CONNECT")){
            error("Login error" , "-MUST- to login first.",hasReceipt);
        }
        else {
            switch (frameType) {
                case "CONNECT": {
                    validConnect(hasReceipt);
                    if (!"ERROR".equals(frameHeaders.get("frameType")))
                        tryToLogin(hasReceipt);
                    break;
                }
                case "SEND": {
                    validSend(hasReceipt);
                    if (!"ERROR".equals(frameHeaders.get("frameType")))
                        System.out.println(connectionId + " send a message");
                        buildAndSendMessage(hasReceipt);
                    break;
                }
                case "SUBSCRIBE": {
                    validSubscribe(hasReceipt);
                    if (!"ERROR".equals(frameHeaders.get("frameType")))
                        subscribeUser(hasReceipt);
                    break;
                }
                case "UNSUBSCRIBE": {
                    validUnsubscribe(hasReceipt);
                    if (!"ERROR".equals(frameHeaders.get("frameType")))
                        unsubscribeUser(hasReceipt);
                    break;
                }
                case "DISCONNECT": {
                    validDisconnect(hasReceipt);
                    if (!"ERROR".equals(frameHeaders.get("frameType")))
                        disconnectUser();
                    break;
                }
                default:
                    error("Stomp Syntax Error", "frame type doesn't exist.", hasReceipt);
            }
        }
        if(!this.frameHeaders.isEmpty()){
            String response = headersToString(this.frameHeaders);
            connections.send(connectionId, response);
            if (frameType.equals("DISCONNECT")) {
                connections.disconnect(connectionId);
            }
            else if(frameType.equals("ERROR")){
                dataBase.disconnect(connectionId);
                connections.disconnect(connectionId);
            }
            this.frameHeaders.clear();
        }
    }

    private static String headersToString(Map<String, String> headers) {
        StringBuilder stomp = new StringBuilder();
        String frameType = headers.get("frameType");
        switch (frameType) {
            case "ERROR": {
                stomp.append("ERROR\n");
                if (headers.containsKey("receipt-id")) {
                    stomp.append("receipt-id:" + headers.get("receipt-id") + '\n');
                }
                stomp.append("message:" + headers.get("errorType")+ "\n\n" + 
                            "The message:\n-----------------\n" + headers.get("originalMessage") + "\n-----------------\n" +
                            headers.get("errorDescription"));
                break;
            }
            case "RECEIPT": {
                stomp.append("RECEIPT\nreceipt-id:" + headers.get("receipt-id") + "\n\n");
                break;
            }
            case "MESSAGE": {
                stomp.append("MESSAGE\nsubscription:" + headers.get("subscription") + 
                            "\nmessage-id:" + headers.get("message-id") + 
                            "\ndestination:" + headers.get("destination") + "\n\n" + 
                            headers.get("message"));
                break;
            }
            case "CONNECTED": {
                stomp.append("CONNECTED\nversion:" + headers.get("accept-version") + "\n\n");
                break;
            }
        }
        return stomp.toString();
    }

    private void buildAndSendMessage(boolean hasReceipt) {
        User senderUser = dataBase.getUserFromConId(connectionId);
        String dest_id = senderUser.getChannelId(frameHeaders.get("destination"));
        if (dest_id==null){
            error("SEND command error","destination not found.",hasReceipt);
        } 
        else{
            Map<String,String> tempMap=new Hashtable<>();
            tempMap.put("frameType","MESSAGE");
            tempMap.put("subscription",dest_id);
            tempMap.put("message-id",connections.getMsgID());
            tempMap.put("message",frameHeaders.get("frameBody"));
            tempMap.put("destination",frameHeaders.get("destination"));

            Queue<User> subscribes = dataBase.getUsersQueue(frameHeaders.get("destination"));
            synchronized (subscribes) {
                for (User user : subscribes) {
                    dest_id = user.getChannelId(frameHeaders.get("destination"));
                    tempMap.put("subscription", dest_id);
                    String msg = headersToString(tempMap);
                    connections.send(user.getConnectionId(), msg);
                }
            }

            if (hasReceipt){
                generateReceipt();
            }
            else {
                frameHeaders.clear();
            }
        }
    }

    private void disconnectUser() {
        dataBase.disconnect(connectionId);
        generateReceipt();
    }

    private void unsubscribeUser(boolean hasReceipt) {
        User user=dataBase.getUserFromConId(connectionId);
        String topic_name=user.getChannelNameFromSubID(frameHeaders.get("id"));
        if(topic_name!=null){
            user.unsubscribe(frameHeaders.get("id"));
            dataBase.unsubscribeUser(user, topic_name);
            if (hasReceipt){
                generateReceipt();
            }
            else {
                frameHeaders.clear();;
            }
        }
        else{
            error("Unsubscribe Error","Channel id doesn't exist. ",hasReceipt);
        } 
    }

    private void subscribeUser(boolean hasReceipt) {
        User user = dataBase.getUserFromConId(connectionId);
        if (user.subscribe(frameHeaders.get("destination"),frameHeaders.get("id"))){
            if (!dataBase.containsChannel(frameHeaders.get("destination"))){
                dataBase.addChannel(frameHeaders.get("destination"));
            }
            dataBase.subscribeUserToChannel(user,frameHeaders.get("destination"));
            if (hasReceipt){
                generateReceipt();
            }
            else{
                frameHeaders.clear();;
            }
                
        }
        else{
            System.out.println("user " + user.userName + " is already subscribe to " + frameHeaders.get("destination") + ", OR id isn't unique.");
        }
        // else {
        //     error("Subscription Error","User is already subscribed to this channel. ",hasReceipt);
        // }
    }
    private void generateReceipt() {
        String receipt = frameHeaders.get("receipt");
        frameHeaders.clear();
        frameHeaders.put("frameType","RECEIPT");
        frameHeaders.put("receipt-id",receipt);
    }


    private void tryToLogin(boolean receipt) {
        String username = frameHeaders.get("login");
        if(!dataBase.containsUsername(username)){
            if ("stomp.cs.bgu.ac.il".equals(frameHeaders.get("host"))){
                if ("1.2".equals(frameHeaders.get("accept-version"))){
                    User user = new User(frameHeaders.get("host"),username,frameHeaders.get("passcode"),true);
                    dataBase.addUser(username, user, connectionId);

                    frameHeaders.clear();
                    frameHeaders.put("frameType","CONNECTED");
                    frameHeaders.put("accept-version","1.2");
                    System.out.println("User: " + username + ", login successfully!");
                }
                else{ error("Login Error","accept-version is wrong! ",receipt);}
            }
            else{ error("Login Error","host is Illegal.",receipt);}
        }
        else if(dataBase.getUser(username).isActive()) {
            error("Login Error", "User is already Logged in ", receipt);
        }
        else if(!dataBase.getUser(username).checkPasscode(frameHeaders.get("passcode"))){
            error("Login Error","User password is wrong ",receipt);
        }
        else {
            frameHeaders.clear();
            frameHeaders.put("frameType","CONNECTED");
            frameHeaders.put("accept-version","1.2");

            dataBase.joinUser_conId(connectionId,dataBase.getUser(username));
            dataBase.getUser(username).connectUser();
        }
    }

    @Override
    public boolean shouldTerminate() {
        return false;
    }

    private void error(String errorType,String errorDescription,boolean hasReceipt){
        String receipt = frameHeaders.get("receipt");
        frameHeaders.clear();
        frameHeaders.put("frameType","ERROR");
        frameHeaders.put("errorType",errorType);
        frameHeaders.put("errorDescription",errorDescription);
        frameHeaders.put("originalMessage",currentMessage);
        if (hasReceipt) {
            frameHeaders.put("receipt-id",receipt);
        }
    }

    private boolean containsAllHeader(String frameType,boolean hasReceipt){
        switch (frameType){
            case "CONNECT":{
                if(!frameHeaders.containsKey("accept-version") || !frameHeaders.containsKey("host") || !frameHeaders.containsKey("login") || !frameHeaders.containsKey("passcode")){
                    return false;
                }
                break;
                }
            case "SEND":{
                if(!frameHeaders.containsKey("frameBody")){
                    return false;
                }
                break;
            }
            case "SUBSCRIBE": {
                if(!frameHeaders.containsKey("id") || !frameHeaders.containsKey("destination")){
                    return false;
                }
                break;
            }
            case "UNSUBSCRIBE": {
                if(!frameHeaders.containsKey("id")){
                    return false;
                }
                break;
            }
        }
        return true;
    }

    private void validDisconnect(boolean receipt) {
        if(frameHeaders.containsKey("frameBody"))
            error("Stomp Syntax Error","DISCONNECT frame shouldn't have a frameBody.",receipt);
        else if(frameHeaders.size()==2){
            if (!receipt){
                error("Stomp Syntax Error","DISCONNECT frame -MUST- includes receipt header",receipt);
            }
        }
        else error("Stomp Syntax Error","DISCONNECT frame -MUST- have 1 header: receipt --- instead got " + (frameHeaders.size()-1)+".",receipt);
    }

    private void validUnsubscribe(boolean receipt) {
        int increase = 0;
        if(receipt){
            increase++;
        }
        if(frameHeaders.containsKey("frameBody")){
            error("Stomp Syntax Error","UNSUBSCRIBE frame shouldn't have a frameBody.",receipt);
        }
        else if(frameHeaders.size()==2+increase){
            if(!containsAllHeader("UNSUBSCRIBE", receipt)){
                error("Stomp Syntax Error","UNSUBSCRIBE frame -MUST- have id header",receipt);
            }
        }
        else {
            error("Stomp Syntax Error","UNSUBSCRIBE frame -MUST- have 1 header: id and optionally receipt --- instead got "+(frameHeaders.size()-1)+".",receipt);
        }
    }

    private void validSubscribe(boolean receipt) {
        int increase = 0;
        if(receipt){
            increase++;
        }
        if(frameHeaders.containsKey("frameBody"))
            error("Stomp Syntax Error","SUBSCRIBE frame shouldn't have a frameBody.",receipt);
        else if(frameHeaders.size()==3+increase){
            if(!containsAllHeader("SUBSCRIBE", receipt)){
                error("Stomp Syntax Error","SUBSCRIBE frame -MUST- have the next headers: destination, id.",receipt);
            }
        }
        else error("Stomp Syntax Error","SUBSCRIBE frame -MUST- have 2 headers: destination, id and optionally receipt --- instead got " + (frameHeaders.size()-1)+".",receipt);
    }

    private void validSend(boolean receipt) {
        int increase = 0;
        if(receipt){
            increase++;
        }
        if(!frameHeaders.containsKey("frameBody")){
            error("Stomp Syntax Error","SEND frame -MUST- have a frameBody.",receipt);
        }
        else if(frameHeaders.size()==3+increase){
            if(!containsAllHeader("SEND", receipt)){
                error("Stomp Syntax Error","SEND frame -MUST- have a destination header.",receipt);
            }
        }
        else error("Stomp Syntax Error","SEND frame -MUST- have 1 header: destination and optionally receipt --- instead got " + (frameHeaders.size()-2) + ".",receipt);
    }

    private void validConnect(boolean receipt) {
        int increase = 0;
        if(receipt){
            increase++;
        }
        if(frameHeaders.containsKey("frameBody"))
            error("Stomp Syntax Error","CONNECT frame shouldn't have a frameBody.",receipt);
        else if(frameHeaders.size()==5+increase){
            if(!containsAllHeader("CONNECT", receipt)){
                error("Stomp Syntax Error","CONNECT frame -MUST- have the headers: accept-version, host, login, passcode",receipt);
            }
        }
        else error("Stomp Syntax Error","CONNECT frame -MUST- have 4 headers: accept-version, host, login, passcode and optionally receipt --- instead got " + (frameHeaders.size()-1) + " headers.",receipt);
    }

    private void putHeaders(String s){
        int body = s.indexOf("\n\n");
        if(body == -1){
            error("Stomp Syntax Error",frameHeaders.get("frameType") + " frame -MUST- contains an empty line in the end of before frame body",false);
        }
        else if(body+2<s.length()){
            frameHeaders.put("frameBody", s.substring(body + 2));
        }

        int i = 0;
        while(i< body){
            int j = s.indexOf('\n', i); 
            String line = s.substring(i, j);
            int colon = line.indexOf(':');
            if(colon==-1 || colon==j-1){
                break;
            }
            String header = line.substring(0, colon);
            String value = line.substring(colon+1);
            if(header.equals("destination") && !value.isEmpty() && value.charAt(0)=='/'){
                value = value.substring(1);
            }
            frameHeaders.put(header,value);
            i=j+1;
        }

    }
}
