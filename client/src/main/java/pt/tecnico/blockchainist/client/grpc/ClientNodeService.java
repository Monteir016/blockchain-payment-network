package pt.tecnico.blockchainist.client.grpc;

import pt.tecnico.blockchainist.contract.*;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.List;


public class ClientNodeService {

    private final ManagedChannel channel;
    private final NodeServiceGrpc.NodeServiceBlockingStub stub;

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

        this.stub = NodeServiceGrpc.newBlockingStub(this.channel);
        
    }

    public void createWallet(String userId, String walletId) {
        
        CreateWalletRequest request = CreateWalletRequest.newBuilder()
                                        .setUserId(userId)
                                        .setWalletId(walletId)
                                        .build();
        stub.createWallet(request);
        
    }

    public void deleteWallet(String userId, String walletId) {

        
        DeleteWalletRequest request = DeleteWalletRequest.newBuilder()
                                        .setUserId(userId)
                                        .setWalletId(walletId)
                                        .build();
        stub.deleteWallet(request);
        
    }

    public void transfer(String srcUserId, String srcWalletId, String dstWalletId, long value) {

        TransferRequest request = TransferRequest.newBuilder()
                                                .setSrcUserId(srcUserId)
                                                .setSrcWalletId(srcWalletId)
                                                .setDstWalletId(dstWalletId)
                                                .setValue(value)
                                                .build();
        stub.transfer(request);
        
    }

    public long readBalance(String walletId) {

        ReadBalanceRequest request = ReadBalanceRequest.newBuilder()
                                                        .setWalletId(walletId)
                                                        .build();
        ReadBalanceResponse response = stub.readBalance(request);
        return response.getBalance();
        
    }

    public List<Transaction> getBlockchainState() {
        
        GetBlockchainStateRequest request = GetBlockchainStateRequest.newBuilder().build();
        GetBlockchainStateResponse response = stub.getBlockchainState(request);
        return response.getTransactionsList();
    
    }

    public void shutdown() {
        channel.shutdown();
    }

}
