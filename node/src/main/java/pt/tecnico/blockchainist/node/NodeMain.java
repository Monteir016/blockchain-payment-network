package pt.tecnico.blockchainist.node;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;

import pt.tecnico.blockchainist.contract.Block;
import pt.tecnico.blockchainist.contract.DeliverBlockResponse;
import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.node.domain.ApplicationPipeline;
import pt.tecnico.blockchainist.node.domain.NodeState;
import pt.tecnico.blockchainist.node.grpc.DelayMetadataServerInterceptor;
import pt.tecnico.blockchainist.node.grpc.NodeServiceImpl;
import pt.tecnico.blockchainist.node.grpc.NodeSequencerService;

import java.util.Optional;

public class NodeMain {
    public static void main(String[] args) {
        boolean debug = Boolean.getBoolean("debug");
        if (debug) {
            System.err.println("[DEBUG] Debug mode enabled");
            System.err.println("[DEBUG] Args: " + java.util.Arrays.toString(args));
        }

        // Log received arguments
        if (debug) {
            System.err.printf("[DEBUG] Received %d arguments\n", args.length);
            for (int i = 0; i < args.length; i++) {
                System.err.printf("[DEBUG] arg[%d] = %s\n", i, args[i]);
            }
        }

        // Validate argument count
        if (args.length < 3) {
            System.err.println("Argument(s) missing!");
            System.err.printf("Usage: java %s <port> <organization> <sequencerHost:sequencerPort>%n", NodeMain.class.getName());
            return;
        }

        // Parse arguments: port, organization name, sequencer address
        final int port = Integer.parseInt(args[0]);
                if (port < 0 || port > 65535) {
                    System.err.println("Error: invalid port number. Must be between 0 and 65535.");
                    return;
                }
        final String organization = args[1];
                if (organization == null || organization.trim().isEmpty()) {
                    System.err.println("Error: organization name cannot be empty.");
                    return;
                }
        final String sequencerAddress = args[2];
        if (sequencerAddress == null || sequencerAddress.trim().isEmpty()) {
            System.err.println("Error: sequencerAddress cannot be empty.");
            return;
        }

        // Parse sequencer host and port from address string
        String[] sequencerParts = sequencerAddress.split(":");  
        if (sequencerParts.length != 2) {
            System.err.println("Error: invalid sequencerAddress format. Expected host:port.");
            return;
        }
        String sequencerHost = sequencerParts[0];
        int sequencerPort;
        try {
            sequencerPort = Integer.parseInt(sequencerParts[1]);
        } catch (NumberFormatException e) {
            System.err.println("Error: invalid sequencer port.");
            return;
        }
        if (sequencerPort < 0 || sequencerPort > 65535) {
            System.err.println("Error: sequencer port out of valid range (0-65535).");
            return;
        }
        // Prevent node from using same address as sequencer
        if (port == sequencerPort && (sequencerHost.equals("localhost") || sequencerHost.equals("127.0.0.1"))) {
            System.err.println("Error: node address (host:port) cannot be the same as sequencer address.");
            return;
        }

        if (debug) System.err.printf("[DEBUG] Connecting to sequencer at %s:%d\n", sequencerHost, sequencerPort);

        // Create gRPC client for the sequencer
        NodeSequencerService sequencerService = new NodeSequencerService(sequencerHost, sequencerPort);

        // Create domain state
        NodeState nodeState = new NodeState();

        int syncedBlocks = bootstrapSync(nodeState, sequencerService);

        // Start the application pipeline from the first block not yet applied.
        ApplicationPipeline applicationPipeline = new ApplicationPipeline(nodeState, sequencerService);
        applicationPipeline.setNextBlockIndex(syncedBlocks);
        applicationPipeline.start();

        final BindableService impl = new NodeServiceImpl(nodeState, sequencerService, applicationPipeline);

        try {
            if (debug) System.err.println("[DEBUG] Starting gRPC server");
            final Server server = ServerBuilder.forPort(port)
                    .addService(ServerInterceptors.intercept(impl, new DelayMetadataServerInterceptor()))
                    .build();
            server.start();
            if (debug) System.err.println("[DEBUG] Server started, listening on " + port);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (debug) System.err.println("[DEBUG] Shutting down server...");
                applicationPipeline.stop();
                server.shutdown();
                sequencerService.shutdown();
            }));
            server.awaitTermination();
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            if (debug) {
                System.err.println("[DEBUG] Exception stack trace:");
                e.printStackTrace(System.err);
            }
        }
    }

    // Maximum time to wait for the sequencer to become reachable at startup
    private static final long BOOTSTRAP_TIMEOUT_MS = 30_000;
    private static final long BOOTSTRAP_RETRY_MS   = 1_000;

    private static int bootstrapSync(NodeState nodeState, NodeSequencerService sequencerService) {
        boolean debug = Boolean.getBoolean("debug");
        if (debug) System.err.println("[DEBUG] Bootstrap sync: fetching existing blocks from sequencer...");
        int blockIndex = 0;
        long deadline = System.currentTimeMillis() + BOOTSTRAP_TIMEOUT_MS;

        while (true) {
            Optional<DeliverBlockResponse> opt;
            try {
                opt = sequencerService.tryDeliverBlock(blockIndex);
            } catch (io.grpc.StatusRuntimeException e) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    System.err.printf("[Bootstrap] Sequencer unreachable after %ds — starting with %d block(s).%n",
                            BOOTSTRAP_TIMEOUT_MS / 1000, blockIndex);
                    break;
                }
                System.err.printf("[Bootstrap] Sequencer unreachable (%s), retrying in %ds...%n",
                        e.getStatus().getCode(), BOOTSTRAP_RETRY_MS / 1000);
                try { Thread.sleep(BOOTSTRAP_RETRY_MS); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            if (opt.isEmpty()) {
                break;
            }

            Block block = opt.get().getBlock();
            for (Transaction tx : block.getTransactionsList()) {
                try {
                    nodeState.executeTransaction(tx);
                } catch (Exception e) {
                    System.err.printf("[Bootstrap] Error applying tx in block %d: %s%n",
                            blockIndex, e.getMessage());
                }
            }
            blockIndex++;
        }

        if (debug) System.err.printf("[DEBUG] Bootstrap sync complete: %d block(s) applied.%n", blockIndex);
        return blockIndex;
    }
}
