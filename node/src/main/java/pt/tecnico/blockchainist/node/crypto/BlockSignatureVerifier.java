package pt.tecnico.blockchainist.node.crypto;

import com.google.protobuf.ByteString;
import pt.tecnico.blockchainist.contract.Block;
import pt.tecnico.blockchainist.crypto.CryptoUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

public class BlockSignatureVerifier {

    private static final String EXPECTED_SIGNER_ID = "Seq";
    private final PublicKey sequencerPublicKey;

    public BlockSignatureVerifier() {
        this.sequencerPublicKey = loadSequencerPublicKey();
    }

    public void verifyOrThrow(Block block) {
        if (block.getSignature() == null || block.getSignature().isEmpty()) {
            throw new SecurityException("Block " + block.getBlockId() + " has missing signature");
        }
        if (!EXPECTED_SIGNER_ID.equals(block.getSignerId())) {
            throw new SecurityException(
                    "Block " + block.getBlockId() + " has invalid signer: '" + block.getSignerId() + "'");
        }

        Block canonical = block.toBuilder().clearSignature().build();
        byte[] payload = canonical.toByteArray();
        ByteString signature = block.getSignature();

        final boolean valid;
        try {
            valid = CryptoUtils.verify(payload, signature.toByteArray(), sequencerPublicKey);
        } catch (GeneralSecurityException e) {
            throw new SecurityException("Could not verify signature for block " + block.getBlockId(), e);
        }
        if (!valid) {
            throw new SecurityException("Invalid signature for block " + block.getBlockId());
        }
    }

    private PublicKey loadSequencerPublicKey() {
        try (InputStream in = BlockSignatureVerifier.class.getResourceAsStream("/seq/Seq.pub")) {
            if (in == null) {
                throw new SecurityException(
                        "Missing sequencer public key: add /seq/Seq.pub under node/src/main/resources (see genkeys.sh)");
            }
            return CryptoUtils.readPublicKey(in);
        } catch (IOException | GeneralSecurityException e) {
            throw new SecurityException("Failed to load sequencer public key", e);
        }
    }
}
