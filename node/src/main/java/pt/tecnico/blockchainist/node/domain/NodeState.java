package pt.tecnico.blockchainist.node.domain;

import pt.tecnico.blockchainist.contract.CreateWalletRequest;
import pt.tecnico.blockchainist.contract.DeleteWalletRequest;
import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.contract.TransferRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * Domain class representing the global state of the cryptocurrency.
 * All public methods are synchronized to ensure thread-safe access
 * from the gRPC server thread pool.
 */
public class NodeState {

    // Wallet ownership: walletId -> userId
    private final HashMap<String, String> walletOwners = new HashMap<>();
    // Wallet balances: walletId -> balance
    private final HashMap<String, Long> walletBalances = new HashMap<>();

    private final HashMap<String, String> organizationByUser = new HashMap<>();
    
    // Transaction ledger (A.2: individual transactions; B.1+: blocks)
    private final List<Transaction> transactionLedger = new ArrayList<>();

    /**
     * - Key: requestId (stable across retries).
     * - Value: whether the request succeeded; if it failed, store the error message.
     *
     * When the same requestId appears multiple times (client retry causing duplicate broadcast),
     * we skip state mutation and return the same outcome deterministically.
     */
    private final HashMap<String, ExecutionOutcome> outcomesByRequestId = new HashMap<>();

    private static class ExecutionOutcome {
        private final boolean success;
        private final String errorMessage; // non-null when success=false

        private ExecutionOutcome(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }
    }

    private static String normalizeErrorMessage(String msg) {
        // Ensure deterministic and non-null gRPC error descriptions for retries/duplicates.
        if (msg == null || msg.isBlank()) {
            return "Invalid request";
        }
        return msg;
    }


    public NodeState() {
        // Pre-existing central bank wallet with initial balance of 1000
        walletOwners.put("bc", "BC");
        walletBalances.put("bc", 1000L);

        // Pre-defined user-organization list
        organizationByUser.put("BC", "OrgA");

        organizationByUser.put("Alice", "OrgA");
        organizationByUser.put("Bob", "OrgA");
        organizationByUser.put("Charlie", "OrgA");

        organizationByUser.put("David", "OrgB");
        organizationByUser.put("Emma", "OrgB");
        organizationByUser.put("Fred", "OrgB");

        organizationByUser.put("Ginger", "OrgC");
        organizationByUser.put("Henry", "OrgC");
        organizationByUser.put("Iris", "OrgC");

    }

    public void isUserFromOrganization(String user, String org) {
        String userOrg = organizationByUser.get(user);
        if (userOrg == null || !userOrg.equals(org)) {
            throw new IllegalArgumentException("User " + user + " is not from organization " + org);
        }
    }

    public synchronized void createWallet(String userId, String walletId) {

        if (walletOwners.containsKey(walletId)) {
            throw new IllegalArgumentException("Wallet already exists: " + walletId);
        }

        walletOwners.put(walletId, userId);
        walletBalances.put(walletId, 0L);
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

    }

    private String extractRequestId(Transaction transaction) {
        switch (transaction.getOperationCase()) {
            case CREATE_WALLET:
                return transaction.getCreateWallet().getRequestId();
            case DELETE_WALLET:
                return transaction.getDeleteWallet().getRequestId();
            case TRANSFER:
                return transaction.getTransfer().getRequestId();
            default:
                return "";
        }
    }

    // Execute a transaction already ordered by the sequencer.
    public synchronized void executeTransaction(Transaction transaction) {
        String requestId = extractRequestId(transaction);

        transactionLedger.add(transaction);

        if (requestId == null || requestId.isBlank()) {
            executeWithoutIdempotency(transaction);
            return;
        }

        ExecutionOutcome known = outcomesByRequestId.get(requestId);
        if (known != null) {
            if (known.success) {
                return;
            }
            throw new IllegalArgumentException(normalizeErrorMessage(known.errorMessage));
        }

        // First time we see this requestId: execute and remember the outcome.
        try {
            executeWithoutIdempotency(transaction);
            outcomesByRequestId.put(requestId, new ExecutionOutcome(true, null));
        } catch (IllegalArgumentException e) {
            outcomesByRequestId.put(
                    requestId,
                    new ExecutionOutcome(false, normalizeErrorMessage(e.getMessage()))
            );
            throw e;
        }
    }

    private void executeWithoutIdempotency(Transaction transaction) {
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
        return new ArrayList<>(transactionLedger);  // defensive copy
    }

}
