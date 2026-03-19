package pt.tecnico.blockchainist.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import pt.tecnico.blockchainist.client.grpc.ClientNodeService;
import pt.tecnico.blockchainist.contract.Transaction;


import io.grpc.StatusRuntimeException;
import io.grpc.Status;

/** Reads user commands from stdin, dispatches gRPC calls, and prints results. */
public class CommandProcessor {
    private final boolean debug = Boolean.getBoolean("debug");

    private static final String SPACE = " ";
    private static final String CREATE_BLOCKING = "C";
    private static final String CREATE_ASYNC = "c";
    private static final String DELETE_BLOCKING = "E";
    private static final String DELETE_ASYNC = "e";
    private static final String BALANCE_BLOCKING = "S";
    private static final String BALANCE_SYNC = "s";
    private static final String TRANSFER_BLOCKING = "T";
    private static final String TRANSFER_ASYNC = "t";
    private static final String DEBUG_BLOCKCHAIN_STATE = "B";
    private static final String PAUSE = "P";
    private static final String EXIT = "X";

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

    private final AtomicLong commandCounter = new AtomicLong(0);
    private final ArrayList<ClientNodeService> nodes;

    public CommandProcessor(ArrayList<ClientNodeService> nodes) {
        this.nodes = nodes;
    }

    @FunctionalInterface
    private interface NodeOperation<T> {
        T run(ClientNodeService node) throws Exception;
    }

