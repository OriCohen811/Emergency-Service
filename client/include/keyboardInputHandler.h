#pragma once

#include "StompProtocol.h"
#include <string>
#include <mutex>

class KeyboardInputHandler {
private:
    StompProtocol*& protocol;
    std::mutex& mtx;
    bool& running;

public:
    KeyboardInputHandler(StompProtocol*& protocol, std::mutex& mtx, bool& running);
    void handleInput(const std::string& input);
};