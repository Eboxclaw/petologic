import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
public class EnvVerify {
 public static void main(String[] a) throws Exception {
  String pub=Files.readString(Path.of(a[0])).replaceAll("-----[^-]+-----","").replaceAll("\\\\s","");
  PublicKey key=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pub)));
  String env=Files.readString(Path.of(a[1]));
  String payloadB64=env.replaceAll(".*\"payload\":\"([^\"]+)\".*","$1");
  String sigB64=env.replaceAll(".*\"signature\":\"([^\"]+)\".*","$1");
  byte[] payload=Base64.getDecoder().decode(payloadB64);byte[] sig=Base64.getDecoder().decode(sigB64);
  Signature v=Signature.getInstance("Ed25519");v.initVerify(key);v.update(payload);
  byte[] orig=Files.readAllBytes(Path.of(a[2]));
  System.out.println("payloadRoundtrip="+java.util.Arrays.equals(payload,orig)+" sigLen="+sig.length+" envelopeVerify="+v.verify(sig));
 }
}
