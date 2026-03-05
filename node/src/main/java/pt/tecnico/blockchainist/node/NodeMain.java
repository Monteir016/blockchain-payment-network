package pt.tecnico.blockchainist.node;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import pt.tecnico.blockchainist.node.domain.NodeState;
import pt.tecnico.blockchainist.node.grpc.NodeServiceImpl;
import pt.tecnico.blockchainist.node.grpc.NodeSequencerService;

public class NodeMain {
    public static void main(String[] args) {

        // Log received arguments
        System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		// Validate argument count
		if (args.length < 3) {
			System.err.println("Argument(s) missing!");
            System.err.printf("Usage: java %s <port> <organization> <sequencerHost:sequencerPort>%n", NodeMain.class.getName());
			return;
		}

        // Parse arguments: port, organization name, sequencer address
        final int port = Integer.parseInt(args[0]);
        final String organization = args[1];
        final String sequencerAddress = args[2];

        // Parse sequencer host and port from address string
        String[] sequencerParts = sequencerAddress.split(":");  
        if (sequencerParts.length != 2) {
            System.err.println("Invalid sequencerId format. Expected host:port");
            return;
        }
        String sequencerHost = sequencerParts[0];
        int sequencerPort = Integer.parseInt(sequencerParts[1]);

        // Create gRPC client for the sequencer
        NodeSequencerService sequencerService = new NodeSequencerService(sequencerHost, sequencerPort);

        // Create domain state and gRPC service implementation
        NodeState nodeState = new NodeState();
        final BindableService impl = new NodeServiceImpl(nodeState, sequencerService);
        
        try {
            final Server server = ServerBuilder.forPort(port)
                    .addService(impl)
                    .build();
            server.start();
            System.out.println("Server started, listening on " + port);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down server...");
                server.shutdown();
                sequencerService.shutdown();
            }));
            server.awaitTermination();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
