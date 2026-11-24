package com.nhom11.smartcard.host;
import com.nhom11.smartcard.host.model.*;
import com.nhom11.smartcard.host.provider.*;
import com.nhom11.smartcard.host.service.AccountService;
import java.nio.file.Files;
import java.time.LocalDateTime;

public final class PolicyAuthorizationTest {
    public static void main(String[] args) throws Exception {
        var dir=Files.createTempDirectory("policy-auth-test");
        var accounts=new AccountService(dir.resolve("accounts.tsv"));
        accounts.setup("admin","demo-pass".toCharArray());
        var employees=new InMemoryEmployeeProvider();
        employees.put(new Employee("E1","Demo","Dept","Role",true));
        var seed=new InMemoryAccessPolicyProvider(); seed.putArea(new Area("A",1,"A",1));
        var policies=new FileAccessPolicyProvider(dir.resolve("policy.tsv"),employees,seed,accounts::requireAdmin);
        var rule=new AccessRule("R","A","Dept",null,AccessRule.MatchMode.DEPARTMENT_OR_POSITION,true);
        try { policies.saveRule(rule); throw new AssertionError("anonymous write"); } catch(SecurityException expected) { }
        if(!policies.listRules().isEmpty()) throw new AssertionError("changed without session");
        if(!accounts.login("admin","demo-pass".toCharArray())) throw new AssertionError("login");
        policies.saveRule(rule);
        accounts.create("user","demo-pass".toCharArray(),AccountService.Role.USER);
        accounts.logout(); accounts.login("user","demo-pass".toCharArray());
        try { policies.saveGrant(new TemporaryGrant("G","E1","A",LocalDateTime.now(),LocalDateTime.now().plusHours(1),false,"")); throw new AssertionError("user write"); } catch(SecurityException expected) { }
        if(!policies.listGrants().isEmpty()) throw new AssertionError("grant changed");
        System.out.println("PolicyAuthorizationTest: 4 checks passed.");
    }
}
