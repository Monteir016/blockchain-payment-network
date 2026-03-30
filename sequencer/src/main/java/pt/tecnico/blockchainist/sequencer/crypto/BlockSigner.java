package pt.tecnico.blockchainist.sequencer.crypto;

import com.google.protobuf.ByteString;
import pt.tecnico.blockchainist.contract.Block;
import pt.tecnico.blockchainist.crypto.CryptoUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;

public class BlockSigner {

    private static final String SEQUENCER_ID = "Seq";
    private final PrivateKey privateKey;

    public BlockSigner() {
        this.privateKey = loadSequencerPrivateKey();
    }

    public Block sign(Block unsignedBlock) {
        try {
            Block canonical = unsignedBlock.toBuilder()
                    .setSignerId(SEQUENCER_ID)
                    .clearSignature()
                    .build();
            byte[] payload = canonical.toByteArray();
            byte[] signature = CryptoUtils.sign(payload, privateKey);
            return canonical.toBuilder()
                    .setSignature(ByteString.copyFrom(signature))
                    .build();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign closed block", e);
        }
    }

    private PrivateKey loadSequencerPrivateKey() {
        try (InputStream in = BlockSigner.class.getResourceAsStream("/Seq.priv")) {
            if (in == null) {
                throw new IllegalStateException(
                        "Missing sequencer private key: add /Seq.priv under sequencer/src/main/resources (see genkeys.sh)");
            }
            return CryptoUtils.readPrivateKey(in);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to load sequencer private key", e);
        }
    }
}
