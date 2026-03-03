package pt.tecnico.blockchainist.node;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import pt.tecnico.blockchainist.node.domain.NodeState;
import pt.tecnico.blockchainist.node.grpc.NodeServiceImpl;

public class NodeMain {
    public static void main(String[] args) {

        // Receive arguments
        System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		// Check arguments
		if (args.length < 3) {
			System.err.println("Argument(s) missing!");
            System.err.printf("Usage: java %s <port> <nodeId> <sequencerId>%n", NodeMain.class.getName());
			return;
		}

        // Parse arguments
        final int port = Integer.parseInt(args[0]);
        final String nodeId = args[1];
        final String sequencerId = args[2];

        final BindableService impl = new NodeServiceImpl(new NodeState());
        
        // Start gRPC server
        final Server server = ServerBuilder.forPort(port).addService(impl).build();

        try {
            server.start();
            System.out.println("Server started, listening on " + port);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down server...");
                server.shutdown();
            }));
            server.awaitTermination();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
