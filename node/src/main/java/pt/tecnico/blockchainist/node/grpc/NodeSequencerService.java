package pt.tecnico.blockchainist.node.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import pt.tecnico.blockchainist.contract.*;

/** gRPC client for communicating with the sequencer service. */
public class NodeSequencerService {

    private final ManagedChannel channel;
    private final SequencerServiceGrpc.SequencerServiceBlockingStub stub;

    public NodeSequencerService(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.stub = SequencerServiceGrpc.newBlockingStub(this.channel);
    }

    /**
     * Sends a transaction to the sequencer for total ordering.
     * @return the sequence number assigned by the sequencer
     */
    public int broadcast(Transaction transaction) {
        BroadcastRequest request = BroadcastRequest.newBuilder()
                .setTransaction(transaction)
                .build();
        BroadcastResponse response = stub.broadcast(request);
        return response.getSequenceNumber();
    }

    /**
     * Requests the transaction with the given sequence number from the sequencer.
     * @return the delivered transaction
     */
    public Transaction deliverTransaction(int sequenceNumber) {
        DeliverTransactionRequest request = DeliverTransactionRequest.newBuilder()
                .setSequenceNumber(sequenceNumber)
                .build();
        DeliverTransactionResponse response = stub.deliverTransaction(request);
        return response.getTransaction();
    }

    /**
     * Requests block by index from the sequencer.
     *
     * @param blockId block index (0-based)
     * @return the response if the block is available, or empty if the block has
     *         not been closed yet (sequencer returns NOT_FOUND / available=false)
     * @throws StatusRuntimeException if the sequencer is unreachable (UNAVAILABLE)
     *         or another unexpected gRPC error occurs — callers that need to
     *         distinguish "not ready" from "sequencer down" should inspect the
     *         thrown exception's status code
     */
    public java.util.Optional<DeliverBlockResponse> tryDeliverBlock(int blockId) {
        DeliverBlockRequest request = DeliverBlockRequest.newBuilder()
                .setBlockId(blockId)
                .build();
        try {
            DeliverBlockResponse response = stub.deliverBlock(request);
            // Some sequencer implementations signal unavailability via the
            // available flag rather than by throwing.
            if (!response.getAvailable()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(response);
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.NOT_FOUND
                    || code == Status.Code.OUT_OF_RANGE
                    || code == Status.Code.INVALID_ARGUMENT) {
                // Block not yet closed — expected stop condition during polling/bootstrap
                return java.util.Optional.empty();
            }
            // Any other error (UNAVAILABLE, INTERNAL, …) is re-thrown so callers
            // can decide whether to retry or abort.
            throw e;
        }
    }

    public void shutdown() {
        channel.shutdown();
    }
}   