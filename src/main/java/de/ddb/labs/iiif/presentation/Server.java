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

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.rjeschke.txtmark.Processor;
import de.ddb.labs.iiif.presentation.helper.Configuration;
import de.ddb.labs.iiif.presentation.helper.NaturalOrderComparator;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.plugin.json.JavalinJackson;
import io.javalin.plugin.openapi.annotations.ContentType;
import io.javalin.plugin.rendering.vue.JavalinVue;
import io.javalin.plugin.rendering.vue.VueComponent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import static java.util.Collections.singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Michael Büchner <m.buechner@dnb.de>
 */
public class Server {

    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private final static List<String> ENV = new ArrayList<>() {
        {
            add("iiif-presentation.base-url");
            add("iiif-presentation.git-url");
            add("iiif-presentation.git-branch");
            add("iiif-presentation.folder");
            add("iiif-presentation.webhook-secret");
            add("iiif-presentation.port");
            add("iiif-presentation.pathprefix");
        }
    };
    private Path folder;
    private Git git;
    private ObjectId oIdOfLastCommit;
    private ObjectMapper mapper = new ObjectMapper();

    /**
     * Constructor
     */
    public Server() {
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
            cloneRepository(folder);
        } catch (IOException | GitAPIException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        try {
            final List<String> list = getResourceFiles("/viewer");
            LOG.info("No. of resources: {}", list.size());
            for (String f : list) {
                LOG.info("/viewer/{} found.", f);
            }
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private List<String> getResourceFiles(String path) throws IOException {
        List<String> filenames = new ArrayList<>();

        try (
                InputStream in = getResourceAsStream(path);
                BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String resource;

            while ((resource = br.readLine()) != null) {
                filenames.add(resource);
            }
        }

        return filenames;
    }

    private InputStream getResourceAsStream(String resource) {
        final InputStream in
                = getContextClassLoader().getResourceAsStream(resource);

        return in == null ? getClass().getResourceAsStream(resource) : in;
    }

    private ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    /**
     * Start the server... :-)
     *
     * @throws Exception
     */
    public void start() throws Exception {

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

            config.requestLogger((ctx, timeMs) -> {
                LOG.info("{} {} took {}", ctx.method(), ctx.path(), timeMs);
            });

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
            if (ctx.res.getHeader("Access-Control-Allow-Origin") == null || ctx.res.getHeader("Access-Control-Allow-Origin").isEmpty()) {
                ctx.res.addHeader("Access-Control-Allow-Origin", "*");
            }

            ctx.res.addHeader("Access-Control-Allow-Methods", "GET");
            ctx.res.addHeader("Access-Control-Allow-Headers", "X-PINGOTHER,Origin,X-Requested-With,Content-Type,Accept,Authorization");

            ctx.res.addHeader("Vary", "Accept-Encoding");
        });

        /**
         * Get JSON file API entry point
         */
        app.get(Configuration.get().getValue("iiif-presentation.pathprefix") + "/api/file", ctx -> {

            final CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                String f = ctx.queryParam("f", "");
                if (f != null && !f.isEmpty()) {
                    f = f.replaceAll("\\.\\." + StringEscapeUtils.escapeJava(File.separator) + "|\\.\\./", "");
                    f = StringUtils.strip(f, File.separator + "/");
                    f += File.separator;
                }

                final Path file = Path.of(folder.toString() + File.separator + f);
                try {
                    JsonNode rootNode = mapper.readTree(file.toFile());
                    rootNode = changeDdbImage(rootNode, ctx.path() + "?" + ctx.queryString());
                    final String r = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
                    ctx.status(200);
                    return r;
                } catch (Exception ex) {
                    ctx.status(404);
                    return String.format("{\"error\":\"404\",\"message\": \"%s\"}", StringEscapeUtils.escapeJson(ex.getMessage()));
                }
            });
            ctx.contentType(ContentType.JSON).result(future);
        });

        app.get(Configuration.get().getValue("iiif-presentation.pathprefix") + "/api/configuration", ctx -> {
            ctx.json(Configuration.get().getAllConfiguration());
        });

        /**
         * List JSON files API entry point
         */
        app.get(Configuration.get().getValue("iiif-presentation.pathprefix") + "/api/browse", ctx -> {

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

            final List<Path> folderPathes = Files.list(localFolder)
                    .filter(filterGit.negate())
                    .filter(isHidden.negate())
                    .filter(isDirectory)
                    .collect(Collectors.toList());
            Collections.sort(folderPathes, new NaturalOrderComparator());

            final List<Path> filePathes = Files.list(localFolder)
                    .filter(filterGit.negate())
                    .filter(isHidden.negate())
                    .filter(endsWithJson)
                    .collect(Collectors.toList());

            Collections.sort(filePathes, new NaturalOrderComparator());

            final List<IiifFile> fles = new ArrayList<>();
            for (Path filePath : folderPathes) {
                fles.add(new IiifFile(filePath));
            }
            for (Path filePath : filePathes) {
                fles.add(new IiifFile(filePath));
            }

            ctx.json(fles);

        });

        /**
         * Get description, stored in [filename].md, of a JSON file.
         */
        app.get(Configuration.get().getValue("iiif-presentation.pathprefix") + "/api/description", ctx -> {

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

        /**
         * Webhook API entry point
         */
        app.post(Configuration.get().getValue("iiif-presentation.pathprefix") + "/api/update", ctx -> {
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

        /**
         * Vue template
         */
        app.get(Configuration.get().getValue("iiif-presentation.pathprefix") + "/", new VueComponent("<file-overview></file-overview>"));

        app.start(Integer.parseInt(Configuration.get().getValue("iiif-presentation.port")));
    }

    public JsonNode changeDdbImage(JsonNode parent, String path) throws JsonProcessingException {
        String jsonString = mapper.writeValueAsString(parent);
        jsonString = jsonString.replaceAll("\\{\\{iiif\\-image\\-url\\}\\}", Configuration.get().getValue("iiif-presentation.image-api-url"));
        jsonString = jsonString.replaceAll("\\{\\{self\\-url\\}\\}", Configuration.get().getValue("iiif-presentation.base-url") + path);
        return mapper.readTree(jsonString);
    }

    /**
     * Sets environment variables if there any, otherwise it'll use the values
     * from iiif-presentation.cfg
     *
     * @param varName
     */
    private void setEnvironmentVariable(String varName) {
        if (System.getenv(varName) != null) {
            System.setProperty(varName, System.getenv(varName));
            Configuration.get().setValue(varName, System.getenv(varName));
        } else {
            System.setProperty(varName, Configuration.get().getValue(varName));
        }
    }

    /**
     * Clone Repository configured in iiif-presentation.cfg or set over
     * environment variables.
     *
     * @param folder
     * @throws IOException
     * @throws GitAPIException
     */
    private void cloneRepository(Path folder) throws IOException, GitAPIException {

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

    /**
     * Pulls the Git repositiory if there's a new commit.
     *
     * @throws IncorrectObjectTypeException
     * @throws GitAPIException
     * @throws IOException
     */
    private void pullRepository() throws IncorrectObjectTypeException, GitAPIException, IOException {

        final Map<String, Ref> map = Git.lsRemoteRepository()
                .setHeads(true)
                .setTags(true)
                .setRemote(Configuration.get().getValue("iiif-presentation.git-url"))
                .callAsMap();

        final Ref commit = map.get(Configuration.get().getValue("iiif-presentation.git-branch"));
        final ObjectId oId = commit.getObjectId();

        if (oId != null && !oId.equals(oIdOfLastCommit) && git != null) {
            final PullCommand pull = git.pull();
            pull.call();
            oIdOfLastCommit = oId;
            LOG.info("ObjectId of last commit is now: {}", oIdOfLastCommit);
        }
    }

    /**
     * IIIF file for JSON serialization
     */
    public class IiifFile {

        private Path name;

        public IiifFile(Path name) {
            this.name = name;
        }

        public int getId() {
            return name.hashCode() & 0xfffffff;
        }

        @JsonIgnore
        public Path getName() {
            return name;
        }

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

        public void setName(Path name) {
            this.name = name;
        }
    }
}
