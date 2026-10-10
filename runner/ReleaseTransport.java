// One-use Termux private transport key. This is NOT the user's signing key.
// Java source runs inside Termux. No signing secrets are stored on disk.
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermission;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

class ReleaseTransport {
    private static final Path STATE=Path.of("/data/data/com.termux/files/home/.local/share/animadev");
    private static final String PREFIX=".release-session-";
    private static boolean valid(String s){return s.matches("[a-f0-9]{32}");}
    private static Path keyFile(String id){
        if(!valid(id)) throw new IllegalArgumentException("id");
        return STATE.resolve(PREFIX+id+".key");
    }
    private static void ensureState() throws IOException {
        if(!Files.isDirectory(STATE,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(STATE))
            throw new IOException("state");
    }
    private static OAEPParameterSpec oaep() {
        return new OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT);
    }
    private static void enroll(String id) throws Exception {
        ensureState();
        Path file=keyFile(id);
        KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA");
        gen.initialize(3072);
        KeyPair pair=gen.generateKeyPair();
        Files.write(file,pair.getPrivate().getEncoded(),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        Files.setPosixFilePermissions(file,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));
        System.out.print(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
    }
    private static void decrypt(String id) throws Exception {
        ensureState();
        Path file=keyFile(id);
        if(Files.isSymbolicLink(file) || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("key");
        byte[] privateBytes=Files.readAllBytes(file);
        // A one-shot private key is consumed regardless of whether decrypt succeeds.
        Files.delete(file);
        PrivateKey privateKey=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
        Arrays.fill(privateBytes,(byte)0);
        byte[] input=System.in.readNBytes(230001);
        if(input.length>230000)throw new IOException("size");
        String[] lines=new String(input,StandardCharsets.US_ASCII).strip().split("\n");
        Arrays.fill(input,(byte)0);
        if(lines.length!=3)throw new IOException("envelope");
        Base64.Decoder b64=Base64.getDecoder();
        byte[] wrapped=b64.decode(lines[0]);
        byte[] iv=b64.decode(lines[1]);
        byte[] body=b64.decode(lines[2]);
        if(iv.length!=12 || wrapped.length!=384 || body.length<16 || body.length>190000)
            throw new IOException("envelope");
        Cipher rsa=Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.DECRYPT_MODE,privateKey,oaep());
        byte[] aesKey=rsa.doFinal(wrapped);
        if(aesKey.length!=32)throw new IOException("key");
        byte[] plaintext;
        try {
            Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.DECRYPT_MODE,new SecretKeySpec(aesKey,"AES"),new GCMParameterSpec(128,iv));
            aes.updateAAD(id.getBytes(StandardCharsets.US_ASCII));
            plaintext=aes.doFinal(body);
        } finally {Arrays.fill(aesKey,(byte)0);}
        try {System.out.write(plaintext);System.out.flush();}
        finally {Arrays.fill(plaintext,(byte)0);}
    }
    public static void main(String[] args) {
        try {
            if(args.length!=2 || !valid(args[1]))throw new IllegalArgumentException("args");
            if(args[0].equals("enroll"))enroll(args[1]);
            else if(args[0].equals("decrypt"))decrypt(args[1]);
            else throw new IllegalArgumentException("mode");
        } catch(Exception error) {
            // Never print private keys, passwords, plaintext or exception messages.
            System.err.print("RELEASE_TRANSPORT_FAILED");
            System.exit(1);
        }
    }
}