import java.util.*;

public class Scheduler {

    // ======================
    // Process class
    // ======================
   static class Process {
    String name;
    int arrival;
    int burst;
    int remaining;
    int priority;
    int last;
    int completion;

    Process(String name, int arrival, int burst, int priority) {
        this.name = name;
        this.arrival = arrival;
        this.burst = burst;
        this.remaining = burst;
        this.priority = priority;
        this.last = arrival;
    }

    // Debug runner: run specific test scenarios from command line
    public static void main(String[] args) {
        if (args.length > 0 && "debug3".equals(args[0])) {
            List<Process> processes = new ArrayList<>();
            processes.add(new Process("P1", 0, 10, 5));
            processes.add(new Process("P2", 2, 5, 1));
            processes.add(new Process("P3", 5, 3, 2));
            processes.add(new Process("P4", 8, 7, 1));
            processes.add(new Process("P5", 10, 2, 3));

            Result r = priorityScheduling(processes, 1, 4);
            System.out.println("Result execution order: " + r.executionOrder);
            System.out.println("Waiting times: " + r.waitingTime);
            System.out.println("Turnaround times: " + r.turnaroundTime);
        }
    }
}



    // ======================
    // Result class
    // ======================
    static class Result {
        List<String> executionOrder = new ArrayList<>();
        Map<String, Integer> waitingTime = new HashMap<>();
        Map<String, Integer> turnaroundTime = new HashMap<>();
        double avgWaiting;
        double avgTurnaround;
    }

    // ======================
    // Preemptive SJF Scheduler
    // ======================
    public static Result preemptiveSJF(List<Process> processes, int contextSwitch) {

        Result res = new Result();
        int time = 0;
        int finished = 0;
        Process current = null;

        while (finished < processes.size()) {

            // Build ready queue
            List<Process> ready = new ArrayList<>();
            for (Process p : processes) {
                if (p.arrival <= time && p.remaining > 0) {
                    ready.add(p);
                }
            }

            if (ready.isEmpty()) {
                time++;
                continue;
            }

            // Select process with shortest remaining time
            ready.sort(Comparator.comparingInt(p -> p.remaining));
            Process next = ready.get(0);

            // Add context switch time if switching processes
            if (current != null && !current.name.equals(next.name)) {
                time += contextSwitch;
            }

            current = next;
            res.executionOrder.add(current.name);

            // Calculate how long to run this process
            // Run until: completion, or a new process with shorter remaining time arrives
            int runTime = current.remaining;
            
            // Check for preemption by upcoming arrivals
            for (Process p : processes) {
                if (p.arrival > time && p.arrival < time + runTime) {
                    // A process will arrive while current is running
                    int timeUntilArrival = p.arrival - time;
                    int remainingAfter = current.remaining - timeUntilArrival;
                    
                    // Will the new process preempt?
                    if (p.burst < remainingAfter) {
                        runTime = timeUntilArrival;
                        break;
                    }
                }
            }

            // Execute for runTime
            current.remaining -= runTime;
            time += runTime;

            if (current.remaining == 0) {
                current.completion = time;
                finished++;
            }
        }

        // calculate times
        double totalWT = 0;
        double totalTAT = 0;

        for (Process p : processes) {
            int tat = p.completion - p.arrival;
            int wt = tat - p.burst;

            res.turnaroundTime.put(p.name, tat);
            res.waitingTime.put(p.name, wt);

            totalWT += wt;
            totalTAT += tat;
        }

        res.avgWaiting = totalWT / processes.size();
        res.avgTurnaround = totalTAT / processes.size();

        System.out.println("[DEBUG] Priority executionOrder: " + res.executionOrder);

        return res;
    }

    // ======================
    // Round Robin Scheduler
    // ======================
    public static Result roundRobin(
            List<Process> processes,
            int quantum,
            int contextSwitch
    ) {

        Result res = new Result();

        Queue<Process> readyQueue = new LinkedList<>();
        int time = 0;
        int finished = 0;

        // sort by arrival time
        processes.sort(Comparator.comparingInt(p -> p.arrival));

        int index = 0; // for arrival tracking
        Process current = null;

        while (finished < processes.size()) {

            // add newly arrived processes
            while (index < processes.size()
                    && processes.get(index).arrival <= time) {
                readyQueue.add(processes.get(index));
                index++;
            }

            if (readyQueue.isEmpty()) {
                time++;
                continue;
            }

            Process p = readyQueue.poll();

            // context switch
            if (current != null && current != p) {
                time += contextSwitch;
            }
            current = p;

            res.executionOrder.add(p.name);

            int execTime = Math.min(quantum, p.remaining);
            p.remaining -= execTime;
            time += execTime;

            // check new arrivals during execution
            while (index < processes.size()
                    && processes.get(index).arrival <= time) {
                readyQueue.add(processes.get(index));
                index++;
            }

            if (p.remaining > 0) {
                readyQueue.add(p); // re-queue
            } else {
                p.completion = time;
                finished++;
            }
        }

        // calculate times
        double totalWT = 0;
        double totalTAT = 0;

        for (Process p : processes) {
            int tat = p.completion - p.arrival;
            int wt = tat - p.burst;

            res.turnaroundTime.put(p.name, tat);
            res.waitingTime.put(p.name, wt);

            totalWT += wt;
            totalTAT += tat;
        }

        res.avgWaiting = totalWT / processes.size();
        res.avgTurnaround = totalTAT / processes.size();

        return res;
    }

