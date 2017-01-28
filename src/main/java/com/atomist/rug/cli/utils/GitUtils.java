package com.atomist.rug.cli.utils;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.atomist.project.ProjectOperationArguments;
import com.atomist.project.ProvenanceInfoWriter;
import com.atomist.project.edit.ProjectEditor;
import com.atomist.project.generate.ProjectGenerator;
import com.atomist.rug.cli.Constants;
import com.atomist.rug.cli.Log;
import com.atomist.rug.cli.RunnerException;

public abstract class GitUtils {

    private static Log log = new Log(GitUtils.class);

    public static void initializeRepoAndCommitFiles(ProjectGenerator generator,
            ProjectOperationArguments arguments, File root) {
        try (Git git = Git.init().setDirectory(root).call()) {
            log.info("Initialized a new git repository at " + git.getRepository().getDirectory());
            git.add().addFilepattern(".").call();
            RevCommit commit = git.commit().setAll(true)
                    .setMessage(
                            String.format("Initial commit by generator %s\n\n%s", generator.name(),
                                    new ProvenanceInfoWriter().write(generator, arguments,
                                            Constants.cliClient())))
                    .setAuthor("Atomist", "cli@atomist.com").call();
            log.info("Committed initial set of files to git repository (%s)",
                    commit.abbreviate(7).name());
        }
        catch (IllegalStateException | GitAPIException e) {
            throw new RunnerException(e);
        }
    }

    public static void commitFiles(ProjectEditor editor, ProjectOperationArguments arguments,
            File root) {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder.setGitDir(new File(root, ".git")).readEnvironment()
                .findGitDir().build()) {
            try (Git git = new Git(repository)) {
                log.info("Committing to git repository at " + git.getRepository().getDirectory());
                git.add().addFilepattern(".").call();
                RevCommit commit = git.commit().setAll(true)
                        .setMessage(String.format("Commit by editor %s\n\n%s", editor.name(),
                                new ProvenanceInfoWriter().write(editor, arguments,
                                        Constants.cliClient())))
                        .setAuthor("Atomist", "cli@atomist.com").call();
                log.info("Committed changes to git repository (%s)", commit.abbreviate(7).name());
            }
        }
        catch (IllegalStateException | IOException | GitAPIException e) {
            throw new RunnerException(e);
        }
    }

}