package pt.tecnico.blockchainist.node.domain;

import pt.tecnico.blockchainist.contract.Block;
import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.node.crypto.BlockSignatureVerifier;
import pt.tecnico.blockchainist.node.grpc.NodeSequencerService;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread pipeline that ensures that the transactions are applied in the same order of the sequencer, and notifies the client when their request is really done
 */
public class ApplicationPipeline implements Runnable {

    private static final int POLL_MS = 100;

    private final NodeState nodeState;
    private final NodeSequencerService sequencerService;
    private final BlockSignatureVerifier blockSignatureVerifier;
    private final AtomicInteger nextBlockIndex = new AtomicInteger(0);

    // A future for each pending transaction, so we can complete it when the transaction is applied
    private final ConcurrentHashMap<Transaction, CompletableFuture<Void>> pendingByTransaction = new ConcurrentHashMap<>();


    private volatile boolean running = true;
    private Thread workerThread;

    public ApplicationPipeline(
            NodeState nodeState,
            NodeSequencerService sequencerService,
            BlockSignatureVerifier blockSignatureVerifier) {
        this.nodeState = nodeState;
        this.sequencerService = sequencerService;
        this.blockSignatureVerifier = blockSignatureVerifier;
    }

    // Called by the gRPC handler after broadcasting a transaction
    public void registerPending(Transaction transaction, CompletableFuture<Void> future) {
        pendingByTransaction.put(transaction, future);
    }

    // Transaction has been applied, complete the future so the client can return the response
    private void completePendingFor(Transaction transaction, Throwable failure) {
        CompletableFuture<Void> f = pendingByTransaction.remove(transaction);
        if (f != null) {
            if (failure == null) f.complete(null);
            else f.completeExceptionally(failure);
        }
    }

    @Override
    public void run() {
        while (running) {
            int next = nextBlockIndex.get();
            boolean debug = Boolean.getBoolean("debug");
            try {
                Block block = sequencerService.deliverBlockBlocking(next);
                blockSignatureVerifier.verifyOrThrow(block);
                if (debug) {
                    System.err.printf("[DEBUG] [Pipeline] Received block %d with %d txs\n",
                            block.getBlockId(), block.getTransactionsCount());
                }
                for (Transaction tx : block.getTransactionsList()) {
                    try {
                        if (debug) System.err.printf("[DEBUG] [Pipeline] Applying tx: %s\n", tx);
                        nodeState.executeTransaction(tx);
                        if (debug) System.err.printf("[DEBUG] [Pipeline] Applied tx: %s\n", tx);
                        completePendingFor(tx, null);
                    } catch (Throwable e) {
                        if (debug) System.err.printf("[DEBUG] [Pipeline] Error applying tx: %s\n", tx);
                        completePendingFor(tx, e);
                    }
                }
                nextBlockIndex.incrementAndGet();
            } catch (SecurityException e) {
                System.err.printf("[Security] Rejected block %d: %s%n", next, e.getMessage());
                sleepOrStop(POLL_MS * 20);
                continue;
            } catch (io.grpc.StatusRuntimeException e) {
                if (debug) System.err.println("[DEBUG] [Pipeline] Sequencer unavailable: " + e.getStatus() + " retrying...");
                sleepOrStop(POLL_MS * 20);
                continue;
            }
        }
    }

    private void sleepOrStop(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    // Starts the thread that runs the pipeline loop
    public void start() {
        workerThread = new Thread(this, "block-application");
        workerThread.setDaemon(false);
        workerThread.start();
    }

    // Stops the pipeline thread
    public void stop() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    // Used for sync: set the next block index when the node catches up with the sequencer
    public void setNextBlockIndex(int index) {
        nextBlockIndex.set(index);
    }

    public int getNextBlockIndex() {
        return nextBlockIndex.get();
    }
}
