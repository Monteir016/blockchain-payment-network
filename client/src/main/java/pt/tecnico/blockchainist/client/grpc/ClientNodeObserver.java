package pt.tecnico.blockchainist.client.grpc;

import io.grpc.stub.StreamObserver;

public class ClientNodeObserver<R> implements StreamObserver<R> {
    private final long commandNumber;
    private final boolean debug;

    public ClientNodeObserver(long commandNumber, boolean debug) {
        this.commandNumber = commandNumber;
        this.debug = debug;
    }

    @Override
    public void onNext(R response) {
        if (debug) System.err.printf("[DEBUG] Received async response for cmd=%d\n", commandNumber);
        System.out.println("OK " + commandNumber);
    }

    @Override
    public void onError(Throwable t) {
        if (t instanceof io.grpc.StatusRuntimeException) {
            if (debug) System.err.printf("[DEBUG] Async gRPC error for cmd=%d: %s\n", commandNumber, ((io.grpc.StatusRuntimeException) t).getStatus());
            System.err.println(((io.grpc.StatusRuntimeException) t).getStatus().getDescription());
        } else {
            System.err.println("Node is unreachable");
            if (debug) {
                System.err.println("[DEBUG] Exception stack trace:");
                t.printStackTrace(System.err);
            }
        }
    }

    @Override
    public void onCompleted() {
        // No action needed
    }
}