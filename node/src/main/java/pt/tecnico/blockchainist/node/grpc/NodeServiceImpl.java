package pt.tecnico.blockchainist.node.grpc;

import java.util.List;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.tecnico.blockchainist.node.domain.NodeState;
import pt.tecnico.blockchainist.contract.*;


public class NodeServiceImpl extends NodeServiceGrpc.NodeServiceImplBase {
    
    private final NodeState nodeState;

    public NodeServiceImpl(NodeState nodeState) {
        this.nodeState = nodeState;
    }

    @Override
    public void createWallet(CreateWalletRequest request, StreamObserver<CreateWalletResponse> responseObserver) {
        
        try {
            nodeState.createWallet(request.getUserId(), request.getWalletId());
            CreateWalletResponse response = CreateWalletResponse.newBuilder().build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void deleteWallet(DeleteWalletRequest request, StreamObserver<DeleteWalletResponse> responseObserver) {
        try {
            nodeState.deleteWallet(request.getUserId(), request.getWalletId());
            DeleteWalletResponse response = DeleteWalletResponse.newBuilder().build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void transfer(TransferRequest request, StreamObserver<TransferResponse> responseObserver) {
        try {
            nodeState.transfer(request.getSrcUserId(), request.getSrcWalletId(), request.getDstWalletId(), request.getValue());
            TransferResponse response = TransferResponse.newBuilder().build();
            responseObserver.onNext(response);
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
