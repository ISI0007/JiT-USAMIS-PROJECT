import java.sql.*;
public class Recon {
  public static void main(String[] a) throws Exception {
    Class.forName("org.postgresql.Driver");
    try(Connection c=DriverManager.getConnection("jdbc:postgresql://localhost:5432/usamis","postgres","postgres");
        Statement s=c.createStatement()){
      System.out.println("== TABLES ==");
      ResultSet t=s.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename");
      int nt=0; while(t.next()){ System.out.println("  "+t.getString(1)); nt++; }
      System.out.println("  COUNT="+nt);
      System.out.println("== VIEWS ==");
      ResultSet v=s.executeQuery("SELECT viewname FROM pg_views WHERE schemaname='public' ORDER BY viewname");
      int nv=0; while(v.next()){ System.out.println("  "+v.getString(1)); nv++; }
      System.out.println("  COUNT="+nv);
      System.out.println("== PERMISSIONS ==");
      ResultSet p=s.executeQuery("SELECT name,module FROM permissions ORDER BY name");
      int np=0; while(p.next()){ System.out.println("  "+p.getString(1)+" ("+p.getString(2)+")"); np++; }
      System.out.println("  COUNT="+np);
      System.out.println("== ROLES ==");
      ResultSet r=s.executeQuery("SELECT name FROM roles ORDER BY id");
      int nr=0; while(r.next()){ System.out.println("  "+r.getString(1)); nr++; }
      System.out.println("  COUNT="+nr);
      System.out.println("== ROLE x PERM GRANTS ==");
      ResultSet g=s.executeQuery("SELECT r.name, string_agg(p.name,',' ORDER BY p.name) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id GROUP BY r.name ORDER BY r.name");
      while(g.next()) System.out.println("  "+g.getString(1)+": "+g.getString(2));
      System.out.println("== FK CONSTRAINTS ==");
      ResultSet f=s.executeQuery("SELECT count(*) FROM information_schema.table_constraints WHERE constraint_type='FOREIGN KEY' AND table_schema='public'");
      f.next(); System.out.println("  COUNT="+f.getInt(1));
      System.out.println("== ROW COUNTS ==");
      for(String tb: new String[]{"users","students","courses","enrollments","grades","fee_records","audit_log","ai_prediction","departments","programs","instructors","attendance"}){
        try(ResultSet rc=s.executeQuery("SELECT count(*) FROM "+tb)){ rc.next(); System.out.println("  "+tb+"="+rc.getInt(1)); }
        catch(Exception e){ System.out.println("  "+tb+"=<"+(e.getMessage().length()>40?e.getMessage().substring(0,40):e.getMessage())+">"); }
      }
    }
  }
}