    void userInputLoop() {

        Scanner scanner = new Scanner(System.in);
        boolean exit = false;

        // Imprime o símbolo '>' antes do primeiro comando
        System.out.print("\n> ");

        while (!exit && scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                System.out.print("\n> ");
                continue;
            }
            String[] split = line.split(SPACE);
            try {
                switch (split[0]) {
                    case CREATE_BLOCKING:
                        this.create(split, true);
                        break;

                    case CREATE_ASYNC:
                        this.create(split, false);
                        break;

                    case DELETE_BLOCKING:
                        this.delete(split, true);
                        break;

                    case DELETE_ASYNC:
                        this.delete(split, false);
                        break;

                    case BALANCE_BLOCKING:
                    case BALANCE_SYNC:
                        this.balance(split, true);
                        break;


                    case TRANSFER_BLOCKING:
                        this.transfer(split, true);
                        break;

                    case TRANSFER_ASYNC:
                        this.transfer(split, false);
                        break;

                    case DEBUG_BLOCKCHAIN_STATE:
                        this.debugBlockchainState(split);
                        break;

                    case PAUSE:
                        this.pause(split);
                        break;

                    case EXIT:
                        exit = true;
                        break;

                    default:
                        printUsage();
                        break;
                }
            } catch (IllegalArgumentException e) {
                System.err.println("Error: " + e.getMessage());
                printUsage();
            }
            if (!exit) {
                System.out.print("\n> ");
            }
        }
        scanner.close();
    }

    private void create(String[] split, boolean isBlocking) {
        this.checkCreateCommandArgs(split);

        Long commandNumber = this.commandCounter.incrementAndGet();

        String userId = split[1];
        String walletId = split[2];
        Integer nodeIndex = Integer.parseInt(split[3]);
        Integer nodeDelay = Integer.parseInt(split[4]);

        if (debug) System.err.printf("[DEBUG] Sending CreateWallet starting at node %d: userId=%s, walletId=%s, delay=%d, blocking=%b, cmd=%d\n", nodeIndex, userId, walletId, nodeDelay, isBlocking, commandNumber);
        try {
            executeWithFailover(nodeIndex, "CreateWallet", node ->
                    { node.createWallet(userId, walletId, nodeDelay, isBlocking, commandNumber); return null; });
            if (isBlocking) {
                if (debug) System.err.printf("[DEBUG] Received CreateWallet response for cmd=%d\n", commandNumber);
                System.out.println("OK " + commandNumber);
            }
        } catch (StatusRuntimeException e) {
            if (debug) System.err.printf("[DEBUG] CreateWallet gRPC error for cmd=%d: %s\n", commandNumber, e.getStatus());
            printCommandError(e, nodeIndex);
        } catch (Exception e) {
            System.err.println("All known nodes failed for command " + commandNumber);
            if (debug) {
                System.err.println("[DEBUG] Exception stack trace:");
                e.printStackTrace(System.err);
            }
        }
    
    }

    private void delete(String[] split, boolean isBlocking) {
        this.checkDeleteCommandArgs(split);

        Long commandNumber = this.commandCounter.incrementAndGet();

        String userId = split[1];
        String walletId = split[2];
        Integer nodeIndex = Integer.parseInt(split[3]);
        Integer nodeDelay = Integer.parseInt(split[4]);

        if (debug) System.err.printf("[DEBUG] Sending DeleteWallet starting at node %d: userId=%s, walletId=%s, delay=%d, blocking=%b, cmd=%d\n", nodeIndex, userId, walletId, nodeDelay, isBlocking, commandNumber);
        try {
            executeWithFailover(nodeIndex, "DeleteWallet", node ->
                    { node.deleteWallet(userId, walletId, nodeDelay, isBlocking, commandNumber); return null; });
            if (isBlocking) {
                if (debug) System.err.printf("[DEBUG] Received DeleteWallet response for cmd=%d\n", commandNumber);
                System.out.println("OK " + commandNumber);
            }
        } catch (StatusRuntimeException e) {
            if (debug) System.err.printf("[DEBUG] DeleteWallet gRPC error for cmd=%d: %s\n", commandNumber, e.getStatus());
            printCommandError(e, nodeIndex);
        } catch (Exception e) {
            System.err.println("All known nodes failed for command " + commandNumber);
            if (debug) {
                System.err.println("[DEBUG] Exception stack trace:");
                e.printStackTrace(System.err);
            }
        }

    }

    private void balance(String[] split, boolean isBlocking) {
        this.checkBalanceCommandArgs(split);

        Long commandNumber = this.commandCounter.incrementAndGet();

        String walletId = split[1];
        Integer nodeIndex = Integer.parseInt(split[2]);
        Integer nodeDelay = Integer.parseInt(split[3]);

        if (debug) System.err.printf("[DEBUG] Sending ReadBalance starting at node %d: walletId=%s, delay=%d, blocking=%b, cmd=%d\n", nodeIndex, walletId, nodeDelay, isBlocking, commandNumber);
        try {
            long balance = executeWithFailover(nodeIndex, "ReadBalance",
                    node -> node.readBalance(walletId, nodeDelay, isBlocking, commandNumber));
            if (isBlocking) {
                if (debug) System.err.printf("[DEBUG] Received ReadBalance response for cmd=%d: balance=%d\n", commandNumber, balance);
                System.out.println("OK " + commandNumber);
                System.out.println(balance);
            }
        } catch (StatusRuntimeException e) {
            if (debug) System.err.printf("[DEBUG] ReadBalance gRPC error for cmd=%d: %s\n", commandNumber, e.getStatus());
            printCommandError(e, nodeIndex);
        } catch (Exception e) {
            System.err.println("All known nodes failed for command " + commandNumber);
            if (debug) {
                System.err.println("[DEBUG] Exception stack trace:");
                e.printStackTrace(System.err);
            }
        }
    }

    private void transfer(String[] split, boolean isBlocking) {
        this.checkTransferCommandArgs(split);

        Long commandNumber = this.commandCounter.incrementAndGet();

        String sourceUserId = split[1];
        String sourceWalletId = split[2];
        String destinationWalletId = split[3];
        Long amount = Long.parseLong(split[4]);
        Integer nodeIndex = Integer.parseInt(split[5]);
        Integer nodeDelay = Integer.parseInt(split[6]);

        if (debug) System.err.printf("[DEBUG] Sending Transfer starting at node %d: srcUserId=%s, srcWalletId=%s, dstWalletId=%s, amount=%d, delay=%d, blocking=%b, cmd=%d\n", nodeIndex, sourceUserId, sourceWalletId, destinationWalletId, amount, nodeDelay, isBlocking, commandNumber);
        try {
            executeWithFailover(nodeIndex, "Transfer", node ->
                    { node.transfer(sourceUserId, sourceWalletId, destinationWalletId, amount, nodeDelay, isBlocking, commandNumber); return null; });
            if (isBlocking) {
                if (debug) System.err.printf("[DEBUG] Received Transfer response for cmd=%d\n", commandNumber);
                System.out.println("OK " + commandNumber);
            }
        } catch (StatusRuntimeException e) {
            if (debug) System.err.printf("[DEBUG] Transfer gRPC error for cmd=%d: %s\n", commandNumber, e.getStatus());
            printCommandError(e, nodeIndex);
        } catch (Exception e) {
            System.err.println("All known nodes failed for command " + commandNumber);
            if (debug) {
                System.err.println("[DEBUG] Exception stack trace:");
                e.printStackTrace(System.err);
            }
        }
    }

    private void debugBlockchainState(String[] split) {
        this.checkDebugBlockchainStateArgs(split);

        Long commandNumber = this.commandCounter.incrementAndGet();

        Integer nodeIndex = Integer.parseInt(split[1]);

        ClientNodeService node = this.nodes.get(nodeIndex);

        if (debug) System.err.printf("[DEBUG] Sending GetBlockchainState to node %d, cmd=%d\n", nodeIndex, commandNumber);
        try {
            List<Transaction> blockchainState = node.getBlockchainState();
            if (debug) System.err.printf("[DEBUG] Received GetBlockchainState response for cmd=%d: %d txs\n", commandNumber, blockchainState.size());
            System.out.println("OK " + commandNumber);
            for (Transaction tx : blockchainState) {
                System.out.println(tx);
            }
        } catch (StatusRuntimeException e) {
            if (debug) System.err.printf("[DEBUG] GetBlockchainState gRPC error for cmd=%d: %s\n", commandNumber, e.getStatus());
            if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                System.err.println("Node " + nodeIndex + " is unreachable");
            } else {
                System.err.println(e.getStatus().getDescription());
            }
        } catch (Exception e) {
            System.err.println("Node " + nodeIndex + " is unreachable");
            if (debug) {
                System.err.println("[DEBUG] Exception stack trace:");
                e.printStackTrace(System.err);
            }
        }

    }

    private void pause(String[] split) {
        this.checkPauseArgs(split);

        Integer time;

        time = Integer.parseInt(split[1]);

        try {
            Thread.sleep(time * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkCreateCommandArgs(String[] split) {
        // C|c <user_id> <wallet_id> <node_index> <node_delay>
        if (split.length != 5) {
            throw new IllegalArgumentException("Expected 5 arguments, got " + split.length);
        }

        if (!ID_PATTERN.matcher(split[1]).matches()) {
            throw new IllegalArgumentException("Expected User ID to be composed of ASCII alphanumeric characters, got \"" + split[1] + "\"");
        }

        if (!ID_PATTERN.matcher(split[2]).matches()) {
            throw new IllegalArgumentException("Expected Wallet ID to be composed of ASCII alphanumeric characters, got \"" + split[2] + "\"");
        }

        try {
            int nodeIndex = Integer.parseInt(split[3]);
            if (nodeIndex < 0 || nodeIndex >= this.nodes.size()) {
                throw new IllegalArgumentException("Node index must be between 0 and " + (this.nodes.size() - 1));
            }
            if (Integer.parseInt(split[4]) < 0) {
                throw new IllegalArgumentException("Node delay cannot be negative");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected initial balance, node number, and node delay to be integers");
        }
    }

    private void checkDeleteCommandArgs(String[] split) {
        // E|e <user_id> <wallet_id> <node_index> <node_delay>
        if (split.length != 5) {
            throw new IllegalArgumentException("Expected 5 arguments, got " + split.length);
        }

        if (!ID_PATTERN.matcher(split[1]).matches()) {
            throw new IllegalArgumentException("Expected User ID to be composed of ASCII alphanumeric characters, got \"" + split[1] + "\"");
        }

        if (!ID_PATTERN.matcher(split[2]).matches()) {
            throw new IllegalArgumentException("Expected Wallet ID to be composed of ASCII alphanumeric characters, got \"" + split[2] + "\"");
        }

        try {
            int nodeIndex = Integer.parseInt(split[3]);
            if (nodeIndex < 0 || nodeIndex >= this.nodes.size()) {
                throw new IllegalArgumentException("Node index must be between 0 and " + (this.nodes.size() - 1));
            }
            if (Integer.parseInt(split[4]) < 0) {
                throw new IllegalArgumentException("Node delay cannot be negative");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected node number and node delay to be integers");
        }
    }

    private void checkBalanceCommandArgs(String[] split) {
        // S|s <wallet_id> <node_index> <node_delay>
        if (split.length != 4) {
            throw new IllegalArgumentException("Expected 4 arguments, got " + split.length);
        }

        if (!ID_PATTERN.matcher(split[1]).matches()) {
            throw new IllegalArgumentException("Expected Wallet ID to be composed of ASCII alphanumeric characters, got \"" + split[1] + "\"");
        }

        try {
            int nodeIndex = Integer.parseInt(split[2]);
            if (nodeIndex < 0 || nodeIndex >= this.nodes.size()) {
                throw new IllegalArgumentException("Node index must be between 0 and " + (this.nodes.size() - 1));
            }
            if (Integer.parseInt(split[3]) < 0) {
                throw new IllegalArgumentException("Node delay cannot be negative");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected node number and node delay to be integers");
        }
    }

    private void checkTransferCommandArgs(String[] split) {
        // T|t <source_user_id> <source_wallet_id> <destination_wallet_id> <amount> <node_index> <node_delay>
        if (split.length != 7) {
            throw new IllegalArgumentException("Expected 7 arguments, got " + split.length);
        }

        if (!ID_PATTERN.matcher(split[1]).matches()) {
            throw new IllegalArgumentException("Expected Source User ID to be composed of ASCII alphanumeric characters, got \"" + split[1] + "\"");
        }

        if (!ID_PATTERN.matcher(split[2]).matches()) {
            throw new IllegalArgumentException("Expected Source Wallet ID to be composed of ASCII alphanumeric characters, got \"" + split[2] + "\"");
        }

        if (!ID_PATTERN.matcher(split[3]).matches()) {
            throw new IllegalArgumentException("Expected Destination Wallet ID to be composed of ASCII alphanumeric characters, got \"" + split[1] + "\"");
        }

        try {
            if (Long.parseLong(split[4]) < 0) {
                throw new IllegalArgumentException("Amount cannot be negative");
            }
            int nodeIndex = Integer.parseInt(split[5]);
            if (nodeIndex < 0 || nodeIndex >= this.nodes.size()) {
                throw new IllegalArgumentException("Node index must be between 0 and " + (this.nodes.size() - 1));
            }
            if (Integer.parseInt(split[6]) < 0) {
                throw new IllegalArgumentException("Node delay cannot be negative");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected amount, node number, and node delay to be integers");
        }
    }

    private void checkDebugBlockchainStateArgs(String[] split) {
        // B <node_index>
        if (split.length != 2) {
            throw new IllegalArgumentException("Expected 2 arguments, got " + split.length);
        }

        try {
            int nodeIndex = Integer.parseInt(split[1]);
            if (nodeIndex < 0 || nodeIndex >= this.nodes.size()) {
                throw new IllegalArgumentException("Node index must be between 0 and " + (this.nodes.size() - 1));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected node index to be an integer");
        }
    }

    private void checkPauseArgs(String[] split) {
        // P <integer>
        if (split.length != 2) {
            throw new IllegalArgumentException("Expected 2 arguments, got " + split.length);
        }

        try {
            if (Integer.parseInt(split[1]) < 0) {
                throw new IllegalArgumentException("Pause time cannot be negative");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected pause time to be an integer");
        }
    }

    private static void printUsage() {
        System.err.println("Usage:\n" +
                "- C|c <user_id> <wallet_id> <node_index> <node_delay>\n" +
                "- E|e <user_id> <wallet_id> <node_index> <node_delay>\n" +
                "- S|s <wallet_id> <node_index> <node_delay>\n" +
                "- T|t <source_user_id> <source_wallet_id> <destination_wallet_id> <amount> <node_index> <node_delay>\n" +
                "- B <node_index>\n" +
                "- P <integer>\n" +
                "- X\n");
    }

    private boolean isRetryable(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED;
    }

    private void printCommandError(StatusRuntimeException e, int initialNodeIndex) {
        if (isRetryable(e)) {
            String description = e.getStatus().getDescription();
            if (description == null || description.isBlank()) {
                description = "All known nodes are unreachable";
            }
            System.err.println(description + " (starting at node " + initialNodeIndex + ")");
            return;
        }

        String description = e.getStatus().getDescription();
        if (description != null && !description.isBlank()) {
            System.err.println(description);
        } else {
            System.err.println(e.getStatus());
        }
    }

    private <T> T executeWithFailover(int initialNodeIndex, String operationName, NodeOperation<T> operation) throws Exception {
        StatusRuntimeException lastRetryableStatus = null;
        Exception lastException = null;

        for (int attempt = 0; attempt < this.nodes.size(); attempt++) {
            int currentIndex = (initialNodeIndex + attempt) % this.nodes.size();
            ClientNodeService node = this.nodes.get(currentIndex);
            try {
                if (debug && attempt > 0) {
                    System.err.printf("[DEBUG] Retry %s on node %d (attempt %d/%d)\n",
                            operationName, currentIndex, attempt + 1, this.nodes.size());
                }
                return operation.run(node);
            } catch (StatusRuntimeException e) {
                if (!isRetryable(e)) {
                    throw e;
                }
                lastRetryableStatus = e;
                if (debug) {
                    System.err.printf("[DEBUG] Retryable gRPC error on node %d while executing %s: %s\n",
                            currentIndex, operationName, e.getStatus());
                }
            } catch (Exception e) {
                lastException = e;
                if (debug) {
                    System.err.printf("[DEBUG] Retryable client-side error on node %d while executing %s: %s\n",
                            currentIndex, operationName, e.getMessage());
                }
            }
        }

        if (lastRetryableStatus != null) {
            throw new StatusRuntimeException(Status.UNAVAILABLE.withDescription(
                    String.format("All known nodes failed while executing %s", operationName)));
        }
        if (lastException != null) {
            throw lastException;
        }

        throw new StatusRuntimeException(Status.UNAVAILABLE.withDescription(
                String.format("No nodes configured for %s", operationName)));
    }
}
