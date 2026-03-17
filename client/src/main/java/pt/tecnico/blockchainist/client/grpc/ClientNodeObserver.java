package pt.tecnico.blockchainist.client.grpc;

import io.grpc.stub.StreamObserver;

public class ClientNodeObserver<R> implements StreamObserver<R> {
    private final long commandNumber;

    public ClientNodeObserver(long commandNumber) {
        this.commandNumber = commandNumber;
    }

    @Override
    public void onNext(R response) {
        System.out.println("OK " + commandNumber);
        // Print extra info for ReadBalanceResponse
        if (response instanceof pt.tecnico.blockchainist.contract.ReadBalanceResponse) {
            long balance = ((pt.tecnico.blockchainist.contract.ReadBalanceResponse) response).getBalance();
            System.out.println(balance);
        }
        // For other responses, nothing extra is printed (could be extended if needed)
    }

    @Override
    public void onError(Throwable t) {
        System.err.println("[Command " + commandNumber + " Error]: " + t.getMessage());
    }

    @Override
    public void onCompleted() {
        // No action needed
    }
}