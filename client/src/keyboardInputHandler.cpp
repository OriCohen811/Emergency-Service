#include "../include/keyboardInputHandler.h"
#include "../include/ConnectionHandler.h"
#include "event.h"
#include <iostream>
#include <sstream>
#include <fstream>
#include <algorithm>

KeyboardInputHandler::KeyboardInputHandler(StompProtocol*& protocol, std::mutex& mtx, bool& running)
    : protocol(protocol), mtx(mtx), running(running) {}

void KeyboardInputHandler::handleInput(const std::string& input) {
    std::istringstream iss(input);
    std::string command;
    iss >> command;

    if (command == "login") {
        if (protocol->LoggedIn()) {
            std::cerr << "A user is already logged in. Please log out first." << std::endl;
            return;
        }
        std::string hostPort, username, password;
        iss >> hostPort >> username >> password;
        size_t colonPos = hostPort.find(':');
        std::string host = hostPort.substr(0, colonPos);
        int port = std::stoi(hostPort.substr(colonPos + 1));
        ConnectionHandler* connectionHandler = new ConnectionHandler(host, port);
        protocol->setConnectionHandler(connectionHandler);
        if (!protocol->connect(username, password)) {
            std::cerr << "Failed to connect to server" << std::endl;
        } 
    } else if (protocol->getConnectionHandler() != nullptr) {
        if (command == "join") {
            std::string channelName;
            iss >> channelName;
            protocol->subscribe(channelName);
        } else if (command == "exit") {
            std::string channelName;
            iss >> channelName;
            protocol->unsubscribe(channelName);
        } else if (command == "report") {
            std::string filePath;
            iss >> filePath;
            names_and_events events_and_names = parseEventsFile(filePath);
            for (const auto& event : events_and_names.events) {
                protocol->sendEmergencyEvent(event);
            }
            // Implement the logic to read the file and send the report
        } else if (command == "summary") {
            std::string channelName, userName, filePath;
            iss >> channelName >> userName >> filePath;
            protocol->summarizeEvents(channelName, userName, filePath);
            // Implement the logic to summarize the reports
        } else if (command == "logout") {
            if(!protocol->LoggedIn()){
                std::cout << "Client is not logged in" << std::endl;
                return;
            }
            else{
                protocol->disconnect();
            }
        } else {
            std::cout << "Unknown command" << std::endl;
        }
    } else {
        std::cerr << "Please login first" << std::endl;
    }
}