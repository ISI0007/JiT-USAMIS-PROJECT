package com.usamis.verify;

import org.apache.catalina.startup.Tomcat;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone launcher used only for verification: starts an embedded Tomcat
 * serving the webapp with the project's compiled classes + dependency jars.
 * Not part of the shipped application (which deploys as a WAR).
 *
 *   java -cp <cp> com.usamis.verify.VerifyServer <buildDir> <webappDir> <cp> <port>
 */
public class VerifyServer {
    public static void main(String[] args) throws Exception {
        String buildDir  = args.length > 0 ? args[0] : System.getProperty("java.io.tmpdir") + "/usamis_build";
        String webappDir = args.length > 1 ? args[1] : "java/src/main/webapp";
        String cp        = args.length > 2 ? args[2] : buildDir;
        int    port      = args.length > 3 ? Integer.parseInt(args[3]) : 8080;

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.getConnector();
        tomcat.getHost().setAppBase(new File(buildDir).getAbsolutePath());

        // Build a classloader holding the compiled classes + all dependency jars,
        // and hand it to the webapp so @WebServlet/@WebFilter annotations are
        // scanned and the app classes are loadable.
        List<URL> urls = new ArrayList<>();
        for (String entry : cp.split(File.pathSeparator)) {
            if (entry.isBlank()) continue;
            File f = new File(entry);
            if (f.exists()) urls.add(f.toURI().toURL());
        }
        URLClassLoader webappCl = new URLClassLoader(urls.toArray(new URL[0]),
                VerifyServer.class.getClassLoader());

        File docBase = new File(webappDir).getAbsoluteFile();
        org.apache.catalina.Context ctx = tomcat.addWebapp("/usamis", docBase.getAbsolutePath());
        ctx.setParentClassLoader(webappCl);

        tomcat.start();
        System.out.println("USAMIS VerifyServer started on http://127.0.0.1:" + port + "/usamis/");
        tomcat.getServer().await();
    }
}
