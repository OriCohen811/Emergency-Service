#include "StompProtocol.h"
#include <iostream>
#include <sstream>
#include "event.h"
#include <fstream>


StompProtocol::StompProtocol(bool& running)

  : connectionHandler(nullptr), receiptCounter(0), subscriptionCounter(0), subscribedChannels(), subscriptionIds(),receiptMap(), receivedEvents(), loggedIn(false), running(running){}

StompProtocol::~StompProtocol() {
    if (connectionHandler != nullptr) {
        delete connectionHandler;
    }
}

bool StompProtocol::connect(const std::string& username, const std::string& password) {

    if (!connectionHandler->connect()) {
        std::cout << "Could not connect to server" << std::endl;
        return false;
    }

    std::ostringstream oss;
    oss << "CONNECT\naccept-version:1.2\nhost:stomp.cs.bgu.ac.il\nlogin:" << username << "\npasscode:" << password << "\n\n\0";
    std::string frame = oss.str();
    std::cout << "Sending frame: " << frame << std::endl; // Debug information
    if (!connectionHandler->sendLine(frame)) {
        std::cerr << "Failed to send CONNECT frame" << std::endl;
        return false;
    }
    this->username = username;
    return true;
}

bool StompProtocol::disconnect() {
    std::string receiptId = generateReceiptId();
    std::ostringstream oss;
    oss << "DISCONNECT\nreceipt:" << receiptId << "\n\n\0";
    std::string frame = oss.str();
    std::cout << "Sending frame: " << frame << std::endl; // Debug information
    if (!connectionHandler->sendLine(frame)) {
        std::cerr << "Failed to send DISCONNECT frame" << std::endl;
        return false;
    }
    receiptLock.lock();
    receiptMap[receiptId] = "DISCONNECT";
    receiptLock.unlock();
    return true;
}

void StompProtocol::subscribe(const std::string& destination) {
    if (subscribedChannels.find(destination) != subscribedChannels.end()) {
        std::cout << "You are already subscribed to channel " << destination << std::endl;
        return;
    }
    std::string receiptId = generateReceiptId();
    std::string subscriptionId = generateSubscriptionId();
    std::ostringstream oss;
    oss << "SUBSCRIBE\ndestination:" << destination << "\nid:" << subscriptionId << "\nreceipt:" << receiptId << "\n\n\0";
    std::string frame = oss.str();
    std::cout << "Sending frame: " << frame << std::endl; // Debug information
    if (!connectionHandler->sendLine(frame)) {
        std::cerr << "Failed to send SUBSCRIBE frame" << std::endl;
        return;
    }
    receiptLock.lock();
    receiptMap[receiptId] = "SUBSCRIBE "+ destination  ;
    receiptLock.unlock();
    subscriptionIds[destination] = subscriptionId;
}

void StompProtocol::unsubscribe(const std::string& destination) {
    auto it = subscriptionIds.find(destination);
    if (it == subscriptionIds.end()) {
        std::cout << "You are not subscribed to channel " << destination << std::endl;
        return;
    }
    std::string receiptId = generateReceiptId();
    std::string subscriptionId = it->second;
    std::ostringstream oss;
    oss << "UNSUBSCRIBE\nid:" << subscriptionId << "\nreceipt:" << receiptId << "\n\n\0";
    std::string frame = oss.str();
    std::cout << "Sending frame: " << frame << std::endl; // Debug information
    if (!connectionHandler->sendLine(frame)) {
        std::cerr << "Failed to send UNSUBSCRIBE frame" << std::endl;
        return;
    }    
    receiptLock.lock();
    receiptMap[receiptId] = "UNSUBSCRIBE " + destination  ;
    receiptLock.unlock();
}

bool StompProtocol::receive(std::string& response) {
    if (!connectionHandler->isOpen() || !connectionHandler->getLine(response)) {
        std::cerr << "Failed to receive response" << std::endl;
        return false;
    }
    std::cout << "Received raw response: " << response << std::endl; // Debug information
    return true;
}

void StompProtocol::handleOutput(const std::string& response) {
    std::cout << "Handling output: " << response << std::endl;
    // Add logic to handle different types of responses
    if (response.find("CONNECTED") != std::string::npos) {
        std::cout << "Connection established successfully." << std::endl;
        loggedIn = true;
    } else if (response.find("RECEIPT") != std::string::npos) {
        std::cout << "Receipt received: " << response << std::endl;
        // Extract receipt-id from the response
        size_t pos = response.find("receipt-id:");
        if (pos != std::string::npos) {
            std::string receiptId = response.substr(pos + 11);
            receiptId = receiptId.substr(0, receiptId.find_first_of("\n\r"));
            std::cout << "Extracted Receipt ID: " << receiptId << std::endl;
            // Handle the receipt-id as needed
            receiptLock.lock();
            if (receiptMap.find(receiptId) != receiptMap.end()) {
                std::string action = receiptMap[receiptId];
                std::cout << "action is:" << action << std::endl;
                receiptMap.erase(receiptId);
                receiptLock.unlock();
                std::cout << "Receipt ID " << receiptId << " corresponds to " << action << std::endl;
                if (action.find("DISCONNECT") != std::string::npos) {
                    this->clearAll();  
                    std::cout << "Client logged out succesfully" << std::endl;
                } else if (9<=action.size() && action.substr(0, 9) == "SUBSCRIBE") {
                    std::string channelName = action.substr(10);
                    std::cout << "Joined channel " << channelName << std::endl;
                    subscribedChannels.insert(channelName);
                } else if (11<=action.size() && action.substr(0, 11) == "UNSUBSCRIBE") {
                    std::string channelName = action.substr(12);
                    std::cout << "Exited channel " << channelName << std::endl;
                    subscribedChannels.erase(channelName);
                    subscriptionIds.erase(channelName);
                }
                
            } else {
                receiptLock.unlock();
                std::cerr << "Unknown receipt ID: " << receiptId << std::endl;
            }
        }
    } else if (response.find("MESSAGE") != std::string::npos) {
        std::cout << "Message received: " << response << std::endl;
        // Process the message
    } else if (response.find("ERROR") != std::string::npos) {
        std::cerr << response << std::endl;
        clearAll();
        running = false;
    } else {
        std::cerr << "Unknown response: " << response << std::endl;
    }
}

