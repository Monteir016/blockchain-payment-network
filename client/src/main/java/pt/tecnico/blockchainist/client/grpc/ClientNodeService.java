package pt.tecnico.blockchainist.client.grpc;

import pt.tecnico.blockchainist.contract.*;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;


import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;




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

    /**
     * Executes the given action. If a UNAVAILABLE error is caught, recreates the channel and retries once.
     * If it fails again, the exception is propagated.
     */
    public <T> T withReconnectOnUnavailable(Supplier<T> action) {
        try {
            return action.get();
        } catch (StatusRuntimeException e) {
            if (isUnavailable(e)) {
                if (debug) System.err.println("[DEBUG] Recreating gRPC channel for node " + host + ":" + port);
                createChannelAndStubs();
                return action.get();
            }
            throw e;
        }
    }

    /**
     * Void version for methods that do not return a value.
     */
    public void withReconnectOnUnavailableVoid(Runnable action) {
        try {
            action.run();
        } catch (StatusRuntimeException e) {
            if (isUnavailable(e)) {
                if (debug) System.err.println("[DEBUG] Recreating gRPC channel for node " + host + ":" + port);
                createChannelAndStubs();
                action.run();
            } else {
                throw e;
            }
        }
    }

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

    public void createWallet(String userId, String walletId, int delaySeconds, boolean isBlocking, long commandNumber, String requestId) {
        CreateWalletRequest request = CreateWalletRequest.newBuilder()
                .setUserId(userId)
                .setWalletId(walletId)
                .setRequestId(requestId)
                .build();
        if (isBlocking) {
            withReconnectOnUnavailableVoid(() -> {
                NodeServiceGrpc.NodeServiceBlockingStub stubWithHeaders = blockingStubWithDelay(delaySeconds);
                stubWithHeaders.createWallet(request);
            });
        } else {
            withReconnectOnUnavailableVoid(() -> {
                NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
                stubWithHeaders.createWallet(request, new ClientNodeObserver<CreateWalletResponse>(commandNumber, debug));
            });
        }
    }

    public void createWalletAsync(
            String userId,
            String walletId,
            int delaySeconds,
            long commandNumber,
            String requestId,
            StreamObserver<CreateWalletResponse> observer) {
        CreateWalletRequest request = CreateWalletRequest.newBuilder()
                .setUserId(userId)
                .setWalletId(walletId)
                .setRequestId(requestId)
                .build();
        withReconnectOnUnavailableVoid(() -> {
            NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
            stubWithHeaders.createWallet(request, observer);
        });
    }

    public void deleteWallet(String userId, String walletId, int delaySeconds, boolean isBlocking, long commandNumber, String requestId) {
        DeleteWalletRequest request = DeleteWalletRequest.newBuilder()
                .setUserId(userId)
                .setWalletId(walletId)
                .setRequestId(requestId)
                .build();
        if (isBlocking) {
            withReconnectOnUnavailableVoid(() -> {
                NodeServiceGrpc.NodeServiceBlockingStub stubWithHeaders = blockingStubWithDelay(delaySeconds);
                stubWithHeaders.deleteWallet(request);
            });
        } else {
            withReconnectOnUnavailableVoid(() -> {
                NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
                stubWithHeaders.deleteWallet(request, new ClientNodeObserver<DeleteWalletResponse>(commandNumber, debug));
            });
        }
    }

    public void deleteWalletAsync(
            String userId,
            String walletId,
            int delaySeconds,
            long commandNumber,
            String requestId,
            StreamObserver<DeleteWalletResponse> observer) {
        DeleteWalletRequest request = DeleteWalletRequest.newBuilder()
                .setUserId(userId)
                .setWalletId(walletId)
                .setRequestId(requestId)
                .build();
        withReconnectOnUnavailableVoid(() -> {
            NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
            stubWithHeaders.deleteWallet(request, observer);
        });
    }

    public void transfer(
            String srcUserId,
            String srcWalletId,
            String dstWalletId,
            long value,
            int delaySeconds,
            boolean isBlocking,
            long commandNumber,
            String requestId) {
        TransferRequest request = TransferRequest.newBuilder()
                .setSrcUserId(srcUserId)
                .setSrcWalletId(srcWalletId)
                .setDstWalletId(dstWalletId)
                .setValue(value)
                .setRequestId(requestId)
                .build();
        if (isBlocking) {
            withReconnectOnUnavailableVoid(() -> {
                NodeServiceGrpc.NodeServiceBlockingStub stubWithHeaders = blockingStubWithDelay(delaySeconds);
                stubWithHeaders.transfer(request);
            });
        } else {
            withReconnectOnUnavailableVoid(() -> {
                NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
                stubWithHeaders.transfer(request, new ClientNodeObserver<TransferResponse>(commandNumber, debug));
            });
        }
    }

    public void transferAsync(
            String srcUserId,
            String srcWalletId,
            String dstWalletId,
            long value,
            int delaySeconds,
            long commandNumber,
            String requestId,
            StreamObserver<TransferResponse> observer) {
        TransferRequest request = TransferRequest.newBuilder()
                .setSrcUserId(srcUserId)
                .setSrcWalletId(srcWalletId)
                .setDstWalletId(dstWalletId)
                .setValue(value)
                .setRequestId(requestId)
                .build();
        withReconnectOnUnavailableVoid(() -> {
            NodeServiceGrpc.NodeServiceStub stubWithHeaders = asyncStubWithDelay(delaySeconds);
            stubWithHeaders.transfer(request, observer);
        });
    }

    /**
     * Reads the balance of a wallet. This operation is always blocking, as it is an immediate debug read and does not enter the sequencer as a transaction.
     */
    public long readBalance(String walletId, int delaySeconds) {
        ReadBalanceRequest request = ReadBalanceRequest.newBuilder()
                .setWalletId(walletId)
                .build();
        return withReconnectOnUnavailable(() -> {
            NodeServiceGrpc.NodeServiceBlockingStub stubWithHeaders = blockingStubWithDelay(delaySeconds);
            ReadBalanceResponse response = stubWithHeaders.readBalance(request);
            return response.getBalance();
        });
    }

    public List<Transaction> getBlockchainState() {
        GetBlockchainStateRequest request = GetBlockchainStateRequest.newBuilder().build();
        return withReconnectOnUnavailable(() -> {
            GetBlockchainStateResponse response = blockingStub
                    .withDeadlineAfter(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .getBlockchainState(request);
            return response.getTransactionsList();
        });
    }

    public void shutdown() {
        channel.shutdown();
    }

}
