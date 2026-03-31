package pt.tecnico.blockchainist.node.grpc;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.tecnico.blockchainist.node.domain.ApplicationPipeline;
import pt.tecnico.blockchainist.node.domain.NodeState;
import pt.tecnico.blockchainist.node.crypto.RequestSignatureVerifier;
import pt.tecnico.blockchainist.contract.*;


/**
 * gRPC service implementation for the NodeService
 * Write operations broadcast to the sequencer and wait for the application
 * pipeline to apply the transaction in total order before responding.
 */
public class NodeServiceImpl extends NodeServiceGrpc.NodeServiceImplBase {

    private static final long PENDING_TIMEOUT_SEC = 60;

    private final NodeState nodeState;
    private final String organization;
    private final NodeSequencerService sequencerService;
    private final ApplicationPipeline applicationPipeline;
    private final RequestSignatureVerifier signatureVerifier;

    public NodeServiceImpl(NodeState nodeState, String organization, NodeSequencerService sequencerService,
                           ApplicationPipeline applicationPipeline) {
        this.nodeState = nodeState;
        this.organization = organization;
        this.sequencerService = sequencerService;
        this.applicationPipeline = applicationPipeline;
        this.signatureVerifier = new RequestSignatureVerifier();
    }

    private void applyRequestDelayIfAny() throws InterruptedException {
        Integer delaySeconds = DelayMetadataServerInterceptor.DELAY_SECONDS_CTX_KEY.get();
        if (delaySeconds != null && delaySeconds > 0) {
            TimeUnit.SECONDS.sleep(delaySeconds);
        }
    }

    @Override
    public void createWallet(CreateWalletRequest request, StreamObserver<CreateWalletResponse> responseObserver) {
        try {
            applyRequestDelayIfAny();
            signatureVerifier.verifyOrThrow(request);
            nodeState.isUserFromOrganization(request.getUserId(), organization);

            Transaction tx = Transaction.newBuilder()
                    .setCreateWallet(request)
                    .build();
            CompletableFuture<Void> done = new CompletableFuture<>();
            applicationPipeline.registerPending(tx, done);
            
            sequencerService.broadcast(tx);
            done.get(PENDING_TIMEOUT_SEC, TimeUnit.SECONDS);

            responseObserver.onNext(CreateWalletResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (TimeoutException e) {
            responseObserver.onError(Status.DEADLINE_EXCEEDED.withDescription("Transaction not applied in time").asRuntimeException());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(cause.getMessage()).asRuntimeException());
            } else {
                responseObserver.onError(Status.INTERNAL.withDescription(cause != null ? cause.getMessage() : e.getMessage()).asRuntimeException());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(Status.CANCELLED.withDescription("Request interrupted while waiting delay").asRuntimeException());
        } catch (SecurityException e) {
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (Throwable e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void deleteWallet(DeleteWalletRequest request, StreamObserver<DeleteWalletResponse> responseObserver) {
        try {
            applyRequestDelayIfAny();
            signatureVerifier.verifyOrThrow(request);
            nodeState.isUserFromOrganization(request.getUserId(), organization);

            Transaction tx = Transaction.newBuilder()
                    .setDeleteWallet(request)
                    .build();

            CompletableFuture<Void> done = new CompletableFuture<>();
            applicationPipeline.registerPending(tx, done);

            sequencerService.broadcast(tx);
            done.get(PENDING_TIMEOUT_SEC, TimeUnit.SECONDS);

            responseObserver.onNext(DeleteWalletResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (TimeoutException e) {
            responseObserver.onError(Status.DEADLINE_EXCEEDED.withDescription("Transaction not applied in time").asRuntimeException());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(cause.getMessage()).asRuntimeException());
            } else {
                responseObserver.onError(Status.INTERNAL.withDescription(cause != null ? cause.getMessage() : e.getMessage()).asRuntimeException());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(Status.CANCELLED.withDescription("Request interrupted while waiting delay").asRuntimeException());
        } catch (SecurityException e) {
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (Throwable e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void transfer(TransferRequest request, StreamObserver<TransferResponse> responseObserver) {
        try {
            applyRequestDelayIfAny();
            signatureVerifier.verifyOrThrow(request);
            nodeState.isUserFromOrganization(request.getSrcUserId(), organization);

            Transaction tx = Transaction.newBuilder()
                    .setTransfer(request)
                    .build();

            boolean sameOrg = nodeState.isWalletInOrganization(request.getDstWalletId(), organization);

            if (sameOrg) {
                // Same org: apply the transaction immediately and then broadcast (early response)
                nodeState.executeTransaction(tx);
                sequencerService.broadcast(tx);
                responseObserver.onNext(TransferResponse.newBuilder().build());
                responseObserver.onCompleted();
            } else {
                // Different org: normal broadcast -> sequencer -> apply flow, avoiding inconsistencies
                CompletableFuture<Void> done = new CompletableFuture<>();
                applicationPipeline.registerPending(tx, done);
                
                sequencerService.broadcast(tx);
                done.get(PENDING_TIMEOUT_SEC, TimeUnit.SECONDS);

                responseObserver.onNext(TransferResponse.newBuilder().build());
                responseObserver.onCompleted();
            }
            
        } catch (TimeoutException e) {
            responseObserver.onError(Status.DEADLINE_EXCEEDED.withDescription("Transaction not applied in time").asRuntimeException());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(cause.getMessage()).asRuntimeException());
            } else {
                responseObserver.onError(Status.INTERNAL.withDescription(cause != null ? cause.getMessage() : e.getMessage()).asRuntimeException());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(Status.CANCELLED.withDescription("Request interrupted while waiting delay").asRuntimeException());
        } catch (SecurityException e) {
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (Throwable e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void readBalance(ReadBalanceRequest request, StreamObserver<ReadBalanceResponse> responseObserver) {
        try {
            applyRequestDelayIfAny();
            long balance = nodeState.readBalance(request.getWalletId());
            ReadBalanceResponse response = ReadBalanceResponse.newBuilder().setBalance(balance).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(Status.CANCELLED.withDescription("Request interrupted while waiting delay").asRuntimeException());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getBlockchainState(GetBlockchainStateRequest request, StreamObserver<GetBlockchainStateResponse> responseObserver) {
        try {
            
            List<Transaction> transactions = nodeState.getBlockchainState();
            GetBlockchainStateResponse.Builder responseBuilder = GetBlockchainStateResponse.newBuilder();
            for (Transaction tx : transactions) {
                responseBuilder.addTransactions(tx);
            }
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    
}
