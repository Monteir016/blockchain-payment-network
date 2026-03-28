package pt.tecnico.blockchainist.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public final class CryptoUtils {

	public static final String RSA_ALGORITHM = "RSA";
	public static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

	private CryptoUtils() {
	}

	public static PrivateKey readPrivateKey(Path path) throws IOException, GeneralSecurityException {
		byte[] encoded = Files.readAllBytes(path);
		return decodePrivateKey(encoded);
	}

	public static PublicKey readPublicKey(Path path) throws IOException, GeneralSecurityException {
		byte[] encoded = Files.readAllBytes(path);
		return decodePublicKey(encoded);
	}

	public static PrivateKey readPrivateKey(InputStream in) throws IOException, GeneralSecurityException {
		byte[] encoded = in.readAllBytes();
		return decodePrivateKey(encoded);
	}

	public static PublicKey readPublicKey(InputStream in) throws IOException, GeneralSecurityException {
		byte[] encoded = in.readAllBytes();
		return decodePublicKey(encoded);
	}

	private static PrivateKey decodePrivateKey(byte[] encoded) throws GeneralSecurityException {
		if (encoded.length == 0) {
			throw new IllegalArgumentException("empty private key encoding");
		}
		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(encoded);
		KeyFactory factory = KeyFactory.getInstance(RSA_ALGORITHM);
		return factory.generatePrivate(spec);
	}

	private static PublicKey decodePublicKey(byte[] encoded) throws GeneralSecurityException {
		if (encoded.length == 0) {
			throw new IllegalArgumentException("empty public key encoding");
		}
		X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);
		KeyFactory factory = KeyFactory.getInstance(RSA_ALGORITHM);
		return factory.generatePublic(spec);
	}
}
