package pt.tecnico.blockchainist.node.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import pt.tecnico.blockchainist.contract.*;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** gRPC client for communicating with the sequencer service. */
public class NodeSequencerService {
    /**
     * For bootstrap/safety checks: keep the call short so we can detect
     * "block not yet closed" without blocking the whole node startup.
     */
    private static final long TRY_DELIVER_BLOCK_DEADLINE_MS = 100;

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
    public Optional<DeliverBlockResponse> tryDeliverBlock(int blockId) {
        DeliverBlockRequest request = DeliverBlockRequest.newBuilder()
                .setBlockId(blockId)
                .build();
        try {
            DeliverBlockResponse response = stub
                    .withDeadlineAfter(TRY_DELIVER_BLOCK_DEADLINE_MS, TimeUnit.MILLISECONDS)
                    .deliverBlock(request);
            return response.getAvailable() ? Optional.of(response) : Optional.empty();
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.DEADLINE_EXCEEDED) {
                // deliverBlock is blocking on the server side; treat deadline as "not ready yet".
                return Optional.empty();
            }
            if (code == Status.Code.NOT_FOUND
                    || code == Status.Code.OUT_OF_RANGE
                    || code == Status.Code.INVALID_ARGUMENT) {
                // Defensive fallback for other "not ready" signals.
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * Blocks until the requested block is available in the sequencer.
     * Used by the node's application pipeline to avoid tight polling.
     */
    public Block deliverBlockBlocking(int blockId) {
        DeliverBlockRequest request = DeliverBlockRequest.newBuilder()
                .setBlockId(blockId)
                .build();
        DeliverBlockResponse response = stub.deliverBlock(request);
        if (!response.getAvailable()) {
            throw new IllegalStateException("Expected block " + blockId + " to be available");
        }
        return response.getBlock();
    }

    public void shutdown() {
        channel.shutdown();
    }
}   