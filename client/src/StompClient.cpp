#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"
#include "../include/keyboardInputHandler.h"
#include <thread>
#include <mutex>
#include <iostream>
#include <condition_variable>

void readFromKeyboard(StompProtocol*& protocol, std::mutex& mtx, bool& running, std::condition_variable& cv) {
    KeyboardInputHandler inputHandler(protocol, mtx, running);
    std::string input;
    while (running) {
        std::cout << "Enter command: ";
        std::getline(std::cin, input);
        {
            std::lock_guard<std::mutex> lock(mtx);
            inputHandler.handleInput(input);
        }
        cv.notify_one(); // Notify the read thread that a command has been sent
    }
}

void readFromSocket(StompProtocol*& protocol, std::mutex& mtx, bool& running, std::condition_variable& cv) {
    while (running) {
        std::unique_lock<std::mutex> lock(mtx);
        cv.wait(lock, [&protocol, &running] { return !running || (protocol->getConnectionHandler()!=nullptr && protocol->isSocketOpen()); }); // Wait until protocol is set or running is false
        
        if (!running){
            break; // Exit if running is false
        } 
        lock.unlock();// Release the lock before processing the response
        std::string response;
        if (protocol->receive(response))
        {
            protocol->handleOutput(response); // Handle the response
        }
        
    }
}

/**
 * @brief 
 * 
 * @param argc 
 * @param argv contains host and port (host = argv[1], port = argv[2])
 */
int main(int argc, char *argv[]) {
    std::mutex mtx;
    std::condition_variable cv;
    bool running = true;
    StompProtocol* protocol = new StompProtocol(running);

    // Run readFromSocket in a separate thread
    std::thread socketThread(readFromSocket, std::ref(protocol), std::ref(mtx), std::ref(running), std::ref(cv));

    // Run readFromKeyboard in the main thread
    readFromKeyboard(protocol, mtx, running, cv);

    socketThread.join();

    if (protocol != nullptr) {
        delete protocol;
    }

    return 0;
}
