package pt.tecnico.blockchainist.node.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/** Extracts per-request delay metadata and stores it in gRPC Context. */
public class DelayMetadataServerInterceptor implements ServerInterceptor {

    public static final String DELAY_HEADER_NAME = "delay-seconds";
    public static final Metadata.Key<String> DELAY_HEADER_KEY =
            Metadata.Key.of(DELAY_HEADER_NAME, Metadata.ASCII_STRING_MARSHALLER);
    public static final Context.Key<Integer> DELAY_SECONDS_CTX_KEY =
            Context.key(DELAY_HEADER_NAME);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        int delaySeconds = 0;
        String delayValue = headers.get(DELAY_HEADER_KEY);
        if (delayValue != null) {
            try {
                delaySeconds = Integer.parseInt(delayValue);
                if (delaySeconds < 0) {
                    delaySeconds = 0;
                }
            } catch (NumberFormatException ignored) {
                delaySeconds = 0;
            }
        }

        Context contextWithDelay = Context.current().withValue(DELAY_SECONDS_CTX_KEY, delaySeconds);
        return Contexts.interceptCall(contextWithDelay, call, headers, next);
    }
}
