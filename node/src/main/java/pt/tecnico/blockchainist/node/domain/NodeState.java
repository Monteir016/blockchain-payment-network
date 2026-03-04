package pt.tecnico.blockchainist.node.domain;

import pt.tecnico.blockchainist.contract.CreateWalletRequest;
import pt.tecnico.blockchainist.contract.DeleteWalletRequest;
import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.contract.TransferRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class NodeState {

    HashMap<String, String> walletOwners = new HashMap<>();
    HashMap<String, Long> walletBalances = new HashMap<>();
    
    // - The transaction ledger (up to A.2, a chain of individual transactions; after B.1, a chain of blocks)
    List<Transaction> transactionLedger = new ArrayList<>();


    public NodeState() {
        // Initialize the 'bc' wallet
        walletOwners.put("bc", "BC");
        walletBalances.put("bc", 1000L);

    }

    public synchronized void createWallet(String userId, String walletId) {

        if (walletOwners.containsKey(walletId)) {
            throw new IllegalArgumentException("Wallet already exists: " + walletId);
        }

        walletOwners.put(walletId, userId);
        walletBalances.put(walletId, 0L);

        Transaction tx = Transaction.newBuilder()
                        .setCreateWallet(CreateWalletRequest.newBuilder()
                        .setUserId(userId)
                        .setWalletId(walletId)
                        .build())
                        .build();
        transactionLedger.add(tx);
    }

    public synchronized void deleteWallet(String userId, String walletId) {
        
        if (!walletOwners.containsKey(walletId)) {
            throw new IllegalArgumentException("Wallet does not exist: " + walletId);
        }

        if (walletBalances.get(walletId) != 0) {
            throw new IllegalArgumentException("Cannot delete wallet with non-zero balance: " + walletId);
        }

        if (!walletOwners.get(walletId).equals(userId)) {
            throw new IllegalArgumentException("User " + userId + " does not own wallet " + walletId);
        }


        walletOwners.remove(walletId);
        walletBalances.remove(walletId);

        Transaction tx = Transaction.newBuilder()
                        .setDeleteWallet(DeleteWalletRequest.newBuilder()
                        .setUserId(userId)
                        .setWalletId(walletId)
                        .build())
                        .build();
        transactionLedger.add(tx);
    }

    public synchronized void transfer(String srcUserId, String srcWalletId, String dstWalletId, long amount) {
        
        if (!walletOwners.containsKey(srcWalletId)) {
            throw new IllegalArgumentException("Source wallet does not exist: " + srcWalletId);
        }

        if (!walletOwners.containsKey(dstWalletId)) {
            throw new IllegalArgumentException("Destination wallet does not exist: " + dstWalletId);
        }

        if (!walletOwners.get(srcWalletId).equals(srcUserId)) {
            throw new IllegalArgumentException("User " + srcUserId + " does not own source wallet " + srcWalletId);
        }

        if (walletBalances.get(srcWalletId) < amount) {
            throw new IllegalArgumentException("Insufficient balance in source wallet " + srcWalletId);
        }

        if (amount <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive: " + amount);
        }

        // Perform the transfer
        walletBalances.put(srcWalletId, walletBalances.get(srcWalletId) - amount);
        walletBalances.put(dstWalletId, walletBalances.get(dstWalletId) + amount);

        // Record the transaction in the ledger
        Transaction tx = Transaction.newBuilder()
                        .setTransfer(TransferRequest.newBuilder()
                        .setSrcUserId(srcUserId)
                        .setSrcWalletId(srcWalletId)
                        .setDstWalletId(dstWalletId)
                        .setValue(amount)
                        .build())
                        .build();
        transactionLedger.add(tx);

    }

    // Executa uma transação já ordenada pelo sequenciador.
    public synchronized void executeTransaction(Transaction transaction) {
        switch (transaction.getOperationCase()) {
            case CREATE_WALLET:
                CreateWalletRequest cw = transaction.getCreateWallet();
                createWallet(cw.getUserId(), cw.getWalletId());
                break;
            case DELETE_WALLET:
                DeleteWalletRequest dw = transaction.getDeleteWallet();
                deleteWallet(dw.getUserId(), dw.getWalletId());
                break;
            case TRANSFER:
                TransferRequest tr = transaction.getTransfer();
                transfer(tr.getSrcUserId(), tr.getSrcWalletId(), tr.getDstWalletId(), tr.getValue());
                break;
            default:
                throw new IllegalArgumentException("Unknown transaction type");
        }
    }

    public synchronized long readBalance(String walletId) {
        
        if (!walletOwners.containsKey(walletId)) {
            throw new IllegalArgumentException("Wallet does not exist: " + walletId);
        }

        return walletBalances.get(walletId);
    }

    public synchronized List<Transaction> getBlockchainState() {
        return new ArrayList<>(transactionLedger);  // cópia defensiva
    }

}
