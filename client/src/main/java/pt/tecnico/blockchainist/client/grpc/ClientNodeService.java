package pt.tecnico.blockchainist.client.grpc;

import pt.tecnico.blockchainist.contract.*;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.List;

import org.w3c.dom.Node;

import pt.tecnico.blockchainist.client.grpc.ClientNodeObserver;


/**
 * gRPC client for communicating with a blockchain node.
 * Encapsulates the managed channel and blocking blockingStub.
 * Exceptions are propagated to the caller (CommandProcessor).
 */
public class ClientNodeService {

    private final ManagedChannel channel;
    private final NodeServiceGrpc.NodeServiceBlockingStub blockingStub;
    private final NodeServiceGrpc.NodeServiceStub asyncStub;

    public ClientNodeService(String host, int port, String organization) {
        
        // Input validation
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }

        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port number must be between 0 and 65535");
        }

        if (organization == null || organization.isEmpty()) {
            throw new IllegalArgumentException("Organization cannot be null or empty");
        }
        
        // Channel creation
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext() 
                .build();

        this.blockingStub = NodeServiceGrpc.newBlockingStub(this.channel);
        this.asyncStub = NodeServiceGrpc.newStub(this.channel);
    }

    public void createWallet(String userId, String walletId, boolean isBlocking, long commandNumber) {
        CreateWalletRequest request = CreateWalletRequest.newBuilder()
                                        .setUserId(userId)
                                        .setWalletId(walletId)
                                        .build();
        if (isBlocking) {
            blockingStub.createWallet(request);
        } else {
            asyncStub.createWallet(request, new ClientNodeObserver<CreateWalletResponse>(commandNumber));
        }
    }

    public void deleteWallet(String userId, String walletId, boolean isBlocking, long commandNumber) {
        DeleteWalletRequest request = DeleteWalletRequest.newBuilder()
                                        .setUserId(userId)
                                        .setWalletId(walletId)
                                        .build();
        if (isBlocking) {
            blockingStub.deleteWallet(request);
        } else {
            asyncStub.deleteWallet(request, new ClientNodeObserver<DeleteWalletResponse>(commandNumber));
        }
    }

    public void transfer(String srcUserId, String srcWalletId, String dstWalletId, long value, boolean isBlocking, long commandNumber) {
        TransferRequest request = TransferRequest.newBuilder()
                                                .setSrcUserId(srcUserId)
                                                .setSrcWalletId(srcWalletId)
                                                .setDstWalletId(dstWalletId)
                                                .setValue(value)
                                                .build();
        if (isBlocking) {
            blockingStub.transfer(request);
        } else {
            asyncStub.transfer(request, new ClientNodeObserver<TransferResponse>(commandNumber));
        }
    }

    public long readBalance(String walletId, boolean isBlocking, long commandNumber) {
        ReadBalanceRequest request = ReadBalanceRequest.newBuilder()
                                                        .setWalletId(walletId)
                                                        .build();
        if (isBlocking) {
            ReadBalanceResponse response = blockingStub.readBalance(request);
            return response.getBalance();
        } else {
            asyncStub.readBalance(request, new ClientNodeObserver<pt.tecnico.blockchainist.contract.ReadBalanceResponse>(commandNumber));
        }
        return 0;
    }

    public List<Transaction> getBlockchainState() {
        
        GetBlockchainStateRequest request = GetBlockchainStateRequest.newBuilder().build();
        GetBlockchainStateResponse response = blockingStub.getBlockchainState(request);
        return response.getTransactionsList();
    
    }

    public void shutdown() {
        channel.shutdown();
    }

}