std::string StompProtocol::generateReceiptId() {
    return std::to_string(receiptCounter++);
}

std::string StompProtocol::generateSubscriptionId() {
    return std::to_string(subscriptionCounter++);
}

void StompProtocol::sendEmergencyEvent(const Event& event) {
    std::ostringstream oss;
    oss << "SEND\ndestination:" << event.get_channel_name() << "\n\nuser:" << this->username << "\ncity:" << event.get_city()
        << "\nevent name:" << event.get_name() << "\ndate time:" << event.get_date_time()
        << "\ngeneral information:";
    for (const auto& info : event.get_general_information()) {
        oss << "\n" << info.first << ":" << info.second;
    }
    oss << "\nactive:" << (event.get_general_information().at("active") == "true" ? "true" : "false")
        << "\nforces arrival at scene:" << (event.get_general_information().at("forces_arrival_at_scene") == "true" ? "true" : "false")
        << "\ndescription:" << event.get_description() << "\n\n\0";
    std::string frame = oss.str();
    std::cout << "Sending emergency event frame: " << frame << std::endl; // Debug information
    connectionHandler->sendLine(frame);
    receivedEvents.push_back(event);
}

void StompProtocol::summarizeEvents(const std::string& channelName, const std::string& user, const std::string& filename) {
    std::vector<Event> userEvents;
    for (const auto& event : receivedEvents) {
        if (event.get_channel_name() == channelName && event.getEventOwnerUser() == user) {
            userEvents.push_back(event);
        }
    }

    std::sort(userEvents.begin(), userEvents.end(), [](const Event& a, const Event& b) {
        if (a.get_date_time() == b.get_date_time()) {
            return a.get_name() < b.get_name();
        }
        return a.get_date_time() < b.get_date_time();
    });

    int totalReports = userEvents.size();
    int activeCount = 0;
    int forcesArrivalCount = 0;
    for (const auto& event : userEvents) {
        if (event.get_general_information().at("active") == "true") {
            activeCount++;
        }
        if (event.get_general_information().at("forces_arrival_at_scene") == "true") {
            forcesArrivalCount++;
        }
    }

    std::ofstream file("bin/" + filename);
    if (!file.is_open()) {
        std::cerr << "Failed to open file: bin/" << filename << std::endl;
        return;
    }

    file << "Channel " << channelName << "\n";
    file << "Stats:\n";
    file << "Total: " << totalReports << "\n";
    file << "active: " << activeCount << "\n";
    file << "forces arrival at scene: " << forcesArrivalCount << "\n";
    file << "Event Reports:\n";

    for (size_t i = 0; i < userEvents.size(); ++i) {
        const auto& event = userEvents[i];
        file << "Report_" << (i + 1) << ":\n";
        file << "city: " << event.get_city() << "\n";
        file << "date time: " << Event::epochToDate(event.get_date_time()) << "\n";
        file << "event name: " << event.get_name() << "\n";
        std::string summary = event.get_description().substr(0, 27);
        if (event.get_description().size() > 27) {
            summary += "...";
        }
        file << "summary: " << summary << "\n";
    }

    file.close();
}

bool StompProtocol::LoggedIn()
{
    return loggedIn;
}

ConnectionHandler * StompProtocol::getConnectionHandler() const
{
    return connectionHandler;
}
void StompProtocol::clearAll()
{
    connectionHandler->close();
    delete connectionHandler;
    this->connectionHandler = nullptr;
    receiptCounter = 0;
    subscriptionCounter = 0;
    subscribedChannels.clear();
    subscriptionIds.clear();
    receiptLock.lock();
    receiptMap.clear();
    receiptLock.unlock();
    loggedIn = false;
}

void StompProtocol::setConnectionHandler(ConnectionHandler * connectionHandler)
{
    if(this->connectionHandler != nullptr)
    {
        delete this->connectionHandler;
    }
    this->connectionHandler = connectionHandler;
}

bool StompProtocol::isSocketOpen()
{

    return connectionHandler->isOpen();
}
