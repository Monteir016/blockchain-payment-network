package pt.tecnico.blockchainist.sequencer;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import pt.tecnico.blockchainist.sequencer.domain.SequencerState;
import pt.tecnico.blockchainist.sequencer.grpc.SequencerServiceImpl;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class SequencerMain {
    public static void main(String[] args) {

        System.out.println(SequencerMain.class.getSimpleName());

        // Validate arguments
        if (args.length < 1) {
            System.err.println("Argument(s) missing!");
            System.err.printf("Usage: java %s <port> [maxTransactionsPerBlock] [blockTimeoutSeconds]%n", SequencerMain.class.getName());
            return;
        }

        final int port;
        final int maxTransactionsPerBlock;
        final int blockTimeoutSeconds;

        try {
            port = Integer.parseInt(args[0]);
            maxTransactionsPerBlock = args.length > 1 ? Integer.parseInt(args[1]) : 4; // Default N = 4
            blockTimeoutSeconds = args.length > 2 ? Integer.parseInt(args[2]) : 5; // Default T = 5
        } catch (NumberFormatException e) {
            System.err.println("Invalid numeric argument: " + e.getMessage());
            System.err.printf("Usage: java %s <port> [maxTransactionsPerBlock] [blockTimeoutSeconds]%n", SequencerMain.class.getName());
            return;
        }

        if (port <= 0 || port > 65535) {
            System.err.println("Invalid port number: " + port);
            return;
        }
        if (maxTransactionsPerBlock <= 0) {
            System.err.println("Invalid max transactions per block: " + maxTransactionsPerBlock);
            return;
        }
        if (blockTimeoutSeconds <= 0) {
            System.err.println("Invalid block timeout seconds: " + blockTimeoutSeconds);
            return;
        }

        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        // Create domain state and gRPC service implementation
        final SequencerState state = new SequencerState(maxTransactionsPerBlock,
                                                        blockTimeoutSeconds, scheduler);
        final BindableService impl = new SequencerServiceImpl(state);


        // Start gRPC server
        try {
            final Server server = ServerBuilder.forPort(port).addService(impl).build();
            server.start();
            System.out.println("Sequencer started, listening on port " + port);
            System.out.println("Block policy: N=" + maxTransactionsPerBlock + ", T=" + blockTimeoutSeconds + "s");


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