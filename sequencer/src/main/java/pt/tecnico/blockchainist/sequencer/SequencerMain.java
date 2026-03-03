package pt.tecnico.blockchainist.sequencer;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import pt.tecnico.blockchainist.sequencer.domain.SequencerState;
import pt.tecnico.blockchainist.sequencer.grpc.SequencerServiceImpl;

public class SequencerMain {
    public static void main(String[] args) {

        System.out.println(SequencerMain.class.getSimpleName());

        // Verificar argumentos
        if (args.length < 1) {
            System.err.println("No argument");
            System.err.printf("Usage: java %s <port>%n", SequencerMain.class.getName());
            return;
        }

        final int port = Integer.parseInt(args[0]);

        // Criar estado e serviço
        final SequencerState state = new SequencerState();
        final BindableService impl = new SequencerServiceImpl(state);

        // Arrancar servidor gRPC
        try {
            final Server server = ServerBuilder.forPort(port).addService(impl).build();
            server.start();
            System.out.println("Sequencer started, listening on port " + port);

            // Shutdown hook para fecho limpo
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down sequencer...");
                server.shutdown();
            }));

            server.awaitTermination();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}