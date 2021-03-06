/*
 * Copyright 2015 Dmitry Ustalov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mtsar.resources;

import io.dropwizard.jersey.PATCH;
import mtsar.api.*;
import mtsar.api.csv.WorkerCSV;
import mtsar.api.csv.WorkerRankingCSV;
import mtsar.api.sql.AnswerDAO;
import mtsar.api.sql.TaskDAO;
import mtsar.api.sql.WorkerDAO;
import mtsar.api.validation.AnswerValidation;
import mtsar.api.validation.TaskAnswerValidation;
import mtsar.util.DateTimeUtils;
import mtsar.util.ParamsUtils;
import mtsar.views.WorkersView;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.lang3.tuple.Pair;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Path("/workers")
@Produces(mtsar.util.MediaType.APPLICATION_JSON)
public class WorkerResource {
    private final Stage stage;
    private final TaskDAO taskDAO;
    private final WorkerDAO workerDAO;
    private final AnswerDAO answerDAO;

    public WorkerResource(Stage stage, TaskDAO taskDAO, WorkerDAO workerDAO, AnswerDAO answerDAO) {
        this.stage = stage;
        this.taskDAO = taskDAO;
        this.workerDAO = workerDAO;
        this.answerDAO = answerDAO;
    }

    @GET
    public List<Worker> getWorkers() {
        return workerDAO.listForStage(stage.getId());
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public WorkersView getWorkersView(@Context UriInfo uriInfo) {
        return new WorkersView(uriInfo, stage, workerDAO);
    }

    @GET
    @Path("rankings")
    public Map<Integer, WorkerRanking> getWorkerRankings() {
        final List<Worker> workers = workerDAO.listForStage(stage.getId());
        final Map<Integer, WorkerRanking> rankings = stage.getWorkerRanker().rank(workers);
        return rankings;
    }

    @GET
    @Path("rankings.csv")
    @Produces(mtsar.util.MediaType.TEXT_CSV)
    public StreamingOutput getWorkerRankingsCSV() {
        final List<Worker> workers = workerDAO.listForStage(stage.getId());
        final Map<Integer, WorkerRanking> rankings = stage.getWorkerRanker().rank(workers);
        return output -> WorkerRankingCSV.write(rankings.values(), output);
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response postWorkersCSV(@Context UriInfo uriInfo, @FormDataParam("file") InputStream stream) throws IOException {
        try (final Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            try (final CSVParser csv = new CSVParser(reader, WorkerCSV.FORMAT)) {
                workerDAO.insert(WorkerCSV.parse(stage, csv));
            }
        }
        workerDAO.resetSequence();
        return Response.seeOther(getWorkersURI(uriInfo)).build();
    }

    @POST
    public Response postWorker(@Context UriInfo uriInfo, @FormParam("tags") List<String> tags) {
        int workerId = workerDAO.insert(new Worker.Builder().
                setStage(stage.getId()).
                addAllTags(tags).
                build());
        final Worker worker = workerDAO.find(workerId, stage.getId());
        return Response.created(getWorkerURI(uriInfo, worker)).entity(worker).build();
    }

    @GET
    @Path("{worker}")
    public Worker getWorker(@PathParam("worker") Integer id) {
        return fetchWorker(id);
    }

    @GET
    @Path("tagged/{tag}")
    public Worker getWorkerByTag(@PathParam("tag") String tag) {
        final Worker worker = workerDAO.findByTags(stage.getId(), Collections.singletonList(tag));
        return worker;
    }

    @GET
    @Path("{worker}/task")
    public TaskAllocation getWorkerTask(@PathParam("worker") Integer id) {
        return getWorkerTasks(id, 1);
    }

    @GET
    @Path("{worker}/tasks/{n}")
    public TaskAllocation getWorkerTasks(@PathParam("worker") Integer id, @PathParam("n") Integer n) {
        final Worker worker = fetchWorker(id);
        return ParamsUtils.optional(stage.getTaskAllocator().allocate(worker, n));
    }

    @GET
    @Path("{worker}/tasks")
    public TaskAllocation getWorkerTaskAgain(@PathParam("worker") Integer id, @QueryParam("task_id") List<Integer> taskIds) {
        final Worker worker = fetchWorker(id);
        final List<Task> tasks = taskIds.stream().map(this::fetchTask).collect(Collectors.toList());
        final Optional<TaskAllocation> optional = stage.getTaskAllocator().allocate(worker);

        if (optional.isPresent()) {
            return new TaskAllocation.Builder().
                    mergeFrom(optional.get()).
                    clearTasks().
                    addAllTasks(tasks).
                    build();
        } else {
            return new TaskAllocation.Builder().
                    setWorker(worker).
                    addAllTasks(tasks).
                    setTaskRemaining(1).
                    setTaskCount(taskDAO.count(stage.getId())).
                    build();
        }
    }

    @GET
    @Path("{worker}/answers")
    public List<Answer> getWorkerAnswers(@PathParam("worker") Integer id) {
        final Worker worker = fetchWorker(id);
        return answerDAO.listForWorker(worker.getId(), stage.getId());
    }

    @PATCH
    @Path("{worker}/answers")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response postAnswers(@Context Validator validator, @Context UriInfo uriInfo, @PathParam("worker") Integer id, @FormParam("type") @DefaultValue(AnswerDAO.ANSWER_TYPE_DEFAULT) String type, @FormParam("tags") List<String> tags, @FormParam("datetime") String datetimeParam, MultivaluedMap<String, String> params) {
        final Timestamp datetime = (datetimeParam == null) ? DateTimeUtils.now() : Timestamp.valueOf(datetimeParam);
        final Worker worker = fetchWorker(id);
        final Map<String, List<String>> nested = ParamsUtils.nested(params, "answers");

        final Map<Answer, Set<ConstraintViolation<Object>>> answers = nested.entrySet().stream().map(entry -> {
            final Integer taskId = Integer.valueOf(entry.getKey());
            final Task task = fetchTask(taskId);

            final Answer answer = new Answer.Builder().
                    setStage(stage.getId()).
                    addAllTags(tags).
                    setType(type).
                    setTaskId(task.getId()).
                    setWorkerId(worker.getId()).
                    addAllAnswers(entry.getValue()).
                    setDateTime(datetime).
                    build();

            final Set<ConstraintViolation<Object>> violations = ParamsUtils.validate(validator,
                    new TaskAnswerValidation.Builder().setTask(task).setAnswer(answer).build(),
                    new AnswerValidation.Builder().setAnswer(answer).setAnswerDAO(answerDAO).build()
            );

            return Pair.of(answer, violations);
        }).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        final Set<ConstraintViolation<Object>> violations = answers.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);

        final List<Answer> inserted = AnswerDAO.insert(answerDAO, answers.keySet());
        return Response.ok(inserted).build();
    }

    @PATCH
    @Path("{worker}/answers/skip")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response postAnswersSkip(@Context Validator validator, @Context UriInfo uriInfo, @PathParam("worker") Integer id, @FormParam("tags") List<String> tags, @FormParam("datetime") String datetimeParam, @FormParam("tasks") List<Integer> tasks) {
        final Timestamp datetime = (datetimeParam == null) ? DateTimeUtils.now() : Timestamp.valueOf(datetimeParam);
        final Worker worker = fetchWorker(id);

        final Map<Answer, Set<ConstraintViolation<Object>>> answers = tasks.stream().map(taskId -> {
            final Task task = fetchTask(taskId);

            final Answer answer = new Answer.Builder().
                    setStage(stage.getId()).
                    addAllTags(tags).
                    setType(AnswerDAO.ANSWER_TYPE_SKIP).
                    setTaskId(task.getId()).
                    setWorkerId(worker.getId()).
                    setDateTime(datetime).
                    build();

            /* Since we are skipping the task, the only constraint we need is #answer-duplicate. */
            final Set<ConstraintViolation<Object>> violations = ParamsUtils.validate(validator,
                    new AnswerValidation.Builder().setAnswer(answer).setAnswerDAO(answerDAO).build()
            );

            return Pair.of(answer, violations);
        }).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        final Set<ConstraintViolation<Object>> violations = answers.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);

        final List<Answer> inserted = AnswerDAO.insert(answerDAO, answers.keySet());
        return Response.ok(inserted).build();
    }

    @PATCH
    @Path("{worker}")
    public Worker patchWorker(@PathParam("worker") Integer id) {
        final Worker worker = fetchWorker(id);
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @DELETE
    @Path("{worker}")
    public Worker deleteWorker(@PathParam("worker") Integer id) {
        final Worker worker = fetchWorker(id);
        workerDAO.delete(id, stage.getId());
        return worker;
    }

    @DELETE
    public void deleteWorkers() {
        workerDAO.deleteAll(stage.getId());
        workerDAO.resetSequence();
    }

    private Worker fetchWorker(Integer id) {
        final Worker worker = workerDAO.find(id, stage.getId());
        if (worker == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
        return worker;
    }

    private Task fetchTask(Integer id) {
        final Task task = taskDAO.find(id, stage.getId());
        if (task == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
        return task;
    }

    private URI getWorkersURI(UriInfo uriInfo) {
        return uriInfo.getBaseUriBuilder().
                path("stages").path(stage.getId()).
                path("workers").
                build();
    }

    private URI getWorkerURI(UriInfo uriInfo, Worker worker) {
        return uriInfo.getBaseUriBuilder().
                path("stages").path(stage.getId()).
                path("workers").path(worker.getId().toString()).
                build();
    }
}
