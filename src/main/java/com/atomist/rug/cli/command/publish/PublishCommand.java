package com.atomist.rug.cli.command.publish;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.version.Version;

import com.atomist.rug.cli.Constants;
import com.atomist.rug.cli.command.AbstractRepositoryCommand;
import com.atomist.rug.cli.command.CommandException;
import com.atomist.rug.cli.command.CommandUtils;
import com.atomist.rug.cli.command.annotation.Validator;
import com.atomist.rug.cli.command.search.SearchCommand;
import com.atomist.rug.cli.command.search.SearchOperations.Archive;
import com.atomist.rug.cli.command.search.SearchOperations.Operation;
import com.atomist.rug.cli.command.utils.GitUtils;
import com.atomist.rug.cli.output.ProgressReportingOperationRunner;
import com.atomist.rug.cli.output.ProgressReportingTransferListener;
import com.atomist.rug.cli.output.Style;
import com.atomist.rug.cli.settings.Settings;
import com.atomist.rug.cli.settings.Settings.Authentication;
import com.atomist.rug.cli.settings.Settings.RemoteRepository;
import com.atomist.rug.cli.settings.SettingsReader;
import com.atomist.rug.cli.utils.CommandLineOptions;
import com.atomist.rug.cli.utils.FileUtils;
import com.atomist.rug.cli.utils.StringUtils;
import com.atomist.rug.cli.version.VersionUtils;
import com.atomist.rug.resolver.manifest.Manifest;
import com.atomist.source.ArtifactSource;

public class PublishCommand extends AbstractRepositoryCommand {

    @Validator
    public void validate(CommandLine commandLine) {
        verifyWorkingTree(commandLine);
    }

    protected void doWithRepositorySession(RepositorySystem system, RepositorySystemSession session,
            ArtifactSource source, Manifest manifest, Artifact zip, Artifact pom, Artifact metadata,
            CommandLine commandLine) {
        Set<String> ids = org.springframework.util.StringUtils
                .commaDelimitedListToSet(CommandLineOptions.getOptionValue("id").orElse(""));

        if (CommandLineOptions.hasOption("U")) {
            String group = manifest.group();
            String artifact = manifest.artifact();
            Version version = VersionUtils.parseVersion(manifest.version());

            SearchCommand search = new SearchCommand();
            Map<String, List<Operation>> operations = search.search(SettingsReader.read(), null,
                    new Properties(), null);

            operations.values().stream().filter(ops -> ops.size() > 0).forEach(ops -> {
                Archive archive = ops.get(0).archive();
                if (archive.group().equals(group) && archive.artifact().equals(artifact)
                        && !"global".equals(archive.scope())) {
                    if (version
                            .compareTo(VersionUtils.parseVersion(archive.version().value())) > 0) {
                        ids.add(archive.scope());
                    }
                }
            });
        }

        List<org.eclipse.aether.repository.RemoteRepository> deployRepositorys = getDeployRepositories(
                ids);
        deployRepositorys.forEach(
                r -> publishToRepository(system, session, source, manifest, zip, pom, metadata, r));
    }

    private void publishToRepository(RepositorySystem system, RepositorySystemSession session,
            ArtifactSource source, Manifest manifest, Artifact zip, Artifact pom, Artifact metadata,
            org.eclipse.aether.repository.RemoteRepository deployRepository) {
        String artifactUrl = new ProgressReportingOperationRunner<String>(
                String.format("Publishing archive into repository %s", deployRepository.getId()))
                        .run(indicator -> {
                            String[] url = new String[1];
                            ((DefaultRepositorySystemSession) session).setTransferListener(
                                    new ProgressReportingTransferListener(indicator, false) {

                                        @Override
                                        public void transferSucceeded(TransferEvent event) {
                                            super.transferSucceeded(event);
                                            if (event.getResource().getResourceName()
                                                    .endsWith(".zip")) {
                                                url[0] = event.getResource().getRepositoryUrl()
                                                        + event.getResource().getResourceName();
                                            }
                                        }
                                    });

                            DeployRequest deployRequest = new DeployRequest();
                            deployRequest.addArtifact(zip).addArtifact(pom).addArtifact(metadata);
                            deployRequest.setRepository(deployRepository);

                            system.deploy(session, deployRequest);

                            return url[0];
                        });

        log.newline();
        log.info(Style.cyan(Constants.DIVIDER) + " " + Style.bold("Archive"));
        log.info("  %s (%s in %s files)", Style.underline(FileUtils.relativize(zip.getFile())),
                FileUtils.sizeOf(zip.getFile()), source.allFiles().size());

        printTree(source);

        log.newline();
        log.info(Style.cyan(Constants.DIVIDER) + " " + Style.bold("URL"));
        log.info("  %s", Style.underline(artifactUrl));

        log.newline();
        log.info(Style.green("Successfully published archive for %s:%s (%s)", manifest.group(),
                manifest.artifact(), manifest.version()));
    }

