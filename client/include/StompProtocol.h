#pragma once

#include "ConnectionHandler.h"
#include "event.h"
#include <vector>
#include <string>
#include <unordered_set>
#include <unordered_map>

class StompProtocol
{
private:
    ConnectionHandler* connectionHandler;
    int receiptCounter;
    int subscriptionCounter;
    std::unordered_set<std::string> subscribedChannels;
    std::unordered_map<std::string, std::string> subscriptionIds;
    std::unordered_map<std::string, std::string> receiptMap;
    std::vector<Event> receivedEvents; 
    std::mutex receiptLock;
    std::string generateReceiptId();
    std::string generateSubscriptionId();
    std::atomic<bool> loggedIn;
    bool & running;
    std::string username;
    

public:
    StompProtocol(bool& running);
    ~StompProtocol();
    
    // Delete copy constructor and assignment operator
    StompProtocol(const StompProtocol&) = delete;
    StompProtocol& operator=(const StompProtocol&) = delete;
    
    bool connect(const std::string& username, const std::string& password);
    bool disconnect();
    // void send(const std::string& destination, const std::string& message);
    void subscribe(const std::string& destination);
    void unsubscribe(const std::string& destination);
    bool receive(std::string& response);
    void handleOutput(const std::string& response);
    void sendEmergencyEvent(const Event& event);
    void summarizeEvents(const std::string& channelName, const std::string& user, const std::string& filePath);
    bool LoggedIn();
    ConnectionHandler* getConnectionHandler() const;
    void clearAll();
    void setConnectionHandler(ConnectionHandler* connectionHandler);
    bool isSocketOpen();
    
};