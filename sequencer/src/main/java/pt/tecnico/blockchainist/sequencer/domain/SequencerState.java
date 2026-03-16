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

        transactions.add(transaction);
        boolean blockWasEmpty = openBlockTransactions.isEmpty();
        openBlockTransactions.add(transaction);

        // First transaction -> Start timer
        if (blockWasEmpty) {
            startTimeoutTimer();
        }
        if (openBlockTransactions.size() >= maxTransactionsPerBlock) {
            closeOpenBlock();
        }
        return transactions.size() - 1;
    }

    private void closeOpenBlock() {
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
            .setTimestampMs(System.currentTimeMillis())
            .build();
        blocks.add(closedBlock);

        System.out.println("[Sequencer] Closing Block " + blockId + " with " + txCount + " transactions.");
        openBlockTransactions.clear();
    }

    public synchronized void closeOpenBlockOnTimeout() {

        System.out.println("[Sequencer] Open Block Timeout (" + blockTimeoutSeconds + "s)");
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