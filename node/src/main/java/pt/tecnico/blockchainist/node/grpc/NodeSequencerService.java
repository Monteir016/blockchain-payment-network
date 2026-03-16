package pt.tecnico.blockchainist.node.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
     * Requests block by index from the sequencer
     * @param blockId block index (0-based)
     * @return the response with block if available, or empty if block not yet closed
     */
    public java.util.Optional<DeliverBlockResponse> tryDeliverBlock(int blockId) {
        DeliverBlockRequest request = DeliverBlockRequest.newBuilder()
                .setBlockId(blockId)
                .build();
        try {
            DeliverBlockResponse response = stub.deliverBlock(request);
            return java.util.Optional.of(response);
        } catch (StatusRuntimeException e) {
            // Block not yet available
            return java.util.Optional.empty();
        }
    }

    public void shutdown() {
        channel.shutdown();
    }
}   