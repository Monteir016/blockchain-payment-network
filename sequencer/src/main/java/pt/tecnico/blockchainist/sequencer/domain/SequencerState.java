package pt.tecnico.blockchainist.sequencer.domain;

import pt.tecnico.blockchainist.contract.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain class maintaining the totally ordered list of transactions.
 * The list index serves as the sequence number.
 */
public class SequencerState {

    // Ordered transaction list (index = sequence number)
    private final List<Transaction> transactions = new ArrayList<>();

    public synchronized int addTransaction(Transaction transaction) {
        transactions.add(transaction);
        return transactions.size() - 1; // 0-based index
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
}