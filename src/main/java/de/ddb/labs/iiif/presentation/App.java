/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.ddb.labs.iiif.presentation;

/**
 *
 * @author Michael Büchner <m.buechner@dnb.de>
 */
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.javalin.Javalin;
import io.javalin.plugin.rendering.vue.VueComponent;
import io.javalin.http.staticfiles.Location;
import io.javalin.plugin.json.JavalinJackson;
import io.javalin.plugin.openapi.jackson.JacksonToJsonMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import static java.util.Collections.singleton;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

    private static final Logger LOG = LoggerFactory.getLogger(App.class);
    private Path folder;
    private Git git;
    private ObjectId oIdOfLastCommit;

    public App() {
        try {
            // set System properties for pathes
            // get env and overwrite default configuration
            if (System.getenv("iiif-presentation.git-url") != null) {
                System.setProperty("iiif-presentation.git-url", System.getenv("iiif-presentation.git-url"));
                Configuration.get().setValue("iiif-presentation.git-url", System.getenv("iiif-presentation.git-url"));
            } else {
                System.setProperty("iiif-presentation.git-url", Configuration.get().getValue("iiif-presentation.git-url"));
            }
            if (System.getenv("iiif-presentation.git-branch") != null) {
                System.setProperty("iiif-presentation.git-branch", System.getenv("iiif-presentation.git-branch"));
                Configuration.get().setValue("iiif-presentation.git-branch", System.getenv("iiif-presentation.git-branch"));
            } else {
                System.setProperty("iiif-presentation.git-branch", Configuration.get().getValue("iiif-presentation.git-branch"));
            }
            if (System.getenv("iiif-presentation.folder") != null) {
                System.setProperty("iiif-presentation.folder", System.getenv("iiif-presentation.folder"));
                Configuration.get().setValue("iiif-presentation.folder", System.getenv("iiif-presentation.folder"));
            } else {
                System.setProperty("iiif-presentation.folder", Configuration.get().getValue("iiif-presentation.folder"));
            }
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            System.exit(-1);
        }
        // make local folder
        try {
            folder = Files.createTempDirectory("iiif-image-git");
        } catch (IOException ex) {
            LOG.warn(ex.getMessage());
            folder = Paths.get("tmp/");
        }
        // clone repro
        try {
            clone(folder);
        } catch (IOException | GitAPIException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    public static void main(String[] args) {
        try {
            new App().start();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            System.exit(-1);
        }
    }

    private void start() throws Exception {

        final String files = folder.toString() + File.separator + Configuration.get().getValue("iiif-presentation.folder");
       
        JavalinJackson.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        JavalinJackson.getObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        
        final Javalin app = Javalin.create(config -> {
            config.enableCorsForAllOrigins();
            config.autogenerateEtags = true;
            config.showJavalinBanner = false;
            config.addStaticFiles(files, Location.EXTERNAL);
            config.addStaticFiles("/viewer");
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.stop();
        }));

        app.events(event -> {
            event.serverStopping(() -> {
                if (git != null) {
                    git.close();
                }
                FileUtils.delete(folder.toFile(), FileUtils.RECURSIVE);
            });

        });

        // set UTF-8 as default charset
        app.before(ctx -> {
            ctx.res.setCharacterEncoding("UTF-8");
        });

        app.get("/api/files", ctx -> {
            final List<String> fileNames = Files.list(folder)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toList());
            List<IiifFile> f = new ArrayList<>();
            for (String fileName : fileNames) {
                f.add(new IiifFile(fileName));
            }
            ctx.json(f);
        });

        app.get("/files", new VueComponent("<file-overview></file-overview>"));
        app.get("/messages/:user", new VueComponent("<thread-view></thread-view>"));

        app.start(80);
    }

    private void clone(Path folder) throws IOException, GitAPIException {

        LOG.info("Clone Branch " + Configuration.get().getValue("iiif-presentation.git-branch") + " von " + Configuration.get().getValue("iiif-presentation.git-url") + "...");
        try {
            git = Git.cloneRepository()
                    .setURI(Configuration.get().getValue("iiif-presentation.git-url"))
                    .setDirectory(folder.toFile())
                    .setBranchesToClone(singleton(Configuration.get().getValue("iiif-presentation.git-branch")))
                    .setBranch(Configuration.get().getValue("iiif-presentation.git-branch"))
                    .call();
            pullRepository();
        } catch (IOException | GitAPIException e) {
            LOG.error(e.getMessage());
        }
    }

    private void pullRepository() throws IncorrectObjectTypeException, GitAPIException, IOException {

        final Map<String, Ref> map = Git.lsRemoteRepository()
                .setHeads(true)
                .setTags(true)
                .setRemote(Configuration.get().getValue("iiif-presentation.git-url"))
                .callAsMap();

        final Ref commit = map.get(Configuration.get().getValue("iiif-presentation.git-branch"));
        final ObjectId oId = commit.getObjectId();

        if (oId != null && !oId.equals(oIdOfLastCommit) && git != null) {
            git.pull();
            oIdOfLastCommit = oId;
            LOG.info("ObjectId of last commit is now: {}", oIdOfLastCommit);
        }
    }

    public class IiifFile {

        private String id;
        private String name;

        public IiifFile(String name) {
            this.name = name;
            this.id = "";
        }

        /**
         * @return the id
         */
        public String getId() {
            return id;
        }

        /**
         * @param id the id to set
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * @return the name
         */
        public String getName() {
            return name;
        }

        /**
         * @param name the name to set
         */
        public void setName(String name) {
            this.name = name;
        }
    }

}
