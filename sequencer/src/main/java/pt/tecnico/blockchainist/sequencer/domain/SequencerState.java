package pt.tecnico.blockchainist.sequencer.domain;

import pt.tecnico.blockchainist.contract.Transaction;

import java.util.ArrayList;
import java.util.List;

public class SequencerState {

    // Lista de transações ordenadas (o índice é o sequence_number)
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