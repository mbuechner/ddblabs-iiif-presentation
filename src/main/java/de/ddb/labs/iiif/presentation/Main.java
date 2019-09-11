/* 
 * Copyright 2019 Michael Büchner, Deutsche Digitale Bibliothek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.ddb.labs.iiif.presentation;

/**
 *
 * @author Michael Büchner <m.buechner@dnb.de>
 */
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.rjeschke.txtmark.Processor;
import io.javalin.Javalin;
import io.javalin.plugin.rendering.vue.VueComponent;
import io.javalin.http.staticfiles.Location;
import io.javalin.plugin.json.JavalinJackson;
import io.javalin.plugin.openapi.annotations.ContentType;
import io.javalin.plugin.rendering.vue.JavalinVue;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import static java.util.Collections.singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.MessageDigest;

public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private Path folder;
    private Git git;
    private ObjectId oIdOfLastCommit;
    private ObjectMapper mapper = new ObjectMapper();
    private final static List<String> ENV = new ArrayList<>() {
        {
            add("iiif-presentation.base-url");
            add("iiif-presentation.git-url");
            add("iiif-presentation.git-branch");
            add("iiif-presentation.folder");
            add("iiif-presentation.webhook-secret");
        }
    };

    public Main() {
        // set System properties for pathes
        // get env and overwrite default configuration
        for (String e : ENV) {
            setEnvironmentVariable(e);
        }

        // make local folder
        try {
            // folder = Path.of("d:\\GitHub\\ddblabs-iiif-presentation-files");
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
            new Main().start();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            System.exit(-1);
        }
    }

    private void start() throws Exception {

        final String files = folder.toString() + File.separator + Configuration.get().getValue("iiif-presentation.folder");

        JavalinJackson.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        JavalinJackson.getObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        JavalinJackson.getObjectMapper().setVisibility(PropertyAccessor.FIELD, Visibility.ANY);

        final Javalin app = Javalin.create(config -> {
            config.enableCorsForAllOrigins();
            config.autogenerateEtags = true;
            config.showJavalinBanner = false;
            config.addStaticFiles(files, Location.EXTERNAL);
            config.addStaticFiles("/viewer");
            JavalinVue.stateFunction = (ctx -> {
                return Map.of("baseurl", Configuration.get().getValue("iiif-presentation.base-url"));
            });
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.stop();
        }));

        app.events(event -> {
            event.serverStopping(() -> {
                if (git != null) {
                    git.close();
                }
                FileUtils.deleteQuietly(folder.toFile());
            });

        });

        // set UTF-8 as default charset
        app.before(ctx -> {
            ctx.res.setCharacterEncoding("UTF-8");
        });

        app.get("/api/file", ctx -> {

            final CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                String f = ctx.queryParam("f", "");
                if (f != null && !f.isEmpty()) {
                    f = f.replaceAll("\\.\\." + StringEscapeUtils.escapeJava(File.separator) + "|\\.\\./", "");
                    f = StringUtils.strip(f, File.separator + "/");
                    f += File.separator;
                }

                final Path file = Path.of(folder.toString() + File.separator + f);
                try {
                    final JsonNode rootNode = mapper.readTree(file.toFile());
                    final String r = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
                    ctx.status(200);
                    return r;
                } catch (IOException ex) {
                    ctx.status(404);
                    return String.format("{\"error\":\"404\",\"message\": \"%s\"}", StringEscapeUtils.escapeJson(ex.getMessage()));
                }
            });
            ctx.contentType(ContentType.JSON).result(future);
        });

        app.get("/api/configuration", ctx -> {
            ctx.json(Configuration.get().getAllConfiguration());
        });

        app.get("/api/browse", ctx -> {

            String d = ctx.queryParam("d", "");
            if (d != null && !d.isEmpty()) {
                d = StringUtils.endsWith(d, "\\/") ? d : d + File.separator;
                d = d.replaceAll("\\.\\." + StringEscapeUtils.escapeJava(File.separator) + "|\\.\\./", "");
                d = StringUtils.strip(d, File.separator + "/");
                d += File.separator;
            }

            // only serve files with *.json ending
            final Path b = Path.of(folder.toString() + File.separator + d);
            final PathMatcher jsonMatcher = FileSystems.getDefault().getPathMatcher("glob:*.json");
            Path localFolder = folder;

            if (Files.exists(b, LinkOption.NOFOLLOW_LINKS)
                    && Files.isDirectory(b, LinkOption.NOFOLLOW_LINKS)
                    && Files.isReadable(b)) {

                localFolder = b;
            }

            //final PathMatcher filter = folder.getFileSystem().getPathMatcher("glob:*" + f);
            final Predicate<Path> isDirectory = e -> Files.isDirectory(e, LinkOption.NOFOLLOW_LINKS);
            final Predicate<Path> isHidden = e -> {
                try {
                    return Files.isHidden(e);
                } catch (IOException ex) {
                    return true;
                }
            };
            final Predicate<Path> endsWithJson = e -> jsonMatcher.matches(e.getFileName());

            final Predicate<Path> filterGit = e -> {
                return e.getFileName().toString().equals(".git");
            };

            final List<Path> filePathes = Files.list(localFolder)
                    //.map(Path::getName)
                    //.map(Path::toString)
                    .filter(filterGit.negate())
                    .filter(isHidden.negate())
                    .filter(endsWithJson.or(isDirectory))
                    .sorted()
                    .collect(Collectors.toList());

            final List<IiifFile> fles = new ArrayList<>();
            for (Path filePath : filePathes) {
                fles.add(new IiifFile(filePath));
            }
            ctx.json(fles);
        });

        app.get("/api/description", ctx -> {

            final CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                String f = ctx.queryParam("f", "");
                if (f != null && !f.isEmpty()) {
                    f = f.replaceAll("\\.\\." + StringEscapeUtils.escapeJava(File.separator) + "|\\.\\./", "");
                    f = StringUtils.strip(f, File.separator + "/");
                    f = FilenameUtils.removeExtension(f);
                    f += ".md";
                } else {
                    return String.format("{\"error\":\"500\",\"message\": \"%s\"}", "No file given");
                }

                final Path file = Path.of(folder.toString() + File.separator + f);
                try {
                    final String r = FileUtils.readFileToString(file.toFile(), Charset.forName("UTF-8"));
                    final String m = Processor.process(r);
                    final String j = JavalinJackson.INSTANCE.toJson(Map.of("content", m));
                    ctx.status(200);
                    return j;
                } catch (IOException ex) {
                    ctx.status(404);
                    return String.format("{\"error\":\"404\",\"message\": \"%s\"}", StringEscapeUtils.escapeJson(ex.getMessage()));
                }
            });
            ctx.contentType(ContentType.JSON).result(future);
        });

        app.post("/api/update", ctx -> {
            final String payload = ctx.body();
            LOG.info("Payload received: {}", payload);

            final String event = ctx.header("X-Github-Event");
            LOG.info("X-Github-Event received: {} (must be \"push\")", event);

            final String signatureTransmitted = ctx.header("X-Hub-Signature");
            LOG.info("X-Hub-Signature received: {}", signatureTransmitted);

            final String secret = Configuration.get().getValue("iiif-presentation.webhook-secret");
            LOG.info("Secret configured: {}", secret);

            final String signatureComputed = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, secret).hmacHex(payload);
            LOG.info("X-Hub-Signature computed: {} (must be equal to \"X-Hub-Signature received\")", signatureComputed);

            if (signatureTransmitted != null
                    && signatureComputed != null
                    && MessageDigest.isEqual(signatureTransmitted.replace("sha1=", "").getBytes(), signatureComputed.getBytes())
                    && event != null
                    && event.equalsIgnoreCase("push")) {
                LOG.info("All right! Let's do a GIT PULL...");
                pullRepository(); // get newest files
                ctx.status(200);
            } else {
                LOG.warn("Did not do a GIT PULL!");
                ctx.status(400);
            }

        });

        app.get("/", ctx -> {
            ctx.redirect(Configuration.get().getValue("iiif-presentation.base-url") + "/browse");
        });
        app.get("/browse", new VueComponent("<file-overview></file-overview>"));
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

    private void setEnvironmentVariable(String varName) {
        if (System.getenv(varName) != null) {
            System.setProperty(varName, System.getenv(varName));
            Configuration.get().setValue(varName, System.getenv(varName));
        } else {
            System.setProperty(varName, Configuration.get().getValue(varName));
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

        private Path name;

        public IiifFile(Path name) {
            this.name = name;
        }

        /**
         * @return the id
         */
        public int getId() {
            return name.hashCode() & 0xfffffff;
        }

        /**
         * @return the name
         */
        @JsonIgnore
        public Path getName() {
            return name;
        }

        /**
         * @return the name
         */
        public String getFilename() {

            return name.getFileName().toString();
        }

        public String getFilenameWithPath() {
            return StringUtils.strip(name.toString().replace(folder.toString(), ""), "\\/");
        }

        public String getPath() {
            return StringUtils.strip(getFilenameWithPath().replace(getFilename(), ""), "\\/");
        }

        public String getType() {
            return (Files.isDirectory(name, LinkOption.NOFOLLOW_LINKS)) ? "directory" : "file";
        }

        public long getSize() {
            return name.toFile().length();
        }

        /**
         * @param name the name to set
         */
        public void setName(Path name) {
            this.name = name;
        }
    }

}