    private void verifyWorkingTree(CommandLine commandLine) {
        boolean force = commandLine.hasOption("force");
        File projectRoot = CommandUtils.getRequiredWorkingDirectory();
        if (!force) {
            GitUtils.isClean(projectRoot, "publish");
        }
    }

    private List<org.eclipse.aether.repository.RemoteRepository> getDeployRepositories(
            Set<String> repoIds) {
        Settings settings = SettingsReader.read();
        if (repoIds.size() > 0) {
            return repoIds.stream().map(r -> getDeployRepository(r.trim(), settings))
                    .collect(Collectors.toList());
        }
        else {
            return Collections.singletonList(getDeployRepository(null, settings));
        }
    }

    private org.eclipse.aether.repository.RemoteRepository getDeployRepository(String repoId,
            Settings settings) {
        Map<String, RemoteRepository> deployRepositories = settings.getRemoteRepositories()
                .entrySet().stream().filter(e -> e.getValue().isPublish())
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

        if (repoId != null) {
            Optional<Entry<String, RemoteRepository>> repo = deployRepositories.entrySet().stream()
                    .filter(e -> repoId.equalsIgnoreCase(e.getValue().getName())
                            || repoId.equalsIgnoreCase(e.getKey()))
                    .findAny();
            if (repo.isPresent()) {
                return toRepository(repoId, repo.get().getValue());
            }
            else {
                throw new CommandException(String.format(
                        "Specified repository with id %s doesn't exist or is not enabled for publishing.\nPlease review your ~/.atomist/cli.yml.",
                        repoId), "publish");
            }
        }

        if (deployRepositories.size() > 1) {
            throw new CommandException(String.format(
                    "More than one repository enabled for publishing.\nPlease review your ~/.atomist/cli.yml or specify a repository with --id ID.\n\nValid repository ID values are:\n  %s",
                    org.springframework.util.StringUtils
                            .collectionToDelimitedString(deployRepositories.entrySet().stream()
                                    .map(e -> e.getValue().getName() + " (" + e.getKey() + ")")
                                    .collect(Collectors.toList()), ",\n  ")),
                    "publish");
        }
        else if (deployRepositories.size() == 0) {
            throw new CommandException(
                    "No repository enabled for publishing.\nPlease review your ~/.atomist/cli.yml.",
                    "publish");
        }

        Entry<String, RemoteRepository> remoteRepository = deployRepositories.entrySet().stream()
                .findFirst().get();
        return toRepository(remoteRepository.getKey(), remoteRepository.getValue());
    }

    private org.eclipse.aether.repository.RemoteRepository toRepository(String id,
            RemoteRepository remoteRepository) {
        org.eclipse.aether.repository.RemoteRepository.Builder builder = new org.eclipse.aether.repository.RemoteRepository.Builder(
                id, "default", StringUtils.expandEnvironmentVars(remoteRepository.getUrl()));

        if (remoteRepository.getAuthentication() != null) {
            Authentication auth = remoteRepository.getAuthentication();
            builder.setAuthentication(new AuthenticationBuilder()
                    .addUsername(StringUtils.expandEnvironmentVars(auth.getUsername()))
                    .addPassword(StringUtils.expandEnvironmentVars(auth.getPassword())).build())
                    .build();
        }

        return builder.build();

    }

}
