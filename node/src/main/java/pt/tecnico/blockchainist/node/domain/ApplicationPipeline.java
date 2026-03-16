package pt.tecnico.blockchainist.node.domain;

import pt.tecnico.blockchainist.contract.Block;
import pt.tecnico.blockchainist.contract.DeliverBlockResponse;
import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.node.grpc.NodeSequencerService;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single-threaded pipeline that polls the sequencer for blocks and applies
 * transactions in total order. Write requests from clients register a
 * CompletableFuture here and are completed when the corresponding transaction
 * is applied in this thread
 */
public class ApplicationPipeline implements Runnable {

    private static final int POLL_MS = 50;

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
            Optional<DeliverBlockResponse> opt = sequencerService.tryDeliverBlock(next);
            if (opt.isPresent()) {
                Block block = opt.get().getBlock();
                for (Transaction tx : block.getTransactionsList()) {
                    try {
                        nodeState.executeTransaction(tx);
                        completeFirstPendingFor(tx, null);
                    } catch (Throwable e) {
                        completeFirstPendingFor(tx, e);
                    }
                }
                nextBlockIndex.incrementAndGet();
            } else {
                try {
                    Thread.sleep(POLL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                    break;
                }
            }
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
