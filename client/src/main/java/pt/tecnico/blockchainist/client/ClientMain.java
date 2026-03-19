package pt.tecnico.blockchainist.client;

import java.util.ArrayList;
import java.util.UUID;

import pt.tecnico.blockchainist.client.grpc.ClientNodeService;

public class ClientMain {

    public static void main(String[] args) {
        boolean debug = Boolean.getBoolean("debug");
        if (debug) {
            System.err.println("[DEBUG] Debug mode enabled");
            System.err.println("[DEBUG] Args: " + java.util.Arrays.toString(args));
        }
        System.out.println(ClientMain.class.getSimpleName());

        // check arguments
        if (args.length < 1) {
            System.err.println("Argument(s) missing!");
            printUsage();
            return;
        }

        // parse arguments
        ArrayList<ClientNodeService> nodes = new ArrayList<>(args.length);
        for (String arg : args) {
            String[] split = arg.split(":");
            if (split.length != 3) {
                System.err.println("Invalid argument: " + arg);
                printUsage();
                return;
            }
            String host = split[0];
            int port = -1;
            try {
                port = Integer.parseInt(split[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port (" + split[1] + ") in argument: " + arg);
                printUsage();
                return;
            }
            if (port > 65535 || port < 0) {
                System.err.println("Port number out of range (0-65535): " + port);
                printUsage();
                return;
            }
            String organization = split[2];

            if (debug) System.err.printf("[DEBUG] Adding node: %s:%d:%s\n", host, port, organization);
            nodes.add(new ClientNodeService(host, port, organization, debug));
        }

        // Stable client identifier for Issue 9 (requestId = clientId + commandNumber).
        String clientId = UUID.randomUUID().toString();

        try {
            if (debug) System.err.println("[DEBUG] Creating CommandProcessor");
            CommandProcessor processor = new CommandProcessor(nodes, clientId);
            if (debug) System.err.println("[DEBUG] Entering user input loop");
            processor.userInputLoop();
        } catch (Exception e) {
            System.err.println("Erro fatal: " + e.getMessage());
            if (debug) {
                System.err.println("[DEBUG] Exception stack trace:");
                e.printStackTrace(System.err);
            }
        } finally {
            // Shutdown all gRPC channels before exiting
            for (ClientNodeService node : nodes) {
                node.shutdown();
            }
        }
    }

    private static void printUsage() {
        System.err.println("Usage: mvn exec:java -Dexec.args=\"<host>:<port>:<organization> [<host>:<port>:<organization> ...]\"");
    }
}
