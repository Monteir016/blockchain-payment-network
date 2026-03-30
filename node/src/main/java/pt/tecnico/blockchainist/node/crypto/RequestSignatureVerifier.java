package pt.tecnico.blockchainist.node.crypto;

import com.google.protobuf.ByteString;
import pt.tecnico.blockchainist.contract.CreateWalletRequest;
import pt.tecnico.blockchainist.contract.DeleteWalletRequest;
import pt.tecnico.blockchainist.contract.TransferRequest;
import pt.tecnico.blockchainist.crypto.CryptoUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.concurrent.ConcurrentHashMap;

public class RequestSignatureVerifier {

    private final ConcurrentHashMap<String, PublicKey> publicKeysByUserId = new ConcurrentHashMap<>();

    public void verifyOrThrow(CreateWalletRequest request) {
        verifyOrThrow(
                request.getUserId(),
                canonicalSignPayload(request),
                request.getSignature(),
                "CreateWalletRequest");
    }

    public void verifyOrThrow(DeleteWalletRequest request) {
        verifyOrThrow(
                request.getUserId(),
                canonicalSignPayload(request),
                request.getSignature(),
                "DeleteWalletRequest");
    }

    public void verifyOrThrow(TransferRequest request) {
        verifyOrThrow(
                request.getSrcUserId(),
                canonicalSignPayload(request),
                request.getSignature(),
                "TransferRequest");
    }

    private void verifyOrThrow(String userId, byte[] payload, ByteString signature, String requestType) {
        if (signature == null || signature.isEmpty()) {
            throw new SecurityException("Missing signature in " + requestType);
        }
        if (userId == null || userId.isBlank()) {
            throw new SecurityException("Missing signer userId in " + requestType);
        }

        PublicKey publicKey = publicKeyForUser(userId);
        final boolean isValid;
        try {
            isValid = CryptoUtils.verify(payload, signature.toByteArray(), publicKey);
        } catch (GeneralSecurityException e) {
            throw new SecurityException("Could not verify signature in " + requestType, e);
        }

        if (!isValid) {
            throw new SecurityException("Invalid signature for user '" + userId + "' in " + requestType);
        }
    }

    private PublicKey publicKeyForUser(String userId) {
        return publicKeysByUserId.computeIfAbsent(userId, this::loadPublicKeyForUser);
    }

    private PublicKey loadPublicKeyForUser(String userId) {
        String resourceName = "/" + userId + ".pub";
        try (InputStream in = RequestSignatureVerifier.class.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new SecurityException(
                        "Missing public key for user '" + userId + "': add " + resourceName
                                + " under node/src/main/resources (see genkeys.sh)");
            }
            return CryptoUtils.readPublicKey(in);
        } catch (IOException | GeneralSecurityException e) {
            throw new SecurityException("Failed to load public key for user '" + userId + "'", e);
        }
    }

    private static byte[] canonicalSignPayload(CreateWalletRequest unsigned) {
        return unsigned.toBuilder().clearSignature().build().toByteArray();
    }

    private static byte[] canonicalSignPayload(DeleteWalletRequest unsigned) {
        return unsigned.toBuilder().clearSignature().build().toByteArray();
    }

    private static byte[] canonicalSignPayload(TransferRequest unsigned) {
        return unsigned.toBuilder().clearSignature().build().toByteArray();
    }
}
