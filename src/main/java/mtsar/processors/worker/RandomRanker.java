package mtsar.processors.worker;

import mtsar.api.Task;
import mtsar.api.Worker;
import mtsar.processors.WorkerRanker;

public class RandomRanker implements WorkerRanker {
    @Override
    public double rank(Worker worker) {
        return Math.random();
    }

    @Override
    public double rank(Worker worker, Task task) {
        return Math.random();
    }
}
