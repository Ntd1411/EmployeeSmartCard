package com.nhom11.smartcard.host;

import com.nhom11.smartcard.host.service.AccountService;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AccountServiceTest {
    static int checks;
    static void check(boolean b,String message) { if(!b) throw new AssertionError(message); checks++; }
    public static void main(String[] args) throws Exception {
        Path file=Files.createTempDirectory("account-test").resolve("users.tsv");
        AccountService s=new AccountService(file); check(s.needsSetup(),"setup needed");
        s.setup("admin", "demo-pass-1".toCharArray());
        check(!Files.readString(file).contains("demo-pass-1"),"no cleartext password");
        check(!s.login("admin","incorrect".toCharArray()),"bad password");
        try { s.create("user", "demo-pass-2".toCharArray(),AccountService.Role.USER); throw new AssertionError("unauthorized"); }
        catch(SecurityException expected) { check(true,"guard"); }
        check(s.login("admin","demo-pass-1".toCharArray()) && s.isAdmin(),"admin login");
        s.create("user","demo-pass-2".toCharArray(),AccountService.Role.USER);
        try { s.delete("admin"); throw new AssertionError("last admin deleted"); } catch(IllegalArgumentException expected) { check(true,"last admin"); }
        s.update("user","demo-pass-3".toCharArray(),AccountService.Role.USER);
        s.logout(); check(!s.login("user","demo-pass-2".toCharArray()),"old password rejected");
        check(s.login("user","demo-pass-3".toCharArray()) && !s.isAdmin(),"user login");
        try { s.listUsers(); throw new AssertionError("user listed accounts"); } catch(SecurityException expected) { check(true,"user denied"); }
        AccountService reload=new AccountService(file); check(reload.login("admin","demo-pass-1".toCharArray()),"reload");
        reload.delete("user"); reload.logout(); check(!reload.login("user","demo-pass-3".toCharArray()),"deleted");
        Files.writeString(file,"broken");
        try { new AccountService(file); throw new AssertionError("bad file"); } catch(java.io.IOException expected) { check(Files.readString(file).equals("broken"),"bad file preserved"); }
        System.out.println("AccountServiceTest: "+checks+" checks passed.");
    }
}