    // ======================
    // Priority Scheduler (Preemptive with Aging)
    // ======================
    public static Result priorityScheduling(List<Process> processes, int contextSwitch, int agingInterval) {

        Result res = new Result();

        int time = 0;
        int finished = 0;
        Process current = null;

        // sort by arrival to make arrivals predictable
        processes.sort(Comparator.comparingInt(p -> p.arrival));

        // helper comparator: lower numeric priority means higher priority
        Comparator<Process> prioCmp = Comparator
            .comparingInt((Process p) -> p.priority)
            .thenComparingInt(p -> p.arrival)
            .thenComparing(p -> p.name);

        // previous running process name to record executionOrder only on switches
        String prevRunning = null;

        while (finished < processes.size()) {

            // debug
            boolean DBG = false;

            // apply aging for waiting processes (based on last field)
            for (Process p : processes) {
                if (p.remaining > 0 && p.arrival <= time) {
                    while (agingInterval > 0 && (time - p.last) >= agingInterval) {
                        if (p.priority > 1) p.priority = p.priority - 1;
                        p.last += agingInterval;
                    }
                }
            }

            // build ready list
            List<Process> ready = new ArrayList<>();
            for (Process p : processes) {
                if (p.arrival <= time && p.remaining > 0) ready.add(p);
            }

            if (ready.isEmpty()) {
                time++;
                continue;
            }

            // pick highest priority (lowest numeric)
            ready.sort(prioCmp);
            Process next = ready.get(0);

            // if switching, account for context switch time (simulate aging during it)
            if (current != null && !current.name.equals(next.name)) {
                if (DBG) System.out.println("[DBG] switching from " + current.name + " to " + next.name + ", adding CS=" + contextSwitch);
                
                // Record the process we INTEND to switch to
                if (prevRunning == null || !prevRunning.equals(next.name)) {
                    res.executionOrder.add(next.name);
                    prevRunning = next.name;
                }
                
                for (int i = 0; i < contextSwitch; i++) {
                    time++;
                    // aging during context switch - all waiting processes can age
                    for (Process p : processes) {
                        if (p.remaining > 0 && p.arrival <= time && !p.name.equals(next.name)) {
                            while (agingInterval > 0 && (time - p.last) >= agingInterval) {
                                if (p.priority > 1) p.priority = p.priority - 1;
                                p.last += agingInterval;
                            }
                        }
                    }
                    if (DBG) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("[DBG] time after cs tick: ").append(time).append(" ready:");
                        for (Process p : processes) if (p.arrival <= time && p.remaining>0) sb.append(" ").append(p.name).append("(p=").append(p.priority).append(")");
                        System.out.println(sb.toString());
                    }
                }
                
                // re-evaluate after context switch - if priority changed, do another CS
                ready.clear();
                for (Process p : processes) {
                    if (p.arrival <= time && p.remaining > 0) ready.add(p);
                }
                if (!ready.isEmpty()) {
                    ready.sort(prioCmp);
                    Process reevaluated = ready.get(0);
                    
                    // If the highest priority changed during CS, need ANOTHER context switch
                    if (!reevaluated.name.equals(next.name)) {
                        if (DBG) System.out.println("[DBG] Priority changed during CS! Need another CS to " + reevaluated.name);
                        
                        // Record the NEW process we're switching to
                        if (!prevRunning.equals(reevaluated.name)) {
                            res.executionOrder.add(reevaluated.name);
                            prevRunning = reevaluated.name;
                        }
                        
                        for (int i = 0; i < contextSwitch; i++) {
                            time++;
                            // aging during the second context switch
                            for (Process p : processes) {
                                if (p.remaining > 0 && p.arrival <= time && !p.name.equals(reevaluated.name)) {
                                    while (agingInterval > 0 && (time - p.last) >= agingInterval) {
                                        if (p.priority > 1) p.priority = p.priority - 1;
                                        p.last += agingInterval;
                                    }
                                }
                            }
                        }
                    }
                    next = reevaluated;
                }
            } else {
                // Not switching - record if needed
                if (prevRunning == null || !prevRunning.equals(next.name)) {
                    res.executionOrder.add(next.name);
                    prevRunning = next.name;
                }
            }

            // run one time unit (preemptive, so check each tick)
            current = next;
            current.remaining -= 1;
            // mark that it was just run (reset its last)
            current.last = time + 1;
            time++;

            if (current.remaining == 0) {
                current.completion = time;
                finished++;
                // reset prevRunning so next scheduling records new start
                prevRunning = null;
            }
        }

        // calculate times
        double totalWT = 0;
        double totalTAT = 0;

        for (Process p : processes) {
            int tat = p.completion - p.arrival;
            int wt = tat - p.burst;

            res.turnaroundTime.put(p.name, tat);
            res.waitingTime.put(p.name, wt);

            totalWT += wt;
            totalTAT += tat;
        }

        res.avgWaiting = totalWT / processes.size();
        res.avgTurnaround = totalTAT / processes.size();

        return res;
    }

}