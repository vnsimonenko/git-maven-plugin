package com.gitmaven.plugin;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * The plugin take artifacts from github and its install them in a local repository.
 *
 * @see <a href="https://maven.apache.org/guides/plugin/guide-java-plugin-development.html">Your First Mojo</a>
 * @see <a href="https://maven.apache.org/developers/mojo-api-specification.html#The_Descriptor_and_Annotations">Mojo API</a>
 */
@Mojo(name = "git", defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class GitMogo extends AbstractMojo {
    public GitMogo() {
    }

    /**
     * Loads, build jar, install a jar in a local repository.
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (repositories == null) {
            return;
        }
        getLog().info("***** start git-maven-plugin *****");

        MavenProject project = (MavenProject) getPluginContext().get("project");
        for (Repository repository : repositories) {
            getLog().info(String.format("***** executing %s *****", repository.getScm()));
            correctRepository(project, repository);
        }
        checkRepository(repositories);

        for (Repository repository : repositories) {
            getLog().info(String.format("***** executing %s *****", repository.getScm()));
            if (!project.hasLifecyclePhase("process-resources") || project.hasLifecyclePhase("process-resources") && !project.hasLifecyclePhase("compile")) {
                ScmService scmService = new ScmService();
                try {
                    scmService.checkout(repository);
                } catch (Exception ex) {
                    throw new MojoFailureException("failed to ScmService.checkout()", ex);
                }
                JarService jarService = new JarService();
                try {
                    jarService.build(repository);
                } catch (Exception ex) {
                    throw new MojoFailureException("failed to JarService.build()", ex);
                }
            }
        }

        getLog().info("***** finish git-maven-plugin *****");
    }

    /**
     * 1. Check duplicate working directory.
     * 2. Check value present in scm.
     *
     * @param repositories
     * @return
     */
    private void checkRepository(List<Repository> repositories) throws MojoFailureException {
        Set<String> duplicate = new HashSet<>();
        for (Repository repository : repositories) {
            if (StringUtils.isBlank(repository.getScm())) {
                throw new MojoFailureException("attribute 'scm' is empty.");
            }
            if (duplicate.contains(repository.getWorkingDirectory())) {
                throw new MojoFailureException(repository.getWorkingDirectory() + " already exists.");
            }
            duplicate.add(repository.getWorkingDirectory());
        }
    }

    /**
     * Initialize the default workingDirectory if workingDirectory is empty.
     *
     * @param project    MavenProject
     * @param repository {@link GitMogo.Repository}
     */
    private void correctRepository(MavenProject project, Repository repository) {
        if (StringUtils.isBlank(repository.getWorkingDirectory())) {
            String dir = repository.getScm();
            dir = dir.substring(dir.lastIndexOf("/"), dir.length());
            repository.workingDirectory = Paths.get(project.getBuild().getDirectory(), "git-maven-plugin-working-dir", dir).toFile().getAbsolutePath();
        }
    }

    /**
     * example:
     * <configuration>
     * <repositories>
     * <repository>
     * <scm>scm:git:https://github.com/projects/CollectionManager</scm>
     * <scmVersionType>revision</scmVersionType>
     * <scmVersion>master</scmVersion>
     * <command>clean package install -DskipTests</command>
     * </repository>
     * <repository>
     * <scm>scm:git:https://github.com/projects/akka-stream</scm>
     * <workingDir>${project.basedir}/target/akka-stream/</workingDir>
     * <command>clean package install -DskipTests</command>
     * </repository>
     * </repositories>
     * </configuration>
     */
    @Parameter(property = "repositories")
    private List<Repository> repositories;

    public static class Repository {
        /**
         * scm url
         * scm:git:git://server_name[:port]/path_to_repository
         * scm:git:http://server_name[:port]/path_to_repository
         * scm:git:https://server_name[:port]/path_to_repository
         * scm:git:ssh://server_name[:port]/path_to_repository
         * scm:git:file://[hostname]/path_to_repository
         *
         * @see <a href="https://maven.apache.org/scm/git.html">SCM Implementation: Git</a>
         */
        private String scm;
        /**
         * scmVersionType can be 'branch', 'tag', 'revision'. The default value is 'revision'.
         */
        private String scmVersionType = "revision";
        /**
         * The version (revision number/branch name/tag name).
         */
        private String scmVersion = "master";
        /**
         * The checkout directory.
         * Default value is: ${project.build.directory}/git-maven-plugin-working-dir/[last part of path of scm].
         */
        private String workingDirectory;
        /**
         * The commands of clean package install -DskipTests is default.
         */
        private String command;

        public Repository() {
        }

        String getMavenHome() {
            return System.getProperty("maven.home");
        }

        public String getWorkingDirectory() {
            return workingDirectory;
        }

        public String getCommand() {
            return command;
        }

        public String getScm() {
            return scm;
        }

        public String getScmVersionType() {
            return scmVersionType;
        }

        public String getScmVersion() {
            return scmVersion;
        }
    }
}
