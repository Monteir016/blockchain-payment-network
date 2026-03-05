package pt.tecnico.blockchainist.sequencer;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import pt.tecnico.blockchainist.sequencer.domain.SequencerState;
import pt.tecnico.blockchainist.sequencer.grpc.SequencerServiceImpl;

public class SequencerMain {
    public static void main(String[] args) {

        System.out.println(SequencerMain.class.getSimpleName());

        // Validate arguments
        if (args.length < 1) {
            System.err.println("Argument(s) missing!");
            System.err.printf("Usage: java %s <port>%n", SequencerMain.class.getName());
            return;
        }

        final int port = Integer.parseInt(args[0]);

        // Create domain state and gRPC service implementation
        final SequencerState state = new SequencerState();
        final BindableService impl = new SequencerServiceImpl(state);

        // Start gRPC server
        try {
            final Server server = ServerBuilder.forPort(port).addService(impl).build();
            server.start();
            System.out.println("Sequencer started, listening on port " + port);

            // Register shutdown hook for graceful termination
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down sequencer...");
                server.shutdown();
            }));

            server.awaitTermination();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}