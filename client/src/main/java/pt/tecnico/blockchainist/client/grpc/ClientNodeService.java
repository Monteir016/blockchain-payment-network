package pt.tecnico.blockchainist.client.grpc;

import pt.tecnico.blockchainist.contract.*;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;




/**
 * gRPC client for communicating with a blockchain node.
 * Encapsulates the managed channel and blocking blockingStub.
 * Exceptions are propagated to the caller (CommandProcessor).
 */
public class ClientNodeService {
    private static final int REQUEST_TIMEOUT_SECONDS = 8;
    private static final String DELAY_HEADER_NAME = "delay-seconds";
    private static final Metadata.Key<String> DELAY_HEADER_KEY =
            Metadata.Key.of(DELAY_HEADER_NAME, Metadata.ASCII_STRING_MARSHALLER);

    private ManagedChannel channel;
    private NodeServiceGrpc.NodeServiceBlockingStub blockingStub;
    private NodeServiceGrpc.NodeServiceStub asyncStub;
    private final boolean debug;
    private final String host;
    private final int port;
    // private final String organization; // Not used, can be removed if not needed

    public ClientNodeService(String host, int port, String organization, boolean debug) {
        this.debug = debug;
        this.host = host;
        this.port = port;
        // this.organization = organization; // Not used
        createChannelAndStubs();
    }

    private void createChannelAndStubs() {
        if (this.channel != null) this.channel.shutdown();
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.blockingStub = NodeServiceGrpc.newBlockingStub(this.channel);
        this.asyncStub = NodeServiceGrpc.newStub(this.channel);
    }

    private boolean isUnavailable(StatusRuntimeException e) {
        return e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE;
    }

    private Metadata createDelayMetadata(int delaySeconds) {
        Metadata headers = new Metadata();
        headers.put(DELAY_HEADER_KEY, Integer.toString(delaySeconds));
        return headers;
    }

