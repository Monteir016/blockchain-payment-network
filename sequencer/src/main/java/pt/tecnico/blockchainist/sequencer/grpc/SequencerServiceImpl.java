package pt.tecnico.blockchainist.sequencer.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import pt.tecnico.blockchainist.contract.*;
import pt.tecnico.blockchainist.sequencer.domain.SequencerState;


/** gRPC service implementation for the simplified sequencer (A.2). */
public class SequencerServiceImpl extends SequencerServiceGrpc.SequencerServiceImplBase {

    private final SequencerState state;

    public SequencerServiceImpl(SequencerState state) {
        this.state = state;
    }

    @Override
    public void broadcast(BroadcastRequest request, StreamObserver<BroadcastResponse> responseObserver) {
        try {
            Transaction transaction = request.getTransaction();
            int sequenceNumber = state.addTransaction(transaction);

            BroadcastResponse response = BroadcastResponse.newBuilder()
                    .setSequenceNumber(sequenceNumber)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(
                Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException()
            );
        }
    }

    @Override
    public void deliverTransaction(DeliverTransactionRequest request,
                                    StreamObserver<DeliverTransactionResponse> responseObserver) {
        try {
            int sequenceNumber = request.getSequenceNumber();
            Transaction transaction = state.getTransaction(sequenceNumber);

            DeliverTransactionResponse response = DeliverTransactionResponse.newBuilder()
                    .setTransaction(transaction)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException()
            );
        }
    }

    public void deliverBlock(DeliverBlockRequest request,
                                StreamObserver<DeliverBlockResponse> responseObserver) {
        try {
            int blockId = request.getBlockId();
            Block block = state.getBlock(blockId);

            DeliverBlockResponse response = DeliverBlockResponse.newBuilder()
                    .setBlock(block)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException()
            );
        }
    }
}