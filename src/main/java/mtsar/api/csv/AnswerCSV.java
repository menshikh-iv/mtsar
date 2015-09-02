package mtsar.api.csv;

import com.google.common.collect.Sets;
import mtsar.api.Answer;
import mtsar.api.Process;
import mtsar.api.sql.AnswerDAO;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkArgument;

public final class AnswerCSV {
    public static final CSVFormat FORMAT = CSVFormat.EXCEL.withHeader();

    public static Iterator<Answer> parse(Process process, CSVParser csv) {
        final Set<String> header = csv.getHeaderMap().keySet();
        checkArgument(!Sets.intersection(header, Sets.newHashSet(HEADER)).isEmpty(), "Unknown CSV header: %s", String.join(",", header));

        final Iterable<CSVRecord> iterable = () -> csv.iterator();

        return StreamSupport.stream(iterable.spliterator(), false).map(row -> {
            final String id = row.isSet("id") ? row.get("id") : null;
            final String[] tags = row.isSet("tags") && !StringUtils.isEmpty(row.get("tags")) ? row.get("tags").split("\\|") : null;
            final String type = row.isSet("type") ? row.get("type") : null;
            final String workerId = row.get("worker_id");
            final String taskId = row.get("task_id");
            final String[] answers = row.isSet("answers") && !StringUtils.isEmpty(row.get("answers")) ? row.get("answers").split("\\|") : null;
            final String datetime = row.isSet("datetime") ? row.get("datetime") : null;

            return new Answer.Builder().
                    setId(StringUtils.isEmpty(id) ? null : Integer.valueOf(id)).
                    setProcess(process.getId()).
                    addAllTags(Arrays.asList(tags)).
                    setDateTime(new Timestamp(StringUtils.isEmpty(datetime) ? System.currentTimeMillis() : Long.valueOf(datetime) * 1000L)).
                    setType(StringUtils.defaultIfEmpty(type, AnswerDAO.ANSWER_TYPE_DEFAULT)).
                    setWorkerId(Integer.valueOf(workerId)).
                    setTaskId(Integer.valueOf(taskId)).
                    addAllAnswers(Arrays.asList(answers)).
                    build();
        }).iterator();
    }

    public static final String[] HEADER = {"id", "process", "datetime", "tags", "type", "task_id", "worker_id", "answers"};

    public static void write(List<Answer> answers, OutputStream output) throws IOException {
        try (final Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            final Iterable<String[]> iterable = () -> answers.stream().map(answer -> new String[]{
                    Integer.toString(answer.getId()),                                 // id
                    answer.getProcess(),                                              // process
                    Long.toString(answer.getDateTime().toInstant().getEpochSecond()), // datetime
                    String.join("|", answer.getTags()),                               // tags
                    answer.getType(),                                                 // type
                    Integer.toString(answer.getTaskId()),                             // task_id
                    Integer.toString(answer.getWorkerId()),                           // worker_id
                    String.join("|", answer.getAnswers())                             // answers
            }).iterator();

            FORMAT.withHeader(HEADER).print(writer).printRecords(iterable);
        }
    }
}
