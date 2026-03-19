package pt.tecnico.blockchainist.node.domain;

import pt.tecnico.blockchainist.contract.Block;
import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.node.grpc.NodeSequencerService;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single-threaded pipeline that blocks waiting for sequencer blocks and applies
 * transactions in total order. Write requests from clients register a
 * CompletableFuture here and are completed when the corresponding transaction
 * is applied in this thread
 */
public class ApplicationPipeline implements Runnable {

    private static final int POLL_MS = 100;

    private final NodeState nodeState;
    private final NodeSequencerService sequencerService;
    private final AtomicInteger nextBlockIndex = new AtomicInteger(0);
    /** Pending futures per transaction (by content equality). Supports duplicate tx content. */
    private final ConcurrentHashMap<Transaction, BlockingQueue<CompletableFuture<Void>>> pendingByTransaction = new ConcurrentHashMap<>();
    private volatile boolean running = true;
    private Thread workerThread;

    public ApplicationPipeline(NodeState nodeState, NodeSequencerService sequencerService) {
        this.nodeState = nodeState;
        this.sequencerService = sequencerService;
    }

    /**
     * Registers a future to be completed when the given transaction is applied
     * in the pipeline. Called by the gRPC handler after broadcasting the transaction.
     */
    public void registerPending(Transaction transaction, CompletableFuture<Void> future) {
        pendingByTransaction
                .computeIfAbsent(transaction, k -> new LinkedBlockingQueue<>())
                .add(future);
    }

    /**
     * Completes the first pending future for this transaction (by content).
     * If the application threw, the future is completed with that exception.
     */
    private void completeFirstPendingFor(Transaction transaction, Throwable failure) {
        BlockingQueue<CompletableFuture<Void>> queue = pendingByTransaction.get(transaction);
        if (queue == null) return;
        CompletableFuture<Void> f = queue.poll();
        if (f != null) {
            if (failure == null) {
                f.complete(null);
            } else {
                f.completeExceptionally(failure);
            }
        }
    }

    @Override
    public void run() {
        while (running) {
            int next = nextBlockIndex.get();
            boolean debug = Boolean.getBoolean("debug");
            try {
                Block block = sequencerService.deliverBlockBlocking(next);
                if (debug) {
                    System.err.printf("[DEBUG] [Pipeline] Received block %d with %d txs\n",
                            block.getBlockId(), block.getTransactionsCount());
                }
                for (Transaction tx : block.getTransactionsList()) {
                    try {
                        if (debug) System.err.printf("[DEBUG] [Pipeline] Applying tx: %s\n", tx);
                        nodeState.executeTransaction(tx);
                        if (debug) System.err.printf("[DEBUG] [Pipeline] Applied tx: %s\n", tx);
                        completeFirstPendingFor(tx, null);
                    } catch (Throwable e) {
                        if (debug) System.err.printf("[DEBUG] [Pipeline] Error applying tx: %s\n", tx);
                        completeFirstPendingFor(tx, e);
                    }
                }
                nextBlockIndex.incrementAndGet();
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

    /** Starts the dedicated application thread. */
    public void start() {
        workerThread = new Thread(this, "block-application");
        workerThread.setDaemon(false);
        workerThread.start();
    }

    /** Stops the pipeline thread. */
    public void stop() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    /** For B.2 sync: set next block index when node catches up from sequencer. */
    public void setNextBlockIndex(int index) {
        nextBlockIndex.set(index);
    }

    public int getNextBlockIndex() {
        return nextBlockIndex.get();
    }
}
