// Local integration fixture. Uses synthetic bytes, never the user's JKS.
// A Java source-file test for the exact one-shot Termux decrypt binary.
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

class VerifyReleaseTransport {
    private static final String PROBE="{\"request_id\":\"synthetic-only\",\"message\":\"LOCAL_ONLY_TEST\"}";
    private static OAEPParameterSpec oaep(){
        return new OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT);
    }
    private static class Result {
        int status;
        byte[] out;
        Result(int status,byte[] out){this.status=status;this.out=out;}
    }
    private static Result call(Path source,String mode,String id,byte[] stdin) throws Exception {
        Process process=new ProcessBuilder("java",source.toString(),mode,id)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try(OutputStream writer=process.getOutputStream()){writer.write(stdin);}
        byte[] out=process.getInputStream().readNBytes(250000);
        if(!process.waitFor(120,java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();throw new IllegalStateException("timeout");
        }
        return new Result(process.exitValue(),out);
    }
    private static String seal(String id,String publicKey,String plain) throws Exception {
        RSAPublicKey key=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(
            new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
        if(key.getModulus().bitLength()!=3072)throw new IllegalStateException("rsa");
        KeyGenerator keygen=KeyGenerator.getInstance("AES");keygen.init(256);
        SecretKey aesKey=keygen.generateKey();
        byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);
        Cipher gcm=Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.ENCRYPT_MODE,aesKey,new GCMParameterSpec(128,iv));
        gcm.updateAAD(id.getBytes(StandardCharsets.US_ASCII));
        byte[] ciphertext=gcm.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        Cipher wrap=Cipher.getInstance("RSA/ECB/OAEPPadding");
        wrap.init(Cipher.ENCRYPT_MODE,key,oaep());
        byte[] wrapped=wrap.doFinal(aesKey.getEncoded());
        return String.join("\n",Base64.getEncoder().encodeToString(wrapped),
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(ciphertext));
    }
    private static void check(boolean value,String explanation){
        if(!value)throw new IllegalStateException(explanation);
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("source");
        Path source=Path.of(args[0]);
        String id=UUID.randomUUID().toString().replace("-","");
        Result enrolled=call(source,"enroll",id,new byte[0]);
        check(enrolled.status==0,"enrollment failed");
        String publicKey=new String(enrolled.out,StandardCharsets.US_ASCII);
        String envelope=seal(id,publicKey,PROBE);
        check(!envelope.contains(PROBE),"plaintext leaked to transport");
        Result decrypted=call(source,"decrypt",id,envelope.getBytes(StandardCharsets.US_ASCII));
        check(decrypted.status==0 && PROBE.equals(new String(decrypted.out,StandardCharsets.UTF_8)),"decrypt failed");
        Result reused=call(source,"decrypt",id,envelope.getBytes(StandardCharsets.US_ASCII));
        check(reused.status!=0 && reused.out.length==0,"one-use key was reused");
        String id2=UUID.randomUUID().toString().replace("-","");
        Result enrolled2=call(source,"enroll",id2,new byte[0]);
        check(enrolled2.status==0,"second enrollment failed");
        String valid=seal(id2,new String(enrolled2.out,StandardCharsets.US_ASCII),PROBE);
        String[] fields=valid.split("\n");
        char[] last=fields[2].toCharArray();
        last[last.length/2]=last[last.length/2]=='A'?'B':'A';
        fields[2]=new String(last);
        Result tampered=call(source,"decrypt",id2,String.join("\n",fields).getBytes(StandardCharsets.US_ASCII));
        check(tampered.status!=0 && tampered.out.length==0,"tampered cipher unexpectedly accepted");
        System.out.println("RELEASE_HYBRID_TRANSPORT_OK");
        System.out.println("synthetic_payload_only=true");
        System.out.println("one_time_private_key_reused=false");
        System.out.println("tampered_envelope_rejected=true");
    }
}