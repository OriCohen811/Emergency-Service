package bgu.spl.net.impl.stomp;


import bgu.spl.net.srv.Server;

public class StompServer {

    public static void main(String[] args) {
        if (args.length==2){
            int port=0;
            try{
                port=Integer.parseInt(args[0]);}
            catch (NumberFormatException e){ 
                System.out.println("wront port input(number only)"); 
                return;
            };
            if ("tpc".equals(args[1])) {
                Server.threadPerClient(
                        port, //port
                        ()-> new StompProtocol(),
                        ()-> new StompMessageEncoderDecoder() 
                ).serve();
            }
            else if ("reactor".equals(args[1])) {
                System.out.println("-----------------------");
                Server.reactor(10, port, ()-> new StompProtocol(),()-> new StompMessageEncoderDecoder() ).serve();
            }
            else {
                System.out.println("wrong server type");
            }
        }
        else if (args.length == 1) {
            int space = args[0].indexOf(' ');
            int port = -1;
            String type = args[0].substring(space + 1);
            try {
                port = Integer.parseInt(args[0].substring(0, space));
            } catch (NumberFormatException e) {
                System.out.println("wront port input(number only)");
                return;
            };
            if ("tpc".equals(type)) {
                Server.threadPerClient(
                        port, // port
                        () -> new StompProtocol(),
                        () -> new StompMessageEncoderDecoder()).serve();
            } else if ("reactor".equals(type)) {
                System.out.println("-----------------------");
                Server.reactor(10, port, () -> new StompProtocol(), () -> new StompMessageEncoderDecoder()).serve();
            } else {
                System.out.println("wrong server type");
            }

        }
        else{
            System.out.println("args should be : <port> <servertype(tpc/reactor)> OR <port servertype(tpc/reactor)>");
        }
    }
}
