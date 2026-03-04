package pt.tecnico.blockchainist.node.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import pt.tecnico.blockchainist.contract.*;

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
     * Envia uma transação ao sequenciador para ser ordenada.
     * @return O número de sequência atribuído pelo sequenciador
     */
    public int broadcast(Transaction transaction) {
        BroadcastRequest request = BroadcastRequest.newBuilder()
                .setTransaction(transaction)
                .build();
        BroadcastResponse response = stub.broadcast(request);
        return response.getSequenceNumber();
    }

    /**
     * Pede ao sequenciador a transação com o número de sequência dado.
     * @return A transação entregue pelo sequenciador
     */
    public Transaction deliverTransaction(int sequenceNumber) {
        DeliverTransactionRequest request = DeliverTransactionRequest.newBuilder()
                .setSequenceNumber(sequenceNumber)
                .build();
        DeliverTransactionResponse response = stub.deliverTransaction(request);
        return response.getTransaction();
    }

    public void shutdown() {
        channel.shutdown();
    }
}   