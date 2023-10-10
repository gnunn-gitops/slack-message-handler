package com.redhat.demo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Stream;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;

import com.redhat.demo.pipeline.PipelineService;
import com.redhat.demo.pipeline.PushToProdPipeline;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@Path("/releaseApp")
public class SlackMessageResource {

    private static final Logger LOG = Logger.getLogger(SlackMessageResource.class);

    private static final String GIT_URL = "https://github.com/gnunn-gitops/product-catalog";
    private static final String GIT_SOURCE_URL = "https://github.com/gnunn-gitops/product-catalog-";
    private static final String GIT_REVISION = "main";

    @RestClient
    PipelineService pipelineService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response releaseApplication(@Context UriInfo uriInfo, @QueryParam("application") String application, @QueryParam("image") String image, @QueryParam("tag") String tag, @QueryParam("cluster") String cluster ) {
        PushToProdPipeline msg = new PushToProdPipeline();
        msg.application = application;
        msg.imageURL = image;
        msg.imageTag = tag;
        msg.cluster = cluster;
        msg.gitRevision = GIT_REVISION;
        msg.gitURL = GIT_URL;
        msg.gitSourceURL = GIT_SOURCE_URL + msg.application;

        try {
            pipelineService.startPipeline(msg);
        } catch (Throwable t) {
            LOG.error("Unexpected error occurred", t);
            StringBuffer sb = new StringBuffer();
            sb.append("<html>");
            sb.append("<head><title>Starting Pipeline</title></head>");
            sb.append("<body>");
            sb.append("<span>Unexpected error occurred for " + application + " using image " + image + ":" + image + " in cluster " + cluster + "</span>");
            sb.append("<span>Error Message: " + t.getMessage() + "</span>");
            sb.append("</body>");
            sb.append("</html>");

            return Response.status(500).entity(new ByteArrayInputStream(sb.toString().getBytes())).build();
        }
        LOG.info("Start message sent to trigger: "+msg.toString());

        StringBuffer sb = new StringBuffer();
        sb.append("<html>");
        sb.append("<head><title>Starting Pipeline</title></head>");
        sb.append("<body>");
        sb.append("<span>Starting pipeline for " + application + " using image " + image + ":" + image + " in cluster " + cluster + "</span>");
        sb.append("</body>");
        sb.append("</html>");

        return Response.seeOther(URI.create("https://console-openshift-console.apps.home.ocplab.com/pipelines/ns/product-catalog-cicd")).entity(new ByteArrayInputStream(sb.toString().getBytes())).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String releaseApplication(@RestForm("payload") String payload) {
        if (payload != null) {
            JsonObject json = new JsonObject(payload); // Convert text to object

            Optional<Map.Entry<String, Object>> result = json.stream()
                        .flatMap(SlackMessageResource::recursiveFlattening)
                        .filter(e -> "value".equals(e.getKey()))
                        .findAny();

            if (result.isPresent()) {
                String value = result.get().getValue().toString();
                //LOG.infof("Value is %s", value);
                String[] values = value.split(",");
                PushToProdPipeline msg = new PushToProdPipeline();
                msg.application = values[0];
                msg.imageURL = values[1];
                msg.imageTag = values[2];
                msg.cluster = values[3];
                msg.gitRevision = GIT_REVISION;
                msg.gitURL = GIT_URL;
                msg.gitSourceURL = GIT_SOURCE_URL + msg.application;

                pipelineService.startPipeline(msg);
                LOG.info("Start message sent to trigger: "+msg.toString());
            } else {
                LOG.info("No value found");
                LOG.info(json.encodePrettily());
            }

        } else {
            LOG.info("Payload is null");
        }
        return "{\"message\":\"ok\"}";
    }

    private static Stream<Map.Entry<String, Object>> recursiveFlattening(
        Entry<String, Object> entry) {

      Object jsonElement = entry.getValue();

      if (jsonElement instanceof JsonObject) {
        return ((JsonObject) jsonElement).stream()
                                         .flatMap(SlackMessageResource::recursiveFlattening);

      } else if (jsonElement instanceof JsonArray) {
        return ((JsonArray) jsonElement).stream()
                                        .map(v -> Map.entry("", v))
                                        .flatMap(SlackMessageResource::recursiveFlattening);

      } else {
        return Stream.of(entry);
      }
    }
}
