package pt.tecnico.blockchainist.client.grpc;

import org.w3c.dom.Node;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;


public class ClientNodeService {
    public ClientNodeService(String host, int port, String organization) {
        // TODO: create channel/stub

        /*
        // Input validation
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }

        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port number must be between 0 and 65535");
        }

        if (organization == null || organization.isEmpty()) {
            throw new IllegalArgumentException("Organization cannot be null or empty");
        }
        
        // Channel creation
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext() 
                .build();

        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        */

    }
}
