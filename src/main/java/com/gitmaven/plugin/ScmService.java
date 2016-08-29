package com.gitmaven.plugin;

import com.google.common.reflect.ClassPath;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.maven.scm.AbstractScmVersion;
import org.apache.maven.scm.ScmBranch;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmRevision;
import org.apache.maven.scm.ScmTag;
import org.apache.maven.scm.command.diff.DiffScmResult;
import org.apache.maven.scm.manager.plexus.DefaultScmManager;
import org.apache.maven.scm.provider.git.gitexe.GitExeScmProvider;
import org.apache.maven.scm.repository.ScmRepository;
import org.codehaus.plexus.logging.console.ConsoleLogger;

/**
 * The service take project from github.
 *
 * @see <a href="http://maven.apache.org/components/scm/maven-scm-client/">Maven SCM Client</a>
 * @see <a href="https://maven.apache.org/scm/apidocs/index.html">Maven SCM 1.9.5 API</a>
 * @see <a href="https://maven.apache.org/scm/git.html">SCM Implementation: Git</a>
 **/
public class ScmService {
    private final static String WORKER_DIR = "worker_dir";
    private final static String SCM = "scm";
    private final static String SCM_VERSION_TYPE = "scm_version_type";
    private final static String SCM_VERSION = "scm_version";

    public void checkout(GitMogo.Repository repository) throws Exception {
        JarClassLoader cl = JarClassLoader.create();
        cl.defineClass(ScmService.class.getName(), getClass().getResourceAsStream("/com/gitmaven/plugin/ScmService.class"));
        cl.defineClass(GitMogo.Repository.class.getName(), getClass().getResourceAsStream("/com/gitmaven/plugin/GitMogo$Repository.class"));
        Class<?> launcherClass = cl.loadClass(ScmService.class.getName());
        Object launcher = launcherClass.newInstance();

        Properties properties = new Properties();
        properties.put(WORKER_DIR, repository.getWorkingDirectory());
        properties.put(SCM, repository.getScm());
        properties.put(SCM_VERSION_TYPE, repository.getScmVersionType());
        properties.put(SCM_VERSION, repository.getScmVersion());
        Method m = launcherClass.getMethod("checkout", new Class[]{Properties.class});
        m.invoke(launcher, new Object[]{properties});
    }

    public void checkout(Properties prop) throws Exception {
        String scmUrl = prop.getProperty(SCM);
        File workingDirectory = new File(prop.getProperty(WORKER_DIR)).getAbsoluteFile();
        if (!workingDirectory.exists()) {
            workingDirectory.mkdirs();
        }

        DefaultScmManager scmManager = new DefaultScmManager();
        GitExeScmProvider scmProvider = new GitExeScmProvider();
        scmManager.enableLogging(new ConsoleLogger(1, "c"));
        scmManager.setScmProvider("git", scmProvider);
        AbstractScmVersion scmVersion;
        String version = prop.getProperty(SCM_VERSION);

        switch (prop.getProperty(SCM_VERSION_TYPE)) {
            case "revision":
                scmVersion = new ScmRevision(version);
                break;
            case "branch":
                scmVersion = new ScmBranch(version);
                break;
            case "tag":
                scmVersion = new ScmTag(version);
                break;
            default:
                throw new IllegalArgumentException("not found scmVersion");
        }

        ScmRepository scRepository = scmManager.makeScmRepository(scmUrl);
        ScmFileSet fileSet = new ScmFileSet(workingDirectory);
        DiffScmResult scmResult = scmManager.diff(scRepository, new ScmFileSet(workingDirectory), null, null);
        if (workingDirectory.listFiles().length == 0 || scmResult.getChangedFiles() == null || scmResult.getChangedFiles().size() > 0) {
            scmManager.checkOut(scRepository, fileSet, scmVersion);
        }
    }

    static class JarClassLoader extends URLClassLoader {

        public JarClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
            try {
                loadClassesByURLs();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            return super.loadClass(name, resolve);
        }

        private void loadClassesByURLs() throws IOException {
            Map<String, ByteArrayOutputStream> classes = new HashMap<>();
            for (URL u : getURLs()) {
                URLConnection uc = ((URLConnection) u.openConnection());
                JarInputStream entry = new JarInputStream((InputStream) uc.getContent());
                JarEntry jarEntry;
                while ((jarEntry = entry.getNextJarEntry()) != null) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    if (jarEntry.getName().endsWith(".class")) {
                        IOUtils.copy(entry, out);
                        String name = jarEntry.getName();
                        name = jarEntry.getName().replaceAll("/", ".").substring(0, name.length() - 6);
                        classes.put(name, out);
                    }
                }
                entry.close();
            }
            Queue<String> classNames = new LinkedList<>(classes.keySet());
            String name = "";
            int i, size = i = classNames.size();
            while (!classNames.isEmpty()) {
                try {
                    if (i == 0) {
                        if (classNames.size() == size)
                            break;
                        else
                            size = i = classNames.size();
                    } else {
                        i--;
                    }
                    name = classNames.poll();
                    ByteArrayOutputStream out = classes.get(name);
                    byte[] bs = out.toByteArray();
                    if (this.findLoadedClass(name) == null) {
                        defineClass(name, bs, 0, bs.length);
                    }
                } catch (NoClassDefFoundError ex) {
                    classNames.add(name);
                }
            }
            System.out.println(classNames.toArray());
        }

        public void defineClass(String className, InputStream in) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            IOUtils.copy(in, out);
            IOUtils.closeQuietly(in);
            byte[] bs = out.toByteArray();
            if (this.findLoadedClass(className) == null) {
                defineClass(className, bs, 0, bs.length);
            }
        }

        static JarClassLoader create() throws IOException {
            List<ClassPath.ResourceInfo> infos = ClassPath.from(ScmService.class.getClassLoader()).getResources().asList();
            List<URL> urls = new ArrayList<URL>(infos.size());
            for (ClassPath.ResourceInfo r : infos) {
                if (r.getResourceName().matches("lib/.+[.]jar")) {
                    urls.add(r.url());
                }
            }
            return new JarClassLoader(urls.toArray(new URL[urls.size()]), Thread.currentThread().getContextClassLoader().getParent());
        }
    }
}
