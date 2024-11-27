package example.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.r2dbc.postgresql.codec.Json;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.SimpleTimeZone;

import static java.lang.System.*;

public class JacksonDemo {
  public static void main(String[] args) throws JsonProcessingException {
    ObjectMapper objectMapper =
        new ObjectMapper()
            .findAndRegisterModules()
            .configure(SerializationFeature.WRITE_DATES_WITH_ZONE_ID, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
            .configure(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS, false)
            .configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true)
            .setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
            .setTimeZone(SimpleTimeZone.getTimeZone("GMT+8"));

    ArrayNode jsonArray = objectMapper.createArrayNode();
    jsonArray.add("Jackson").add("JSON");
    ObjectNode jsonObject =
        objectMapper.createObjectNode().put("title", "Json 之 Jackson").put("readCount", 1024);

    ZonedDateTime zdt = ZonedDateTime.parse("2020-07-02T14:31:28.822+08:00[Asia/Shanghai]");
    JavaTime javaTime =
        new JavaTime()
            .setLocalDateTime(zdt.toLocalDateTime())
            .setZonedDateTime(zdt)
            .setOffsetDateTime(zdt.toOffsetDateTime())
            .setLocalDate(zdt.toLocalDate())
            .setLocalTime(zdt.toLocalTime())
            .setDuration(Duration.parse("P1DT1H1M1.1S"))
            .setDate(Date.from(zdt.toInstant()))
            .setTimestamp(Timestamp.from(zdt.toInstant()));
    out.println(objectMapper.writeValueAsString(javaTime));
    out.println(objectMapper.writeValueAsString(jsonObject));
    out.println(objectMapper.writeValueAsString(jsonArray));
    out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(javaTime));

    String jsonText = objectMapper.writeValueAsString(javaTime);
    // json text -> Jackson json node
    JsonNode javaTimeNode = objectMapper.readTree(jsonText);
    // json text -> java class
    JavaTime javaTime1 = objectMapper.readValue(jsonText, JavaTime.class);
    // Jackson json node -> java class
    JavaTime javaTime2 = objectMapper.treeToValue(javaTimeNode, JavaTime.class);

    User user = new User();
    user.setId("yangjing");
    user.setMetadata(Json.of("{\"job\":\"programmer\",\"workYears\":14}\n"));
    String userText = objectMapper.writeValueAsString(user);
    out.println(userText);
  }
}
