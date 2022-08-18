package com.redhat.demo;

import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Stream;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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

    @RestClient
    PipelineService pipelineService;

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
                msg.gitRevision = "main";
                msg.gitURL = "https://github.com/gnunn-gitops/product-catalog";

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