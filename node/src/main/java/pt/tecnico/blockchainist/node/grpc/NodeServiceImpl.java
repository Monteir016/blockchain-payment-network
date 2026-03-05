package pt.tecnico.blockchainist.node.grpc;

import java.util.List;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.tecnico.blockchainist.node.domain.NodeState;
import pt.tecnico.blockchainist.contract.*;


/**
 * gRPC service implementation for the NodeService.
 * Acts as adapter between protobuf messages and the domain (NodeState).
 * Write operations are forwarded to the sequencer before local execution.
 */
public class NodeServiceImpl extends NodeServiceGrpc.NodeServiceImplBase {
    
    private final NodeState nodeState;
    private final NodeSequencerService sequencerService;

    public NodeServiceImpl(NodeState nodeState, NodeSequencerService sequencerService) {
        this.nodeState = nodeState;
        this.sequencerService = sequencerService;
    }

    @Override
    public void createWallet(CreateWalletRequest request, StreamObserver<CreateWalletResponse> responseObserver) {
        
        try {
            Transaction tx = Transaction.newBuilder()
                            .setCreateWallet(request)
                            .build();
            int sequenceNumber = sequencerService.broadcast(tx);
            Transaction deliveredTx = sequencerService.deliverTransaction(sequenceNumber);
            nodeState.executeTransaction(deliveredTx);

            responseObserver.onNext(CreateWalletResponse.newBuilder().build());
            responseObserver.onCompleted();            
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void deleteWallet(DeleteWalletRequest request, StreamObserver<DeleteWalletResponse> responseObserver) {
        try {
            Transaction tx = Transaction.newBuilder()
                    .setDeleteWallet(request)
                    .build();

            int seqNum = sequencerService.broadcast(tx);
            Transaction deliveredTx = sequencerService.deliverTransaction(seqNum);
            nodeState.executeTransaction(deliveredTx);

            responseObserver.onNext(DeleteWalletResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void transfer(TransferRequest request, StreamObserver<TransferResponse> responseObserver) {
        try {
            Transaction tx = Transaction.newBuilder()
                    .setTransfer(request)
                    .build();

            int seqNum = sequencerService.broadcast(tx);
            Transaction deliveredTx = sequencerService.deliverTransaction(seqNum);
            nodeState.executeTransaction(deliveredTx);

            responseObserver.onNext(TransferResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void readBalance(ReadBalanceRequest request, StreamObserver<ReadBalanceResponse> responseObserver) {
        try {
            long balance = nodeState.readBalance(request.getWalletId());
            ReadBalanceResponse response = ReadBalanceResponse.newBuilder().setBalance(balance).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
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
