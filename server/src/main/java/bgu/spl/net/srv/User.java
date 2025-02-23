package bgu.spl.net.srv;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class User {
        public final String host;
        public final String userName;
        public final String password;
        private boolean isActive;
        private int connectionId;
        private Map<String, String> ID_Channels; //<id , channelName>
        private Map<String, String> Channels_ID; //<channelName , id>

        public User(String host, String userName, String password, boolean isActive) {
                this.host = host;
                this.userName = userName;
                this.password = password;
                this.isActive = isActive;
                ID_Channels = new ConcurrentHashMap<>();
                Channels_ID = new ConcurrentHashMap<>();
        }

        public void connectUser() {
                this.isActive = true;
        }

        public void disconnectUser() {
                this.isActive = false;
                ID_Channels.clear();
                Channels_ID.clear();
                connectionId = -1;
        }

        public boolean checkPasscode(String password) {return this.password.equals(password);}

        public boolean subscribe(String destination, String subId) {
                if (ID_Channels.containsKey(subId) || ID_Channels.containsValue(destination)){
                        return false;
                }else{
                        ID_Channels.put(subId, destination);
                        Channels_ID.put(destination, subId);
                }
                return true;
        }

        public String getChannelNameFromSubID(String id) {return ID_Channels.get(id);}

        public void unsubscribe(String id) {Channels_ID.remove(ID_Channels.remove(id));}

        public Collection<String> getTopicNames() {return ID_Channels.values();}

        public String getChannelId(String destination) {
                if(destination==null){
                        return null;
                }
                return Channels_ID.get(destination);
        }

        public void setConnectionID(int connectionId) {this.connectionId = connectionId;}

        public int getConnectionId() {return connectionId;}

        public boolean isActive(){return this.isActive;};
}
