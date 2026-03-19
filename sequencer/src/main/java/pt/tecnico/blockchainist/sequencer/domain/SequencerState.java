package pt.tecnico.blockchainist.sequencer.domain;

import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.contract.Block;

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Domain class maintaining the totally ordered list of transactions.
 * The list index serves as the sequence number.
 */
public class SequencerState {

    // Ordered transaction list (index = sequence number)
    private final List<Transaction> transactions = new ArrayList<>();
    
    private final List<Block> blocks = new ArrayList<>();
    private final List<Transaction> openBlockTransactions = new ArrayList<>();

    private final int maxTransactionsPerBlock;
    private final int blockTimeoutSeconds;

    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> timeoutTask = null;

    public SequencerState(int maxTransactionsPerBlock,
                         int blockTimeoutSeconds,
                         ScheduledExecutorService scheduler) {
        this.maxTransactionsPerBlock = maxTransactionsPerBlock;
        this.blockTimeoutSeconds = blockTimeoutSeconds;
        this.scheduler = scheduler;
    }

    public synchronized int addTransaction(Transaction transaction) {

        boolean debug = Boolean.getBoolean("debug");
        transactions.add(transaction);
        boolean blockWasEmpty = openBlockTransactions.isEmpty();
        openBlockTransactions.add(transaction);
        if (debug) System.err.printf("[DEBUG] [Sequencer] Received transaction: %s\n", transaction);

        // First transaction -> Start timer
        if (blockWasEmpty) {
            if (debug) System.err.println("[DEBUG] [Sequencer] First transaction in block, starting timer");
            startTimeoutTimer();
        }
        if (openBlockTransactions.size() >= maxTransactionsPerBlock) {
            if (debug) System.err.println("[DEBUG] [Sequencer] Block full, closing block");
            closeOpenBlock();
        }
        return transactions.size() - 1;
    }

    private void closeOpenBlock() {
        boolean debug = Boolean.getBoolean("debug");
        if (openBlockTransactions.isEmpty()) {
            return;
        }

        // Cancel timer
        cancelTimeoutTimer();

        final int blockId = getClosedBlockCount();
        final int txCount = openBlockTransactions.size();
        final Block closedBlock = Block.newBuilder()
            .setBlockId(blockId)
            .addAllTransactions(openBlockTransactions)
            .build();
        blocks.add(closedBlock);
        // Wake up any nodes waiting for the next block.
        notifyAll();

        if (debug) System.err.println("[DEBUG] [Sequencer] Closing Block " + blockId + " with " + txCount + " transactions.");
        openBlockTransactions.clear();
    }

    public synchronized void closeOpenBlockOnTimeout() {
        boolean debug = Boolean.getBoolean("debug");
        if (debug) System.err.println("[DEBUG] [Sequencer] Open Block Timeout (" + blockTimeoutSeconds + "s)");
        closeOpenBlock();
    }

    private void startTimeoutTimer() {
        cancelTimeoutTimer();
        timeoutTask = scheduler.schedule(this::closeOpenBlockOnTimeout, blockTimeoutSeconds, TimeUnit.SECONDS);
    }

    private void cancelTimeoutTimer() {
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
            timeoutTask = null;
        }
    }

    public synchronized Block getBlock(int blockId) {
        if (blockId < 0 || blockId >= blocks.size()) {
            throw new IllegalArgumentException(
                "Invalid block ID: " + blockId +
                "Current range: [0, " + (blocks.size() - 1) + "]"
            );
        }
        return blocks.get(blockId);
    }

    public synchronized Block waitForBlock(int blockId) throws InterruptedException {
        if (blockId < 0) {
            throw new IllegalArgumentException("Invalid block ID: " + blockId);
        }
        while (blockId >= blocks.size()) {
            wait();
        }
        return blocks.get(blockId);
    }


    public synchronized Transaction getTransaction(int sequenceNumber) {
        if (sequenceNumber < 0 || sequenceNumber >= transactions.size()) {
            throw new IllegalArgumentException(
                "Invalid sequence number: " + sequenceNumber +
                "Current range: [0, " + (transactions.size() - 1) + "]"
            );
        }
        return transactions.get(sequenceNumber);
    }

    public synchronized int getClosedBlockCount() {
        return blocks.size();
    }

    public int getMaxTransactionsPerBlock() {
        return maxTransactionsPerBlock;
    }

    public int getBlockTimeoutSeconds() {
        return blockTimeoutSeconds;
    }
}