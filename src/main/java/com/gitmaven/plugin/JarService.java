package com.gitmaven.plugin;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The service exec maven commands.
 *
 * @see <a href="http://maven.apache.org/ref/3.3.9/maven-embedder/cli.html>Maven CLI Options Reference</a>
 */
public class JarService {
    private GitMogo.Repository repository;

    public int build(GitMogo.Repository repository) throws IOException, ExecutionException, InterruptedException {
        this.repository = repository;
        ExecutorService es = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                try {
                    thread.setContextClassLoader(createClassLoader());
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                return thread;
            }
        });
        Future<Integer> f = es.submit(new CallableMavenCli(repository));
        return f.get();
    }

    private ClassLoader createClassLoader() throws IOException {
        List<URL> urls = new ArrayList<>();
        Files.walkFileTree(Paths.get(repository.getMavenHome(), "lib"), new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toAbsolutePath().toString().endsWith(".jar")) {
                    urls.add(file.toUri().toURL());
                }
                return FileVisitResult.CONTINUE;
            }

            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
        return new URLClassLoader(urls.toArray(new URL[urls.size()]));
    }

    static class CallableMavenCli implements Callable<Integer> {
        private GitMogo.Repository repository;

        public CallableMavenCli(GitMogo.Repository repository) {
            this.repository = repository;
        }

        @Override
        public Integer call() throws Exception {
            return execute();
        }

        private int execute() throws Exception {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();

            Class<?> launcherClass = cl.loadClass("org.codehaus.plexus.classworlds.launcher.Launcher");
            Object launcher = launcherClass.newInstance();

            Method m = launcherClass.getMethod("setSystemClassLoader", new Class[]{ClassLoader.class});
            m.invoke(launcher, new Object[]{cl});
            m = launcherClass.getMethod("configure", new Class[]{InputStream.class});

            try (InputStream is = new FileInputStream(Paths.get(repository.getMavenHome(), "bin", "m2.conf").toFile())) {
                m.invoke(launcher, new Object[]{is});
            }
            m = launcherClass.getMethod("getWorld", new Class[]{});
            Object classWorld = m.invoke(launcher);

            Class<?> mavenCliClass = cl.loadClass("org.apache.maven.cli.MavenCli");
            Class<?> classWorldClass = cl.loadClass("org.codehaus.plexus.classworlds.ClassWorld");
            Constructor mavenCliConstructor = mavenCliClass.getConstructor(new Class[]{classWorldClass});
            Object mavenCli = mavenCliConstructor.newInstance(new Object[]{classWorld});
            m = mavenCliClass.getMethod("doMain", new Class[]{String[].class, String.class,
                    PrintStream.class, PrintStream.class});
            return (int) m.invoke(mavenCli, new Object[]{parseCommand(),
                    repository.getWorkingDirectory(), System.out, System.out});
        }

        private String[] parseCommand() {
            String cmd = repository.getCommand() == null ? "clean package install -DskipTests" : repository.getCommand();
            Pattern pattern = Pattern.compile("[ ]*([a-zA-Z\\-:.]+)+[ ]*");
            Matcher matcher = pattern.matcher(cmd);
            List<String> args = new ArrayList<>();
            while (matcher.find()) {
                args.add(matcher.group().trim());
            }
            return args.toArray(new String[args.size()]);
        }
    }
}
