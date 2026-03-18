package pt.tecnico.blockchainist.client.grpc;

import pt.tecnico.blockchainist.contract.*;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

import java.util.List;




/**
 * gRPC client for communicating with a blockchain node.
 * Encapsulates the managed channel and blocking blockingStub.
 * Exceptions are propagated to the caller (CommandProcessor).
 */
public class ClientNodeService {
    private static final String DELAY_HEADER_NAME = "delay-seconds";
    private static final Metadata.Key<String> DELAY_HEADER_KEY =
            Metadata.Key.of(DELAY_HEADER_NAME, Metadata.ASCII_STRING_MARSHALLER);

    private final ManagedChannel channel;
    private final NodeServiceGrpc.NodeServiceBlockingStub blockingStub;
    private final NodeServiceGrpc.NodeServiceStub asyncStub;
    private final boolean debug;

    public ClientNodeService(String host, int port, String organization, boolean debug) {
        this.debug = debug;
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

    private Metadata createDelayMetadata(int delaySeconds) {
        Metadata headers = new Metadata();
        headers.put(DELAY_HEADER_KEY, Integer.toString(delaySeconds));
        return headers;
    }

    private NodeServiceGrpc.NodeServiceBlockingStub blockingStubWithDelay(int delaySeconds) {
        Metadata headers = createDelayMetadata(delaySeconds);
        return blockingStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private NodeServiceGrpc.NodeServiceStub asyncStubWithDelay(int delaySeconds) {
        Metadata headers = createDelayMetadata(delaySeconds);
        return asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    public void createWallet(String userId, String walletId, int delaySeconds, boolean isBlocking, long commandNumber) {
        CreateWalletRequest request = CreateWalletRequest.newBuilder()
                                        .setUserId(userId)
                                        .setWalletId(walletId)
                                        .build();
        if (isBlocking) {
            NodeServiceGrpc.NodeServiceBlockingStub stubWithHeaders = blockingStubWithDelay(delaySeconds);
            stubWithHeaders.createWallet(request);
        } else {
            NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
            stubWithHeaders.createWallet(request, new ClientNodeObserver<CreateWalletResponse>(commandNumber, debug));
        }
    }

    public void deleteWallet(String userId, String walletId, int delaySeconds, boolean isBlocking, long commandNumber) {
        DeleteWalletRequest request = DeleteWalletRequest.newBuilder()
                                        .setUserId(userId)
                                        .setWalletId(walletId)
                                        .build();
        if (isBlocking) {
            NodeServiceGrpc.NodeServiceBlockingStub stubWithHeaders = blockingStubWithDelay(delaySeconds);
            stubWithHeaders.deleteWallet(request);
        } else {
            NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
            stubWithHeaders.deleteWallet(request, new ClientNodeObserver<DeleteWalletResponse>(commandNumber, debug));
        }
    }

    public void transfer(String srcUserId, String srcWalletId, String dstWalletId, long value, int delaySeconds, boolean isBlocking, long commandNumber) {
        TransferRequest request = TransferRequest.newBuilder()
                                                .setSrcUserId(srcUserId)
                                                .setSrcWalletId(srcWalletId)
                                                .setDstWalletId(dstWalletId)
                                                .setValue(value)
                                                .build();
        if (isBlocking) {
            NodeServiceGrpc.NodeServiceBlockingStub stubWithHeaders = blockingStubWithDelay(delaySeconds);
            stubWithHeaders.transfer(request);
        } else {
            NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
            stubWithHeaders.transfer(request, new ClientNodeObserver<TransferResponse>(commandNumber, debug));
        }
    }

    public long readBalance(String walletId, int delaySeconds, boolean isBlocking, long commandNumber) {
        ReadBalanceRequest request = ReadBalanceRequest.newBuilder()
                                                        .setWalletId(walletId)
                                                        .build();
        if (isBlocking) {
            NodeServiceGrpc.NodeServiceBlockingStub stubWithHeaders = blockingStubWithDelay(delaySeconds);
            ReadBalanceResponse response = stubWithHeaders.readBalance(request);
            return response.getBalance();
        } else {
            NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
                stubWithHeaders.readBalance(request, new ClientNodeObserver<pt.tecnico.blockchainist.contract.ReadBalanceResponse>(commandNumber, debug));
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
