CFLAGS = -std=c++11 -Wall -Wextra -Iinclude
LDFLAGS = -lpthread

all: bin/StompClient

bin/StompClient: bin/StompClient.o bin/KeyboardInputHandler.o bin/StompProtocol.o bin/Event.o bin/ConnectionHandler.o
	@g++ $(CFLAGS) -o bin/StompClient bin/StompClient.o bin/KeyboardInputHandler.o bin/StompProtocol.o bin/Event.o bin/ConnectionHandler.o $(LDFLAGS)

bin/StompClient.o: src/StompClient.cpp | bin
	@g++ $(CFLAGS) -c -o bin/StompClient.o src/StompClient.cpp

bin/KeyboardInputHandler.o: src/KeyboardInputHandler.cpp | bin
	@g++ $(CFLAGS) -c -o bin/KeyboardInputHandler.o src/KeyboardInputHandler.cpp

bin/StompProtocol.o: src/StompProtocol.cpp | bin
	@g++ $(CFLAGS) -c -o bin/StompProtocol.o src/StompProtocol.cpp

bin/Event.o: src/Event.cpp | bin
	@g++ $(CFLAGS) -c -o bin/Event.o src/Event.cpp

bin/ConnectionHandler.o: src/ConnectionHandler.cpp | bin
	@g++ $(CFLAGS) -c -o bin/ConnectionHandler.o src/ConnectionHandler.cpp

bin:
	@mkdir -p bin

run: all
	@./bin/StompClient

.PHONY: clean
clean:
	@rm -f bin/*.o bin/StompClient