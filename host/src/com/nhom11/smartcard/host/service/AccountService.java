package com.nhom11.smartcard.host.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Local demo accounts. Host authorization is not an applet security boundary. */
public final class AccountService {
    public enum Role { ADMIN, USER }
    private static final String HEADER = "EMPLOYEE_ACCOUNTS_V1";
    private static final int ITERATIONS = 210_000;
    private final Path file;
    private final SecureRandom random = new SecureRandom();
    private Map<String, Account> accounts;
    private String session;

    private static final class Account {
        final String name; final Role role; final byte[] salt; final byte[] hash; final int iterations;
        Account(String n, Role r, byte[] s, byte[] h, int i) { name=n; role=r; salt=s; hash=h; iterations=i; }
    }

    public AccountService(Path file) throws IOException {
        this.file = Objects.requireNonNull(file).toAbsolutePath();
        this.accounts = Files.exists(this.file) ? read() : new LinkedHashMap<>();
    }
    public synchronized boolean needsSetup() { return accounts.isEmpty() && Files.notExists(file); }
    public synchronized void setup(String username, char[] password) throws IOException {
        if (!needsSetup()) throw new IllegalStateException("Đã thiết lập tài khoản.");
        String name = validateName(username); validatePassword(password);
        Map<String,Account> next = new LinkedHashMap<>();
        next.put(name, makeAccount(name, Role.ADMIN, password));
        persist(next); accounts = next;
    }
    public synchronized boolean login(String username, char[] password) {
        session = null;
        if (username == null || password == null) return false;
        Account a = accounts.get(username.trim());
        if (a == null || !MessageDigest.isEqual(a.hash, derive(password, a.salt, a.iterations))) return false;
        session = a.name; return true;
    }
    public synchronized void logout() { session = null; }
    public synchronized String currentUser() { return session; }
    public synchronized boolean isAdmin() { return session != null && accounts.containsKey(session) && accounts.get(session).role == Role.ADMIN; }
    public synchronized void requireAdmin() { if (!isAdmin()) throw new SecurityException("Cần đăng nhập tài khoản quản trị."); }
    public synchronized List<String> listUsers() {
        requireAdmin(); List<String> result = new ArrayList<>();
        for (Account a : accounts.values()) result.add(a.name + "\t" + a.role.name());
        return result;
    }
    public synchronized void create(String username, char[] password, Role role) throws IOException {
        requireAdmin(); String name = validateName(username); validatePassword(password); Objects.requireNonNull(role);
        if (accounts.containsKey(name)) throw new IllegalArgumentException("Tài khoản đã tồn tại.");
        Map<String,Account> next = new LinkedHashMap<>(accounts); next.put(name, makeAccount(name, role, password));
        persist(next); accounts=next;
    }
    /** Update role and optionally password; an empty password keeps the existing hash. */
    public synchronized void update(String username, char[] password, Role role) throws IOException {
        requireAdmin(); Objects.requireNonNull(role); Account old=accounts.get(username);
        if (old == null) throw new IllegalArgumentException("Không tìm thấy tài khoản.");
        if (old.role == Role.ADMIN && role != Role.ADMIN && adminCount() == 1) throw new IllegalArgumentException("Phải giữ ít nhất một quản trị viên.");
        Account changed;
        if (password != null && password.length > 0) { validatePassword(password); changed=makeAccount(old.name,role,password); }
        else changed=new Account(old.name,role,old.salt,old.hash,old.iterations);
        Map<String,Account> next = new LinkedHashMap<>(accounts); next.put(old.name, changed);
        persist(next); accounts=next;
        if (old.name.equals(session) && role != Role.ADMIN) session=null;
    }
    public synchronized void delete(String username) throws IOException {
        requireAdmin(); Account old=accounts.get(username);
        if (old == null) throw new IllegalArgumentException("Không tìm thấy tài khoản.");
        if (old.role == Role.ADMIN && adminCount() == 1) throw new IllegalArgumentException("Phải giữ ít nhất một quản trị viên.");
        Map<String,Account> next = new LinkedHashMap<>(accounts); next.remove(username);
        persist(next); accounts=next; if (username.equals(session)) session=null;
    }
    private int adminCount() { return (int) accounts.values().stream().filter(a -> a.role==Role.ADMIN).count(); }
    private String validateName(String value) {
        if (value==null || !value.trim().matches("[A-Za-z0-9_.-]{3,40}")) throw new IllegalArgumentException("Tên tài khoản cần 3–40 ký tự chữ, số, dấu chấm, gạch hoặc gạch dưới.");
        return value.trim();
    }
    private void validatePassword(char[] value) { if (value==null || value.length<8 || value.length>128) throw new IllegalArgumentException("Mật khẩu cần 8–128 ký tự."); }
    private Account makeAccount(String name, Role role, char[] password) {
        byte[] salt=new byte[16]; random.nextBytes(salt); return new Account(name, role, salt, derive(password,salt,ITERATIONS),ITERATIONS);
    }
    private static byte[] derive(char[] pass, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(pass,salt,iterations,256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("Không hỗ trợ PBKDF2",e); }
        finally { spec.clearPassword(); }
    }
    private Map<String,Account> read() throws IOException {
        Map<String,Account> result=new LinkedHashMap<>();
        try {
            List<String> lines=Files.readAllLines(file,StandardCharsets.UTF_8);
            if(lines.isEmpty() || !HEADER.equals(lines.get(0))) throw new IllegalArgumentException("header");
            for (int i=1;i<lines.size();i++) {
                String[] p=lines.get(i).split("\\t",-1); if(p.length!=5) throw new IllegalArgumentException("row");
                String name=validateName(new String(Base64.getDecoder().decode(p[0]),StandardCharsets.UTF_8));
                Role role=Role.valueOf(p[1]); int iter=Integer.parseInt(p[2]);
                byte[] salt=Base64.getDecoder().decode(p[3]),hash=Base64.getDecoder().decode(p[4]);
                if(iter<100_000 || iter>2_000_000 || salt.length!=16 || hash.length!=32 || result.putIfAbsent(name,new Account(name,role,salt,hash,iter))!=null) throw new IllegalArgumentException("record");
            }
            if(result.values().stream().noneMatch(a -> a.role==Role.ADMIN)) throw new IllegalArgumentException("missing admin");
        } catch(RuntimeException ex) { throw new IOException("Không đọc được file tài khoản; file không bị ghi đè: "+file,ex); }
        return result;
    }
    private void persist(Map<String,Account> values) throws IOException {
        List<String> lines=new ArrayList<>(); lines.add(HEADER);
        for(Account a:values.values()) lines.add(Base64.getEncoder().encodeToString(a.name.getBytes(StandardCharsets.UTF_8))+"\t"+a.role+"\t"+a.iterations+"\t"+Base64.getEncoder().encodeToString(a.salt)+"\t"+Base64.getEncoder().encodeToString(a.hash));
        Files.createDirectories(file.getParent()); Path tmp=Files.createTempFile(file.getParent(),"accounts-",".tmp");
        try { Files.write(tmp,lines,StandardCharsets.UTF_8); try { Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); } catch(AtomicMoveNotSupportedException e) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); } }
        finally { Files.deleteIfExists(tmp); }
    }
}