    private NodeServiceGrpc.NodeServiceBlockingStub blockingStubWithDelay(int delaySeconds) {
        Metadata headers = createDelayMetadata(delaySeconds);
        return blockingStub
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                .withDeadlineAfter(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private NodeServiceGrpc.NodeServiceStub asyncStubWithDelay(int delaySeconds) {
        Metadata headers = createDelayMetadata(delaySeconds);
        return asyncStub
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                .withDeadlineAfter(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public void createWallet(String userId, String walletId, int delaySeconds, boolean isBlocking, long commandNumber) {
        CreateWalletRequest request = CreateWalletRequest.newBuilder()
                                        .setUserId(userId)
                                        .setWalletId(walletId)
                                        .build();
        try {
            if (isBlocking) {
                NodeServiceGrpc.NodeServiceBlockingStub stubWithHeaders = blockingStubWithDelay(delaySeconds);
                stubWithHeaders.createWallet(request);
            } else {
                NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
                stubWithHeaders.createWallet(request, new ClientNodeObserver<CreateWalletResponse>(commandNumber, debug));
            }
        } catch (StatusRuntimeException e) {
            if (isUnavailable(e)) {
                if (debug) System.err.println("[DEBUG] Recreating gRPC channel for node " + host + ":" + port);
                createChannelAndStubs();
                if (isBlocking) {
                    NodeServiceGrpc.NodeServiceBlockingStub stubWithHeaders = blockingStubWithDelay(delaySeconds);
                    stubWithHeaders.createWallet(request);
                } else {
                    NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
                    stubWithHeaders.createWallet(request, new ClientNodeObserver<CreateWalletResponse>(commandNumber, debug));
                }
            } else {
                throw e;
            }
        }
    }

    public void deleteWallet(String userId, String walletId, int delaySeconds, boolean isBlocking, long commandNumber) {
        DeleteWalletRequest request = DeleteWalletRequest.newBuilder()
                                        .setUserId(userId)
                                        .setWalletId(walletId)
                                        .build();
        try {
            if (isBlocking) {
                NodeServiceGrpc.NodeServiceBlockingStub stubWithHeaders = blockingStubWithDelay(delaySeconds);
                stubWithHeaders.deleteWallet(request);
            } else {
                NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
                stubWithHeaders.deleteWallet(request, new ClientNodeObserver<DeleteWalletResponse>(commandNumber, debug));
            }
        } catch (StatusRuntimeException e) {
            if (isUnavailable(e)) {
                if (debug) System.err.println("[DEBUG] Recreating gRPC channel for node " + host + ":" + port);
                createChannelAndStubs();
                if (isBlocking) {
                    NodeServiceGrpc.NodeServiceBlockingStub stubWithHeaders = blockingStubWithDelay(delaySeconds);
                    stubWithHeaders.deleteWallet(request);
                } else {
                    NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
                    stubWithHeaders.deleteWallet(request, new ClientNodeObserver<DeleteWalletResponse>(commandNumber, debug));
                }
            } else {
                throw e;
            }
        }
    }

    public void transfer(String srcUserId, String srcWalletId, String dstWalletId, long value, int delaySeconds, boolean isBlocking, long commandNumber) {
        TransferRequest request = TransferRequest.newBuilder()
                                                .setSrcUserId(srcUserId)
                                                .setSrcWalletId(srcWalletId)
                                                .setDstWalletId(dstWalletId)
                                                .setValue(value)
                                                .build();
        try {
            if (isBlocking) {
                NodeServiceGrpc.NodeServiceBlockingStub stubWithHeaders = blockingStubWithDelay(delaySeconds);
                stubWithHeaders.transfer(request);
            } else {
                NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
                stubWithHeaders.transfer(request, new ClientNodeObserver<TransferResponse>(commandNumber, debug));
            }
        } catch (StatusRuntimeException e) {
            if (isUnavailable(e)) {
                if (debug) System.err.println("[DEBUG] Recreating gRPC channel for node " + host + ":" + port);
                createChannelAndStubs();
                if (isBlocking) {
                    NodeServiceGrpc.NodeServiceBlockingStub stubWithHeaders = blockingStubWithDelay(delaySeconds);
                    stubWithHeaders.transfer(request);
                } else {
                    NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
                    stubWithHeaders.transfer(request, new ClientNodeObserver<TransferResponse>(commandNumber, debug));
                }
            } else {
                throw e;
            }
        }
    }

    public long readBalance(String walletId, int delaySeconds, boolean isBlocking, long commandNumber) {
        ReadBalanceRequest request = ReadBalanceRequest.newBuilder()
                                                        .setWalletId(walletId)
                                                        .build();
        try {
            if (isBlocking) {
                NodeServiceGrpc.NodeServiceBlockingStub stubWithHeaders = blockingStubWithDelay(delaySeconds);
                ReadBalanceResponse response = stubWithHeaders.readBalance(request);
                return response.getBalance();
            } else {
                NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
                stubWithHeaders.readBalance(request, new ClientNodeObserver<pt.tecnico.blockchainist.contract.ReadBalanceResponse>(commandNumber, debug));
            }
        } catch (StatusRuntimeException e) {
            if (isUnavailable(e)) {
                if (debug) System.err.println("[DEBUG] Recreating gRPC channel for node " + host + ":" + port);
                createChannelAndStubs();
                if (isBlocking) {
                    NodeServiceGrpc.NodeServiceBlockingStub stubWithHeaders = blockingStubWithDelay(delaySeconds);
                    ReadBalanceResponse response = stubWithHeaders.readBalance(request);
                    return response.getBalance();
                } else {
                    NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
                    stubWithHeaders.readBalance(request, new ClientNodeObserver<pt.tecnico.blockchainist.contract.ReadBalanceResponse>(commandNumber, debug));
                }
            } else {
                throw e;
            }
        }
        return 0;
    }

    public List<Transaction> getBlockchainState() {
        
        GetBlockchainStateRequest request = GetBlockchainStateRequest.newBuilder().build();
        try {
            GetBlockchainStateResponse response = blockingStub
                    .withDeadlineAfter(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .getBlockchainState(request);
            return response.getTransactionsList();
        } catch (StatusRuntimeException e) {
            if (isUnavailable(e)) {
                if (debug) System.err.println("[DEBUG] Recreating gRPC channel for node " + host + ":" + port);
                createChannelAndStubs();
                GetBlockchainStateResponse response = blockingStub
                        .withDeadlineAfter(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .getBlockchainState(request);
                return response.getTransactionsList();
            } else {
                throw e;
            }
        }
    }

    public void shutdown() {
        channel.shutdown();
    }

}
